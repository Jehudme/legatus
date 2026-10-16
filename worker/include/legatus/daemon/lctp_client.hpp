#pragma once

#include "legatus/daemon/worker_config.hpp"
#include "legatus/daemon/config_manager.hpp"
#include "legatus/daemon/process_supervisor.hpp"
#include "legatus/daemon/bytecode_cache.hpp"
#include <ixwebsocket/IXWebSocket.h>
#include <nlohmann/json.hpp>
#include <string>
#include <memory>
#include <atomic>
#include <thread>
#include <chrono>
#include <functional>
#include <mutex>
#include <condition_variable>

namespace legatus::daemon {

enum class LctpState {
    DISCONNECTED,
    CONNECTING,
    NEGOTIATING,
    OPERATIONAL,
    TERMINATING
};

struct LctpFrame {
    std::string type;
    std::string id;
    int64_t timestamp = 0;
    nlohmann::json payload = nlohmann::json::object();

    std::string serialize() const;
    static LctpFrame deserialize(const std::string& json_str);
    static LctpFrame create(const std::string& type, const nlohmann::json& payload = nlohmann::json::object());
};

class LctpClient {
public:
    LctpClient(
        WorkerConfig& config,
        std::shared_ptr<ProcessSupervisor> supervisor,
        std::shared_ptr<ConfigManager> config_manager = nullptr);
    ~LctpClient();

    LctpClient(const LctpClient&) = delete;
    LctpClient& operator=(const LctpClient&) = delete;

    void start();
    void stop();

    bool is_connected() const;
    LctpState state() const;
    std::string session_id() const;

    std::shared_ptr<ConfigManager> config_manager() const { return config_manager_; }
    std::shared_ptr<BytecodeCache> bytecode_cache() const { return bytecode_cache_; }
    void set_bytecode_cache(std::shared_ptr<BytecodeCache> cache) { bytecode_cache_ = std::move(cache); }

    void set_draining(bool draining);
    bool is_draining() const;

    // Send immediate expedited DRAIN_NOTICE wire frame
    bool send_drain_notice(const std::string& reason = "OPERATOR_MAINTENANCE");

    // Send custom frame over active WebSocket connection
    bool send_frame(const LctpFrame& frame);

    // Callbacks for telemetry and testing hooks
    void set_on_state_change(std::function<void(LctpState)> cb);
    void set_on_frame_received(std::function<void(const LctpFrame&)> cb);
    void set_on_frame_sent(std::function<void(const LctpFrame&)> cb);

    // Explicitly send a heartbeat frame (useful for deterministic testing)
    void send_heartbeat();

    void set_custom_ws_url(const std::string& url) {
        custom_ws_url_ = url;
    }

private:
    void connection_loop();
    void handle_socket_message(const ix::WebSocketMessagePtr& msg);
    void handle_inbound_frame(const LctpFrame& frame);

    void send_session_init();
    void handle_session_ack(const LctpFrame& frame);
    void handle_heartbeat_ack(const LctpFrame& frame);
    void handle_config_sync_resp(const LctpFrame& frame);
    void handle_config_update_push(const LctpFrame& frame);
    void handle_dispatch_task(const LctpFrame& frame);
    void handle_abort_task(const LctpFrame& frame);

    void update_state(LctpState new_state);

    WorkerConfig& config_;
    std::shared_ptr<ProcessSupervisor> supervisor_;
    std::shared_ptr<ConfigManager> config_manager_;

    std::string custom_ws_url_;
    std::atomic<bool> running_{false};
    std::atomic<LctpState> state_{LctpState::DISCONNECTED};
    std::atomic<bool> draining_{false};

    mutable std::mutex session_mutex_;
    std::string session_id_;
    std::chrono::steady_clock::time_point last_heartbeat_ack_;
    std::chrono::steady_clock::time_point last_heartbeat_sent_;
    uint32_t heartbeat_interval_ms_{5000};
    uint32_t heartbeat_timeout_ms_{15000};

    mutable std::mutex ws_mutex_;
    std::unique_ptr<ix::WebSocket> ws_;
    std::thread background_thread_;
    std::mutex cv_mutex_;
    std::condition_variable cv_;

    std::mutex bytecode_cache_mutex_;
    std::shared_ptr<BytecodeCache> bytecode_cache_;

    std::function<void(LctpState)> on_state_change_;
    std::function<void(const LctpFrame&)> on_frame_received_;
    std::function<void(const LctpFrame&)> on_frame_sent_;
};

} // namespace legatus::daemon
