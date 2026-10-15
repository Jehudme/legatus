#include "legatus/daemon/enrollment_client.hpp"
#include "legatus/version.hpp"
#include <ixwebsocket/IXHttpClient.h>
#include <nlohmann/json.hpp>
#include <random>
#include <thread>
#include <chrono>

#if defined(_WIN32)
#include <windows.h>
#else
#include <unistd.h>
#endif

namespace legatus::daemon {

static std::string get_system_hostname() {
#if defined(_WIN32)
    char name_buf[256] = {0};
    DWORD size = sizeof(name_buf);
    if (GetComputerNameA(name_buf, &size)) {
        return name_buf;
    }
#else
    char name_buf[256] = {0};
    if (gethostname(name_buf, sizeof(name_buf) - 1) == 0) {
        return name_buf;
    }
#endif
    return "unknown-host";
}

static std::string get_system_os() {
#if defined(_WIN32)
    return "windows";
#elif defined(__APPLE__)
    return "darwin";
#else
    return "linux";
#endif
}

static std::string get_system_arch() {
#if defined(__x86_64__) || defined(_M_X64)
    return "x86_64";
#elif defined(__aarch64__) || defined(_M_ARM64)
    return "arm64";
#else
    return "unknown";
#endif
}

EnrollmentClient::EnrollmentClient(WorkerConfig& config)
    : config_(config) {}

static void configure_http_client(ix::HttpClient& client, const ServerConfig& server_cfg, const std::string& url) {
    ix::SocketTLSOptions tlsOptions;
    if (url.rfind("https://", 0) == 0) {
        tlsOptions.tls = true;
        if (!server_cfg.tls_verify_peer) {
            tlsOptions.caFile = "NONE";
            tlsOptions.disable_hostname_validation = true;
        } else if (!server_cfg.ca_cert_path.empty()) {
            tlsOptions.caFile = server_cfg.ca_cert_path;
        }
    }
    client.setTLSOptions(tlsOptions);
}

EnrollmentResult EnrollmentClient::register_node(const common::Ed25519KeyPair& keypair) {
    EnrollmentResult res;

    std::string hostname = get_system_hostname();
    if (config_.identity.worker_id.empty()) {
        config_.identity.worker_id = hostname;
    }

    nlohmann::json payload = {
        {"worker_id", config_.identity.worker_id},
        {"hostname", hostname},
        {"os", get_system_os()},
        {"architecture", get_system_arch()},
        {"agent_version", legatus::VERSION},
        {"ed25519_public_key", keypair.public_key_base64},
        {"hardware", {
            {"physical_cores", config_.resources.allocatable_cores},
            {"logical_cores", config_.resources.allocatable_cores},
            {"total_memory_bytes", config_.resources.max_memory_bytes * 2},
            {"gpu_present", false}
        }},
        {"allocatable_capacity", {
            {"allocatable_cores", config_.resources.allocatable_cores},
            {"max_memory_bytes", config_.resources.max_memory_bytes},
            {"max_instruction_budget_per_task", config_.resources.max_instruction_budget_per_task}
        }}
    };

    std::string url = config_.server.endpoint + "/api/v1/workers/register";
    ix::HttpClient client;
    configure_http_client(client, config_.server, url);

    auto args = client.createRequest(url, ix::HttpClient::kPost);
    args->extraHeaders["Content-Type"] = "application/json";
    args->connectTimeout = 10;
    args->transferTimeout = 15;

    auto response = client.post(url, payload.dump(), args);
    if (!response) {
        res.status = EnrollmentStatus::ERROR;
        res.error_message = "Null response from registration endpoint";
        return res;
    }

    if (response->statusCode != 200 && response->statusCode != 202) {
        res.status = EnrollmentStatus::ERROR;
        res.error_message = "Registration failed with HTTP " + std::to_string(response->statusCode) +
                            ": " + response->body + " (" + response->errorMsg + ")";
        return res;
    }

    try {
        nlohmann::json resp_j = nlohmann::json::parse(response->body);
        res.enrollment_id = resp_j.value("enrollment_id", "");
        std::string status_str = resp_j.value("status", "PENDING_APPROVAL");
        res.fingerprint_hex = resp_j.value("fingerprint_hex", keypair.fingerprint);
        res.visual_code = resp_j.value("visual_code", keypair.visual_code);
        res.poll_interval_ms = resp_j.value("poll_interval_ms", 3000u);

        if (status_str == "APPROVED") {
            res.status = EnrollmentStatus::APPROVED;
            res.secret_token = resp_j.value("secret_token", "");
            res.assigned_cluster = resp_j.value("assigned_cluster", "");
        } else if (status_str == "REJECTED") {
            res.status = EnrollmentStatus::REJECTED;
        } else {
            res.status = EnrollmentStatus::PENDING_APPROVAL;
        }
    } catch (const std::exception& e) {
        res.status = EnrollmentStatus::ERROR;
        res.error_message = std::string("Failed to parse registration response JSON: ") + e.what();
    }

    return res;
}

EnrollmentResult EnrollmentClient::poll_status(
    const std::string& enrollment_id,
    std::chrono::milliseconds interval,
    std::chrono::milliseconds timeout,
    std::function<void(const EnrollmentResult&)> on_poll_step) {

    auto start_time = std::chrono::steady_clock::now();
    std::default_random_engine rng(std::random_device{}());
    std::uniform_int_distribution<int> jitter_dist(-500, 500);

    EnrollmentResult current_res;
    current_res.enrollment_id = enrollment_id;
    current_res.status = EnrollmentStatus::PENDING_APPROVAL;

    std::string url = config_.server.endpoint + "/api/v1/workers/register/" + enrollment_id + "/status";

    while (true) {
        auto now = std::chrono::steady_clock::now();
        if (now - start_time > timeout) {
            current_res.status = EnrollmentStatus::EXPIRED;
            current_res.error_message = "Quarantine enrollment polling timed out after " +
                                        std::to_string(timeout.count()) + " ms";
            if (on_poll_step) on_poll_step(current_res);
            return current_res;
        }

        ix::HttpClient client;
        configure_http_client(client, config_.server, url);

        auto args = client.createRequest(url, ix::HttpClient::kGet);
        args->extraHeaders["Accept"] = "application/json";
        args->connectTimeout = 5;
        args->transferTimeout = 10;

        auto response = client.get(url, args);
        if (response && response->statusCode == 200) {
            try {
                nlohmann::json resp_j = nlohmann::json::parse(response->body);
                std::string status_str = resp_j.value("status", "PENDING_APPROVAL");
                if (status_str == "APPROVED") {
                    current_res.status = EnrollmentStatus::APPROVED;
                    current_res.secret_token = resp_j.value("secret_token", "");
                    current_res.assigned_cluster = resp_j.value("assigned_cluster", "");
                    if (on_poll_step) on_poll_step(current_res);
                    return current_res;
                } else if (status_str == "REJECTED") {
                    current_res.status = EnrollmentStatus::REJECTED;
                    current_res.error_message = "Enrollment rejected by operator";
                    if (on_poll_step) on_poll_step(current_res);
                    return current_res;
                } else if (status_str == "EXPIRED") {
                    current_res.status = EnrollmentStatus::EXPIRED;
                    current_res.error_message = "Enrollment expired";
                    if (on_poll_step) on_poll_step(current_res);
                    return current_res;
                } else {
                    current_res.status = EnrollmentStatus::PENDING_APPROVAL;
                }
            } catch (const std::exception& e) {
                current_res.error_message = std::string("JSON parse error in status poll: ") + e.what();
            }
        } else if (response) {
            current_res.error_message = "Status poll HTTP " + std::to_string(response->statusCode);
        } else {
            current_res.error_message = "Connection failure during status poll";
        }

        if (on_poll_step) {
            on_poll_step(current_res);
        }

        int jitter_ms = jitter_dist(rng);
        auto sleep_dur = interval + std::chrono::milliseconds(jitter_ms);
        if (sleep_dur < std::chrono::milliseconds(500)) {
            sleep_dur = std::chrono::milliseconds(500);
        }
        std::this_thread::sleep_for(sleep_dur);
    }
}

EnrollmentResult EnrollmentClient::enroll_and_persist(
    const std::string& config_file_path,
    std::chrono::milliseconds timeout,
    std::function<void(const EnrollmentResult&)> on_status) {

    auto keypair = common::generate_ed25519_keypair();
    config_.identity.public_key = keypair.public_key_base64;

    auto reg_res = register_node(keypair);
    if (reg_res.status == EnrollmentStatus::ERROR) {
        if (on_status) on_status(reg_res);
        return reg_res;
    }

    if (reg_res.status == EnrollmentStatus::PENDING_APPROVAL) {
        if (on_status) on_status(reg_res);
        auto poll_dur = std::chrono::milliseconds(reg_res.poll_interval_ms > 0 ? reg_res.poll_interval_ms : 3000);
        reg_res = poll_status(reg_res.enrollment_id, poll_dur, timeout, on_status);
    }

    if (reg_res.status == EnrollmentStatus::APPROVED) {
        config_.identity.secret_token = reg_res.secret_token;
        config_.save_to_file(config_file_path);
    }

    return reg_res;
}

EphemeralTicketResult EnrollmentClient::exchange_ticket() {
    EphemeralTicketResult result;

    if (config_.identity.secret_token.empty()) {
        result.success = false;
        result.error_message = "Cannot exchange ticket: missing secret_token in config";
        return result;
    }

    std::string url = config_.server.endpoint + "/api/v1/auth/worker";
    nlohmann::json payload = {
        {"worker_id", config_.identity.worker_id},
        {"secret_token", config_.identity.secret_token},
        {"hostname", get_system_hostname()},
        {"os", get_system_os()},
        {"architecture", get_system_arch()},
        {"agent_version", legatus::VERSION},
        {"allocatable_capacity", {
            {"allocatable_cores", config_.resources.allocatable_cores},
            {"max_memory_bytes", config_.resources.max_memory_bytes},
            {"max_instruction_budget_per_task", config_.resources.max_instruction_budget_per_task}
        }}
    };

    ix::HttpClient client;
    configure_http_client(client, config_.server, url);

    auto args = client.createRequest(url, ix::HttpClient::kPost);
    args->extraHeaders["Content-Type"] = "application/json";
    args->connectTimeout = 5;
    args->transferTimeout = 10;

    auto response = client.post(url, payload.dump(), args);
    if (!response || response->statusCode != 200) {
        result.success = false;
        result.error_message = response ? "Ticket exchange HTTP " + std::to_string(response->statusCode) + ": " + response->body
                                        : "Ticket exchange connection failed";
        return result;
    }

    try {
        nlohmann::json resp_j = nlohmann::json::parse(response->body);
        result.ticket = resp_j.value("ticket", "");
        result.websocket_url = resp_j.value("websocket_url", "/ws/worker");
        result.success = !result.ticket.empty();
    } catch (const std::exception& e) {
        result.success = false;
        result.error_message = std::string("Failed to parse ticket JSON: ") + e.what();
    }

    return result;
}

} // namespace legatus::daemon
