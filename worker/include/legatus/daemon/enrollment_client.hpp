#pragma once

#include "legatus/daemon/worker_config.hpp"
#include "legatus/common/crypto_utils.hpp"
#include <string>
#include <chrono>
#include <functional>

namespace legatus::daemon {

#ifdef ERROR
#undef ERROR
#endif

enum class EnrollmentStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXPIRED,
    ERROR_STATE
};

#ifndef ERROR
    static constexpr EnrollmentStatus ERROR = EnrollmentStatus::ERROR_STATE;
#endif

struct EnrollmentResult {
    EnrollmentStatus status = EnrollmentStatus::ERROR_STATE;
    std::string enrollment_id;
    std::string secret_token;
    std::string assigned_cluster;
    std::string fingerprint_hex;
    std::string visual_code;
    uint32_t poll_interval_ms = 3000;
    std::string error_message;
};

struct EphemeralTicketResult {
    bool success = false;
    std::string ticket;
    std::string websocket_url;
    std::string error_message;
};

class EnrollmentClient {
public:
    explicit EnrollmentClient(WorkerConfig& config);
    ~EnrollmentClient() = default;

    EnrollmentResult register_node(const common::Ed25519KeyPair& keypair);

    EnrollmentResult poll_status(
        const std::string& enrollment_id,
        std::chrono::milliseconds interval = std::chrono::milliseconds(3000),
        std::chrono::milliseconds timeout = std::chrono::milliseconds(60000),
        std::function<void(const EnrollmentResult&)> on_poll_step = nullptr);

    EnrollmentResult enroll_and_persist(
        const std::string& config_file_path,
        std::chrono::milliseconds timeout = std::chrono::milliseconds(60000),
        std::function<void(const EnrollmentResult&)> on_status = nullptr);

    EphemeralTicketResult exchange_ticket();

private:
    WorkerConfig& config_;
};

} // namespace legatus::daemon
