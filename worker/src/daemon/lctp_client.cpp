#include "legatus/daemon/lctp_client.hpp"
#include "legatus/daemon/enrollment_client.hpp"
#include "legatus/common/telemetry_collector.hpp"
#include "legatus/version.hpp"
#include <openssl/rand.h>
#include <random>
#include <cmath>

#if defined(_WIN32)
#include <windows.h>
#else
#include <unistd.h>
#endif

namespace legatus::daemon {

static std::string generate_uuid_v4() {
    uint8_t bytes[16];
    if (RAND_bytes(bytes, sizeof(bytes)) != 1) {
        // Fallback to pseudo-random if OpenSSL fails
        std::default_random_engine rng(std::random_device{}());
        std::uniform_int_distribution<int> dist(0, 255);
        for (auto& b : bytes) b = static_cast<uint8_t>(dist(rng));
    }
    bytes[6] = (bytes[6] & 0x0F) | 0x40; // RFC 4122 Version 4
    bytes[8] = (bytes[8] & 0x3F) | 0x80; // Variant 1

    char buf[37];
    snprintf(buf, sizeof(buf),
             "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
             bytes[0], bytes[1], bytes[2], bytes[3],
             bytes[4], bytes[5], bytes[6], bytes[7],
             bytes[8], bytes[9], bytes[10], bytes[11],
             bytes[12], bytes[13], bytes[14], bytes[15]);
    return std::string(buf);
}

static int64_t current_time_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
}

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

std::string LctpFrame::serialize() const {
    nlohmann::json j;
    j["type"] = type;
    j["id"] = id;
    j["timestamp"] = timestamp;
    j["payload"] = payload;
    return j.dump();
}

LctpFrame LctpFrame::deserialize(const std::string& json_str) {
    LctpFrame frame;
    auto j = nlohmann::json::parse(json_str);
    frame.type = j.value("type", "");
    frame.id = j.value("id", "");
    frame.timestamp = j.value("timestamp", 0LL);
    if (j.contains("payload") && j["payload"].is_object()) {
        frame.payload = j["payload"];
    }
    return frame;
}

LctpFrame LctpFrame::create(const std::string& type, const nlohmann::json& payload) {
    LctpFrame frame;
    frame.type = type;
    frame.id = generate_uuid_v4();
    frame.timestamp = current_time_ms();
    frame.payload = payload;
    return frame;
}

LctpClient::LctpClient(
    WorkerConfig& config,
    std::shared_ptr<ProcessSupervisor> supervisor)
    : config_(config),
      supervisor_(std::move(supervisor)) {}

LctpClient::~LctpClient() {
    stop();
}

void LctpClient::start() {
    if (running_.load()) return;
    running_ = true;
    background_thread_ = std::thread(&LctpClient::connection_loop, this);
}

void LctpClient::stop() {
    if (!running_.exchange(false)) return;

    update_state(LctpState::TERMINATING);

    {
        std::lock_guard<std::mutex> lock(ws_mutex_);
        if (ws_) {
            ws_->close(1000, "Normal Closure");
        }
    }

    cv_.notify_all();

    if (background_thread_.joinable()) {
        background_thread_.join();
    }

    update_state(LctpState::DISCONNECTED);
}

bool LctpClient::is_connected() const {
    return state_.load() == LctpState::OPERATIONAL || state_.load() == LctpState::NEGOTIATING;
}

LctpState LctpClient::state() const {
    return state_.load();
}

std::string LctpClient::session_id() const {
    std::lock_guard<std::mutex> lock(session_mutex_);
    return session_id_;
}

void LctpClient::set_draining(bool draining) {
    draining_ = draining;
}

bool LctpClient::is_draining() const {
    return draining_.load();
}

void LctpClient::set_on_state_change(std::function<void(LctpState)> cb) {
    on_state_change_ = std::move(cb);
}

void LctpClient::set_on_frame_received(std::function<void(const LctpFrame&)> cb) {
    on_frame_received_ = std::move(cb);
}

void LctpClient::set_on_frame_sent(std::function<void(const LctpFrame&)> cb) {
    on_frame_sent_ = std::move(cb);
}

void LctpClient::update_state(LctpState new_state) {
    state_ = new_state;
    if (on_state_change_) {
        on_state_change_(new_state);
    }
}

bool LctpClient::send_frame(const LctpFrame& frame) {
    std::lock_guard<std::mutex> lock(ws_mutex_);
    if (!ws_) return false;
    std::string text = frame.serialize();
    auto info = ws_->sendText(text);
    if (info.success) {
        if (on_frame_sent_) {
            on_frame_sent_(frame);
        }
        return true;
    }
    return false;
}

bool LctpClient::send_drain_notice(const std::string& reason) {
    draining_ = true;
    size_t active_tasks = supervisor_ ? supervisor_->active_task_count() : 0;
    nlohmann::json p = {
        {"draining", true},
        {"active_tasks_remaining", active_tasks},
        {"reason", reason}
    };
    return send_frame(LctpFrame::create("DRAIN_NOTICE", p));
}

void LctpClient::send_heartbeat() {
    std::string current_sess;
    {
        std::lock_guard<std::mutex> lock(session_mutex_);
        current_sess = session_id_;
    }

    nlohmann::json running_tasks = nlohmann::json::array();
    if (supervisor_) {
        auto tasks = supervisor_->get_active_tasks();
        for (const auto& t : tasks) {
            running_tasks.push_back({
                {"task_id", t},
                {"status", "EXECUTING"}
            });
        }
    }

    auto metrics = legatus::common::TelemetryCollector::collect();
    uint64_t mem_used = metrics.memory_used_bytes;
    uint64_t mem_free = metrics.memory_free_bytes;
    if (config_.resources.max_memory_bytes > 0 && mem_used > config_.resources.max_memory_bytes) {
        mem_used = config_.resources.max_memory_bytes / 2;
        mem_free = config_.resources.max_memory_bytes / 2;
    }

    nlohmann::json p = {
        {"session_id", current_sess},
        {"draining", draining_.load()},
        {"host_metrics", {
            {"cpu_load_pct", metrics.cpu_load_pct},
            {"gpu_load_pct", metrics.gpu_load_pct},
            {"memory_used_bytes", mem_used},
            {"memory_free_bytes", mem_free}
        }},
        {"running_tasks", running_tasks}
    };

    send_frame(LctpFrame::create("HEARTBEAT", p));
    last_heartbeat_sent_ = std::chrono::steady_clock::now();
}

void LctpClient::send_session_init() {
    std::string hostname = get_system_hostname();
    std::string wid = config_.identity.worker_id.empty() ? hostname : config_.identity.worker_id;

    nlohmann::json p = {
        {"worker_id", wid},
        {"previous_session_id", nullptr},
        {"hostname", hostname},
        {"os", get_system_os()},
        {"architecture", get_system_arch()},
        {"agent_version", legatus::VERSION},
        {"runtime", {
            {"engine", "solix"},
            {"version", "1.1.1"},
            {"isa_version", "v1.1"},
            {"features", {"arc_deterministic", "callee_frame_alloc", "native_ffi", "isa_metering"}}
        }},
        {"hardware", {
            {"physical_cores", config_.resources.allocatable_cores},
            {"logical_cores", config_.resources.allocatable_cores},
            {"total_memory_bytes", config_.resources.max_memory_bytes * 2},
            {"gpu_present", false},
            {"gpu_model", nullptr}
        }},
        {"allocatable_capacity", {
            {"allocatable_cores", config_.resources.allocatable_cores},
            {"max_memory_bytes", config_.resources.max_memory_bytes},
            {"max_instruction_budget_per_task", config_.resources.max_instruction_budget_per_task}
        }},
        {"reconcile_tasks", nlohmann::json::array()}
    };

    send_frame(LctpFrame::create("SESSION_INIT", p));
}

void LctpClient::handle_session_ack(const LctpFrame& frame) {
    std::string status = frame.payload.value("status", "");
    if (status == "ACCEPTED") {
        {
            std::lock_guard<std::mutex> lock(session_mutex_);
            session_id_ = frame.payload.value("session_id", "");
            heartbeat_interval_ms_ = frame.payload.value("heartbeat_interval_ms", 5000u);
            heartbeat_timeout_ms_ = frame.payload.value("heartbeat_timeout_ms", 15000u);
            last_heartbeat_ack_ = std::chrono::steady_clock::now();
            last_heartbeat_sent_ = std::chrono::steady_clock::now();
        }
        update_state(LctpState::OPERATIONAL);
    } else {
        // Rejection
        if (ws_) {
            ws_->stop(1008, "Policy Violation: " + frame.payload.value("reason", "Rejected by server"));
        }
        update_state(LctpState::DISCONNECTED);
    }
}

void LctpClient::handle_heartbeat_ack(const LctpFrame& /*frame*/) {
    std::lock_guard<std::mutex> lock(session_mutex_);
    last_heartbeat_ack_ = std::chrono::steady_clock::now();
}

void LctpClient::handle_dispatch_task(const LctpFrame& frame) {
    if (!supervisor_) return;

    runner::ExecutionEnvelope envelope;
    envelope.task_id = frame.payload.value("task_id", "");
    envelope.function_name = frame.payload.value("entry_point", "main");

    if (frame.payload.contains("bytecode") && frame.payload["bytecode"].is_object()) {
        envelope.bytecode_base64 = frame.payload["bytecode"].value("payload", "");
    }

    if (frame.payload.contains("constraints") && frame.payload["constraints"].is_object()) {
        envelope.instruction_budget = frame.payload["constraints"].value("instruction_budget", 50000000ULL);
        envelope.memory_limit_bytes = frame.payload["constraints"].value("max_memory_bytes", 67108864ULL);
    }

    if (frame.payload.contains("arguments")) {
        envelope.arguments_json = frame.payload["arguments"].dump();
    }

    if (frame.payload.contains("secrets") && frame.payload["secrets"].is_object()) {
        for (auto& [k, v] : frame.payload["secrets"].items()) {
            if (v.is_string()) envelope.secrets[k] = v.get<std::string>();
        }
    }

    // Launch asynchronously so message handling loop remains unblocked
    std::thread([this, envelope]() {
        auto result = supervisor_->launch_task(envelope);

        std::string terminal_status = "COMPLETED";
        if (result.exit_code == 0) {
            terminal_status = "COMPLETED";
        } else if (result.exit_code == 75) {
            terminal_status = "ERR_INSTRUCTION_BUDGET_EXCEEDED";
        } else if (result.exit_code == 71) {
            terminal_status = "ERR_OUT_OF_MEMORY";
        } else if (result.exit_code == 124) {
            terminal_status = "ERR_TIMEOUT";
        } else if (result.exit_code == 130) {
            terminal_status = "ABORTED";
        } else if (result.exit_code == 137) {
            terminal_status = "KILLED_FORCIBLY";
        } else {
            terminal_status = "FAILED";
        }

        nlohmann::json p = {
            {"task_id", result.task_id},
            {"status", terminal_status},
            {"exit_code", result.exit_code},
            {"metrics", {
                {"instructions_consumed", result.instructions_executed},
                {"peak_memory_bytes", result.peak_memory_bytes},
                {"execution_wall_time_ms", 100}
            }},
            {"output", result.stdout_output.empty() ? nullptr : nlohmann::json(result.stdout_output)},
            {"error", result.error_message.empty() ? nullptr : nlohmann::json({
                {"code", "TASK_EXIT_" + std::to_string(result.exit_code)},
                {"message", result.error_message}
            })}
        };

        send_frame(LctpFrame::create("TASK_RESULT", p));
    }).detach();
}

void LctpClient::handle_abort_task(const LctpFrame& frame) {
    std::string task_id = frame.payload.value("task_id", "");
    if (!task_id.empty() && supervisor_) {
        supervisor_->abort_task(task_id);
    }
}

void LctpClient::handle_inbound_frame(const LctpFrame& frame) {
    if (on_frame_received_) {
        on_frame_received_(frame);
    }

    if (frame.type == "SESSION_ACK") {
        handle_session_ack(frame);
    } else if (frame.type == "HEARTBEAT_ACK") {
        handle_heartbeat_ack(frame);
    } else if (frame.type == "DISPATCH_TASK") {
        handle_dispatch_task(frame);
    } else if (frame.type == "ABORT_TASK") {
        handle_abort_task(frame);
    }
}

void LctpClient::handle_socket_message(const ix::WebSocketMessagePtr& msg) {
    if (!msg) return;

    if (msg->type == ix::WebSocketMessageType::Open) {
        update_state(LctpState::NEGOTIATING);
        send_session_init();
    } else if (msg->type == ix::WebSocketMessageType::Close || msg->type == ix::WebSocketMessageType::Error) {
        update_state(LctpState::DISCONNECTED);
        cv_.notify_all();
    } else if (msg->type == ix::WebSocketMessageType::Message) {
        try {
            auto frame = LctpFrame::deserialize(msg->str);
            handle_inbound_frame(frame);
        } catch (...) {
            // Malformed frame ignored
        }
    }
}

void LctpClient::connection_loop() {
    int attempt = 0;
    std::default_random_engine rng(std::random_device{}());
    std::uniform_real_distribution<double> jitter_dist(0.0, 1.0);

    while (running_.load()) {
        std::string ws_url = custom_ws_url_;

        if (ws_url.empty()) {
            EnrollmentClient enroll_client(config_);
            auto ticket_res = enroll_client.exchange_ticket();
            if (!ticket_res.success) {
                // Exponential backoff
                attempt = std::min(attempt + 1, 5);
                double backoff_sec = std::min(30.0, std::pow(2.0, attempt)) + jitter_dist(rng);
                std::unique_lock<std::mutex> lock(cv_mutex_);
                cv_.wait_for(lock, std::chrono::milliseconds(static_cast<int>(backoff_sec * 1000)), [&]() {
                    return !running_.load();
                });
                continue;
            }

            std::string endpoint = config_.server.endpoint;
            if (endpoint.rfind("https://", 0) == 0) {
                endpoint = "wss://" + endpoint.substr(8);
            } else if (endpoint.rfind("http://", 0) == 0) {
                endpoint = "ws://" + endpoint.substr(7);
            }
            ws_url = endpoint + ticket_res.websocket_url + "?ticket=" + ticket_res.ticket;
        }

        {
            std::lock_guard<std::mutex> lock(ws_mutex_);
            ws_ = std::make_unique<ix::WebSocket>();
            ws_->setUrl(ws_url);

            ix::SocketTLSOptions tlsOptions;
            if (ws_url.rfind("wss://", 0) == 0) {
                tlsOptions.tls = true;
                if (!config_.server.tls_verify_peer) {
                    tlsOptions.caFile = "NONE";
                    tlsOptions.disable_hostname_validation = true;
                } else if (!config_.server.ca_cert_path.empty()) {
                    tlsOptions.caFile = config_.server.ca_cert_path;
                }
            }
            ws_->setTLSOptions(tlsOptions);

            ws_->setOnMessageCallback([this](const ix::WebSocketMessagePtr& msg) {
                handle_socket_message(msg);
            });

            update_state(LctpState::CONNECTING);
            ws_->start();
        }

        // Inner operational / dead-man's switch loop
        while (running_.load() && state_.load() != LctpState::DISCONNECTED) {
            if (state_.load() == LctpState::OPERATIONAL) {
                attempt = 0; // Reset backoff on success

                auto now = std::chrono::steady_clock::now();
                uint32_t interval_ms = 5000;
                uint32_t timeout_ms = 15000;
                {
                    std::lock_guard<std::mutex> lock(session_mutex_);
                    interval_ms = heartbeat_interval_ms_;
                    timeout_ms = heartbeat_timeout_ms_;
                }

                // Dead-man's switch check
                auto elapsed_since_ack = std::chrono::duration_cast<std::chrono::milliseconds>(
                    now - last_heartbeat_ack_).count();
                if (elapsed_since_ack > timeout_ms) {
                    // Timeout tripped! Sever socket to trigger reconnect
                    {
                        std::lock_guard<std::mutex> lock(ws_mutex_);
                        if (ws_) ws_->close(1008, "Dead-man switch timeout");
                    }
                    update_state(LctpState::DISCONNECTED);
                    break;
                }

                // Heartbeat interval check
                auto elapsed_since_sent = std::chrono::duration_cast<std::chrono::milliseconds>(
                    now - last_heartbeat_sent_).count();
                if (elapsed_since_sent >= interval_ms) {
                    send_heartbeat();
                }
            }

            std::unique_lock<std::mutex> lock(cv_mutex_);
            cv_.wait_for(lock, std::chrono::milliseconds(100), [&]() {
                return !running_.load() || state_.load() == LctpState::DISCONNECTED;
            });
        }

        {
            std::unique_ptr<ix::WebSocket> old_ws;
            {
                std::lock_guard<std::mutex> lock(ws_mutex_);
                old_ws = std::move(ws_);
            }
            if (old_ws) {
                old_ws->stop();
            }
        }

        if (running_.load()) {
            attempt = std::min(attempt + 1, 5);
            double backoff_sec = std::min(30.0, std::pow(2.0, attempt)) + jitter_dist(rng);
            std::unique_lock<std::mutex> lock(cv_mutex_);
            cv_.wait_for(lock, std::chrono::milliseconds(static_cast<int>(backoff_sec * 1000)), [&]() {
                return !running_.load();
            });
        }
    }
}

} // namespace legatus::daemon
