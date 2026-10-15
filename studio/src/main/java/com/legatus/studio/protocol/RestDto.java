package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Immutable DTO records for all Legatus Server HTTP REST endpoints.
 * Conforms to docs/specs/protocols/api-endpoint.md.
 */
public final class RestDto {

    private RestDto() {}

    // Pagination Envelope
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PageResponse<T>(
            @JsonProperty("content") List<T> content,
            @JsonProperty("page") int page,
            @JsonProperty("size") int size,
            @JsonProperty("total_elements") long total_elements,
            @JsonProperty("total_pages") int total_pages
    ) {}

    // Auth
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginRequest(String username, String password) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(
            String access_token,
            String refresh_token,
            long expires_in_seconds,
            String studio_ws_ticket,
            String studio_ws_url,
            String account_id,
            String username,
            String role
    ) {
        public LoginResponse(String access_token, String refresh_token, long expires_in_seconds, String account_id, String username, String role) {
            this(access_token, refresh_token, expires_in_seconds, null, null, account_id, username, role);
        }
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BytecodeUploadResponse(
            @JsonProperty("sha256") String sha256,
            @JsonProperty("size_bytes") long size_bytes,
            @JsonProperty("download_url") String download_url
    ) {}

    // Secrets Vault
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SecretDto(
            @JsonProperty("secret_key") @JsonAlias("key") String secret_key,
            @JsonProperty("scope") String scope,
            @JsonProperty("created_at") String created_at,
            @JsonProperty("updated_at") String updated_at
    ) {
        public String key() {
            return secret_key;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateSecretRequest(
            @JsonProperty("secret_key") String secret_key,
            @JsonProperty("scope") String scope,
            @JsonProperty("value") String value
    ) {}

    // Audit Log
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditLogEntryDto(
            @JsonProperty("sequence_num") @JsonAlias({"sequence_number", "sequenceNum"}) long sequence_number,
            @JsonProperty("event_id") @JsonAlias("eventId") String event_id,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("actor_id") @JsonAlias("actorId") String actor_id,
            @JsonProperty("event_type") @JsonAlias({"action", "eventType"}) String action,
            @JsonProperty("target_type") @JsonAlias({"resource_type", "targetType"}) String resource_type,
            @JsonProperty("target_id") @JsonAlias({"resource_id", "targetId"}) String resource_id,
            @JsonProperty("action_status") @JsonAlias({"status", "actionStatus"}) String status,
            @JsonProperty("record_hash") @JsonAlias({"sha256_hash", "recordHash"}) String sha256_hash,
            @JsonProperty("prev_record_hash") @JsonAlias({"previous_hash", "prevRecordHash"}) String previous_hash
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditVerifyResponse(
            @JsonProperty("chain_integrity") @JsonAlias("valid") boolean valid,
            @JsonProperty("verified_records_count") @JsonAlias("records_verified") long records_verified,
            @JsonProperty("genesis_hash") @JsonAlias("genesisHash") String genesis_hash,
            @JsonProperty("head_hash") @JsonAlias("headHash") String head_hash,
            @JsonProperty("discrepancy") @JsonAlias("error") String error
    ) {}

    // Generic API Error Envelope
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiErrorResponse(
            String error_code,
            String message,
            long timestamp
    ) {}
}
