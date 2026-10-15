#pragma once

#include <string>
#include <memory>
#include <atomic>
#include <thread>
#include <nlohmann/json.hpp>
#include <nng/nng.h>

namespace legatus::daemon {

inline std::string default_ipc_endpoint() {
#if defined(_WIN32)
    return "ipc://legatus_worker_ipc";
#else
    return "ipc:///tmp/legatus_worker.ipc";
#endif
}

struct IpcRequest {
    std::string action;
    nlohmann::json params = nlohmann::json::object();

    std::string serialize() const;
    static IpcRequest deserialize(const std::string& json_str);
};

struct IpcResponse {
    std::string status = "SUCCESS"; // "SUCCESS" or "ERROR"
    nlohmann::json data = nlohmann::json::object();
    struct Error {
        std::string code;
        std::string message;
    } error;

    static IpcResponse success(const nlohmann::json& data = nlohmann::json::object());
    static IpcResponse failure(const std::string& code, const std::string& message);

    std::string serialize() const;
    static IpcResponse deserialize(const std::string& json_str);
};

class IIpcHandler {
public:
    virtual ~IIpcHandler() = default;

    virtual IpcResponse handle_daemon_status() = 0;
    virtual IpcResponse handle_daemon_down(bool force) = 0;
    virtual IpcResponse handle_daemon_drain(bool enabled, int32_t timeout_seconds) = 0;
    virtual IpcResponse handle_set_log_level(const std::string& level) = 0;
    virtual IpcResponse handle_get_log_config() = 0;
    virtual IpcResponse handle_abort_task(const std::string& task_id, const std::string& reason) = 0;
};

class IpcServer {
public:
    explicit IpcServer(std::string endpoint = default_ipc_endpoint());
    ~IpcServer();

    IpcServer(const IpcServer&) = delete;
    IpcServer& operator=(const IpcServer&) = delete;

    void set_handler(std::shared_ptr<IIpcHandler> handler);
    bool start();
    void stop();
    bool is_running() const;

    const std::string& endpoint() const { return endpoint_; }

private:
    void run_loop();

    std::string endpoint_;
    std::shared_ptr<IIpcHandler> handler_;
    std::atomic<bool> running_{false};
    std::thread worker_thread_;
    nng_socket socket_{NNG_SOCKET_INITIALIZER};
};

class IpcClient {
public:
    explicit IpcClient(std::string endpoint = default_ipc_endpoint());
    ~IpcClient();

    IpcClient(const IpcClient&) = delete;
    IpcClient& operator=(const IpcClient&) = delete;

    bool connect();
    void disconnect();
    bool is_connected() const { return connected_; }

    IpcResponse send_request(const IpcRequest& request);

    // High-level API methods corresponding to the 6 formal IPC actions
    IpcResponse daemon_status();
    IpcResponse daemon_down(bool force = false);
    IpcResponse daemon_drain(bool enabled = true, int32_t timeout_seconds = 0);
    IpcResponse set_log_level(const std::string& level);
    IpcResponse get_log_config();
    IpcResponse abort_task(const std::string& task_id, const std::string& reason = "User requested");

private:
    std::string endpoint_;
    bool connected_{false};
    nng_socket socket_{NNG_SOCKET_INITIALIZER};
};

} // namespace legatus::daemon
