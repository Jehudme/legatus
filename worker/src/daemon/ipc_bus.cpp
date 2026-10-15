#include "legatus/daemon/ipc_bus.hpp"
#include <nng/protocol/reqrep0/rep.h>
#include <nng/protocol/reqrep0/req.h>
#include <filesystem>
#include <iostream>

namespace legatus::daemon {

std::string IpcRequest::serialize() const {
    nlohmann::json j;
    j["action"] = action;
    j["params"] = params;
    return j.dump();
}

IpcRequest IpcRequest::deserialize(const std::string& json_str) {
    IpcRequest req;
    auto j = nlohmann::json::parse(json_str);
    req.action = j.value("action", "");
    if (j.contains("params") && j["params"].is_object()) {
        req.params = j["params"];
    }
    return req;
}

IpcResponse IpcResponse::success(const nlohmann::json& data) {
    IpcResponse res;
    res.status = "SUCCESS";
    res.data = data;
    return res;
}

IpcResponse IpcResponse::failure(const std::string& code, const std::string& message) {
    IpcResponse res;
    res.status = "ERROR";
    res.error.code = code;
    res.error.message = message;
    return res;
}

std::string IpcResponse::serialize() const {
    nlohmann::json j;
    j["status"] = status;
    j["data"] = data;
    if (status == "ERROR") {
        j["error"] = {
            {"code", error.code},
            {"message", error.message}
        };
    }
    return j.dump();
}

IpcResponse IpcResponse::deserialize(const std::string& json_str) {
    IpcResponse res;
    auto j = nlohmann::json::parse(json_str);
    res.status = j.value("status", "ERROR");
    if (j.contains("data") && j["data"].is_object()) {
        res.data = j["data"];
    }
    if (j.contains("error") && j["error"].is_object()) {
        res.error.code = j["error"].value("code", "");
        res.error.message = j["error"].value("message", "");
    }
    return res;
}

IpcServer::IpcServer(std::string endpoint)
    : endpoint_(std::move(endpoint)) {}

IpcServer::~IpcServer() {
    stop();
}

void IpcServer::set_handler(std::shared_ptr<IIpcHandler> handler) {
    handler_ = std::move(handler);
}

bool IpcServer::start() {
    if (running_.load()) return true;

#if !defined(_WIN32)
    // Remove stale unix domain socket if present
    if (endpoint_.rfind("ipc:///", 0) == 0) {
        std::string socket_path = endpoint_.substr(6);
        std::error_code ec;
        std::filesystem::remove(socket_path, ec);
    }
#endif

    int rv = nng_rep0_open(&socket_);
    if (rv != 0) {
        return false;
    }

    // Set receive timeout so that the loop regularly checks running_ condition
    nng_socket_set_ms(socket_, NNG_OPT_RECVTIMEO, 1000);

    rv = nng_listen(socket_, endpoint_.c_str(), nullptr, 0);
    if (rv != 0) {
        nng_close(socket_);
        return false;
    }

    running_ = true;
    worker_thread_ = std::thread(&IpcServer::run_loop, this);
    return true;
}

void IpcServer::stop() {
    if (!running_.exchange(false)) return;

    nng_close(socket_);

    if (worker_thread_.joinable()) {
        worker_thread_.join();
    }

#if !defined(_WIN32)
    if (endpoint_.rfind("ipc:///", 0) == 0) {
        std::string socket_path = endpoint_.substr(6);
        std::error_code ec;
        std::filesystem::remove(socket_path, ec);
    }
#endif
}

bool IpcServer::is_running() const {
    return running_.load();
}

void IpcServer::run_loop() {
    while (running_.load()) {
        char* buf = nullptr;
        size_t sz = 0;
        int rv = nng_recv(socket_, &buf, &sz, NNG_FLAG_ALLOC);
        if (rv == NNG_ETIMEDOUT) {
            continue;
        }
        if (rv != 0) {
            if (rv == NNG_ECLOSED) break;
            continue;
        }

        std::string req_str(buf, sz);
        nng_free(buf, sz);

        IpcResponse resp;
        try {
            auto req = IpcRequest::deserialize(req_str);
            if (!handler_) {
                resp = IpcResponse::failure("ERR_NO_HANDLER", "No IPC handler registered in daemon");
            } else if (req.action == "DAEMON_STATUS") {
                resp = handler_->handle_daemon_status();
            } else if (req.action == "DAEMON_DOWN") {
                bool force = req.params.value("force", false);
                resp = handler_->handle_daemon_down(force);
            } else if (req.action == "DAEMON_DRAIN") {
                bool enabled = req.params.value("enabled", true);
                int32_t timeout_seconds = req.params.value("timeout_seconds", 0);
                resp = handler_->handle_daemon_drain(enabled, timeout_seconds);
            } else if (req.action == "SET_LOG_LEVEL") {
                std::string level = req.params.value("level", "info");
                resp = handler_->handle_set_log_level(level);
            } else if (req.action == "GET_LOG_CONFIG") {
                resp = handler_->handle_get_log_config();
            } else if (req.action == "ABORT_TASK") {
                std::string task_id = req.params.value("task_id", "");
                std::string reason = req.params.value("reason", "Operator abort");
                resp = handler_->handle_abort_task(task_id, reason);
            } else {
                resp = IpcResponse::failure("ERR_UNKNOWN_ACTION", "Unrecognized IPC action: " + req.action);
            }
        } catch (const std::exception& e) {
            resp = IpcResponse::failure("ERR_INVALID_REQUEST", std::string("Malformed JSON request: ") + e.what());
        }

        std::string resp_str = resp.serialize();
        nng_send(socket_, (void*)resp_str.data(), resp_str.size(), 0);
    }
}

IpcClient::IpcClient(std::string endpoint)
    : endpoint_(std::move(endpoint)) {}

IpcClient::~IpcClient() {
    disconnect();
}

bool IpcClient::connect() {
    if (connected_) return true;

    int rv = nng_req0_open(&socket_);
    if (rv != 0) return false;

    nng_socket_set_ms(socket_, NNG_OPT_RECVTIMEO, 5000);
    nng_socket_set_ms(socket_, NNG_OPT_SENDTIMEO, 5000);

    rv = nng_dial(socket_, endpoint_.c_str(), nullptr, 0);
    if (rv != 0) {
        nng_close(socket_);
        return false;
    }

    connected_ = true;
    return true;
}

void IpcClient::disconnect() {
    if (connected_) {
        nng_close(socket_);
        connected_ = false;
    }
}

IpcResponse IpcClient::send_request(const IpcRequest& request) {
    if (!connected_ && !connect()) {
        return IpcResponse::failure("ERR_IPC_CONNECT", "Could not connect to daemon IPC endpoint: " + endpoint_);
    }

    std::string req_str = request.serialize();
    int rv = nng_send(socket_, (void*)req_str.data(), req_str.size(), 0);
    if (rv != 0) {
        disconnect();
        return IpcResponse::failure("ERR_IPC_SEND", nng_strerror(rv));
    }

    char* buf = nullptr;
    size_t sz = 0;
    rv = nng_recv(socket_, &buf, &sz, NNG_FLAG_ALLOC);
    if (rv != 0) {
        disconnect();
        return IpcResponse::failure("ERR_IPC_RECV", nng_strerror(rv));
    }

    std::string resp_str(buf, sz);
    nng_free(buf, sz);

    try {
        return IpcResponse::deserialize(resp_str);
    } catch (const std::exception& e) {
        return IpcResponse::failure("ERR_PARSE_RESPONSE", std::string("Failed to parse response: ") + e.what());
    }
}

IpcResponse IpcClient::daemon_status() {
    return send_request({"DAEMON_STATUS", nlohmann::json::object()});
}

IpcResponse IpcClient::daemon_down(bool force) {
    return send_request({"DAEMON_DOWN", {{"force", force}}});
}

IpcResponse IpcClient::daemon_drain(bool enabled, int32_t timeout_seconds) {
    return send_request({"DAEMON_DRAIN", {{"enabled", enabled}, {"timeout_seconds", timeout_seconds}}});
}

IpcResponse IpcClient::set_log_level(const std::string& level) {
    return send_request({"SET_LOG_LEVEL", {{"level", level}}});
}

IpcResponse IpcClient::get_log_config() {
    return send_request({"GET_LOG_CONFIG", nlohmann::json::object()});
}

IpcResponse IpcClient::abort_task(const std::string& task_id, const std::string& reason) {
    return send_request({"ABORT_TASK", {{"task_id", task_id}, {"reason", reason}}});
}

} // namespace legatus::daemon
