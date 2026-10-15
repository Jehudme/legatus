package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Immutable DTO records for all Legatus Server HTTP REST endpoints.
 * Conforms to docs/specs/protocols/api-endpoint.md.
 */
public final class RestDto {

    private RestDto() {}

    // Auth
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginRequest(String username, String password) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(
            String access_token,
            String refresh_token,
            long expires_in_seconds,
            String account_id,
            String username,
            String role
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefreshTokenRequest(String refresh_token) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TicketResponse(String ticket, long expires_in_seconds) {}

    // Workers & Enrollments
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkerDto(
            String worker_id,
            String hostname,
            String source_ip,
            String os,
            String architecture,
            String state,
            int allocatable_cores,
            long max_memory_bytes,
            double cpu_load_pct,
            long memory_used_bytes,
            int active_tasks_count,
            String last_heartbeat
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EnrollmentDto(
            String enrollment_id,
            String worker_id,
            String hostname,
            String source_ip,
            String os,
            String architecture,
            String fingerprint_hex,
            String visual_code,
            int allocatable_cores,
            long max_memory_bytes,
            String submitted_at
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApproveEnrollmentRequest(String lease_duration) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RejectEnrollmentRequest(String reason, boolean blacklist_ip) {}

    // Tasks & Bytecode
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTaskRequest(
            String name,
            String bytecode_sha256,
            String entrypoint,
            long instruction_budget,
            long max_memory_bytes,
            String input_data_base64
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTaskResponse(String task_id, String status, String created_at) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskStatusResponse(
            String task_id,
            String name,
            String status,
            Integer exit_code,
            String assigned_worker_id,
            Long instructions_consumed,
            Long peak_memory_bytes,
            Long execution_wall_time_ms,
            String created_at,
            String finished_at
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AbortTaskRequest(String reason) {}

    // User Management
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountDto(
            String account_id,
            String username,
            String email,
            String role,
            String status,
            String created_at,
            String last_login_at
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateAccountRequest(
            String username,
            String email,
            String password,
            String role
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateAccountRequest(String email, String role) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateAccountStatusRequest(String status) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResetPasswordRequest(String new_password) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdatePasswordRequest(String old_password, String new_password) {}

    // Secrets Vault
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SecretDto(
            String key,
            String scope,
            String created_at,
            String updated_at
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateSecretRequest(
            String key,
            String scope,
            String plaintext_value
    ) {}

    // Audit Log
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditLogEntryDto(
            long sequence_number,
            String event_id,
            String timestamp,
            String actor_id,
            String action,
            String resource_type,
            String resource_id,
            String status,
            String sha256_hash,
            String previous_hash
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditVerifyResponse(
            boolean valid,
            long records_verified,
            String genesis_hash,
            String head_hash,
            String error
    ) {}

    // Generic API Error Envelope
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiErrorResponse(
            String error_code,
            String message,
            long timestamp
    ) {}
}
