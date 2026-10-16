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
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkerDto(
            String worker_id,
            String friendly_name,
            String hostname,
            String source_ip,
            String os,
            String architecture,
            String state,
            Boolean active,
            int allocatable_cores,
            long max_memory_bytes,
            Double target_cpu_pct,
            Double target_ram_pct,
            Integer max_concurrent_tasks,
            Boolean auto_renew_lease,
            Long remaining_lease_sec,
            String lease_expires_at,
            Long uptime_seconds,
            double cpu_load_pct,
            long memory_used_bytes,
            int active_tasks_count,
            Long config_version,
            String last_heartbeat,
            String last_seen_at,
            String created_at
    ) {
        public WorkerDto(
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
        ) {
            this(worker_id, hostname, hostname, source_ip, os, architecture, state, true,
                    allocatable_cores, max_memory_bytes, 75.0, 80.0, 16, true,
                    86400L, "", 0L, cpu_load_pct, memory_used_bytes, active_tasks_count, 1L, last_heartbeat, "", "");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApproveEnrollmentRequest(
            @JsonProperty("lease_duration") String lease_duration,
            @JsonProperty("friendly_name") String friendly_name
    ) {
        public ApproveEnrollmentRequest(String lease_duration) {
            this(lease_duration, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RejectEnrollmentRequest(String reason, boolean blacklist_ip) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RenameWorkerRequest(
            @JsonProperty("friendly_name") String friendly_name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateThresholdsRequest(
            @JsonProperty("target_cpu_pct") Double target_cpu_pct,
            @JsonProperty("target_ram_pct") Double target_ram_pct,
            @JsonProperty("max_concurrent_tasks") Integer max_concurrent_tasks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AutoRenewLeaseRequest(
            @JsonProperty("auto_renew") Boolean auto_renew
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AutoAcceptRequest(
            @JsonProperty("auto_accept") Boolean auto_accept
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AutoAcceptResponse(
            @JsonProperty("auto_accept") Boolean auto_accept
    ) {}

    // Tasks & Bytecode
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTaskRequest(
            @JsonProperty("name") String name,
            @JsonProperty("priority") String priority,
            @JsonProperty("entry_point") String entry_point,
            @JsonProperty("bytecode") BytecodeRef bytecode,
            @JsonProperty("constraints") ConstraintsPayload constraints,
            @JsonProperty("arguments") java.util.Map<String, Object> arguments,
            @JsonProperty("notify_on_failure") Boolean notify_on_failure,
            @JsonProperty("unlimited_instructions") Boolean unlimited_instructions,
            @JsonProperty("unlimited_time") Boolean unlimited_time
    ) {
        public CreateTaskRequest(String name, String priority, String entry_point, BytecodeRef bytecode,
                                 ConstraintsPayload constraints, java.util.Map<String, Object> arguments) {
            this(name, priority, entry_point, bytecode, constraints, arguments, false, false, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BytecodeRef(
            @JsonProperty("format") String format,
            @JsonProperty("sha256") String sha256
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConstraintsPayload(
            @JsonProperty("instruction_budget") Long instruction_budget,
            @JsonProperty("max_memory_bytes") Long max_memory_bytes,
            @JsonProperty("timeout_ms") Integer timeout_ms,
            @JsonProperty("unlimited_instructions") Boolean unlimited_instructions,
            @JsonProperty("unlimited_time") Boolean unlimited_time
    ) {
        public ConstraintsPayload(Long instruction_budget, Long max_memory_bytes, Integer timeout_ms) {
            this(instruction_budget, max_memory_bytes, timeout_ms, false, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTaskResponse(
            @JsonProperty("task_id") String task_id,
            @JsonProperty("status") String status,
            @JsonProperty("submitted_at") Object submitted_at
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SolixFunctionDto(
            @JsonProperty("functionId") java.util.UUID functionId,
            @JsonProperty("name") String name,
            @JsonProperty("semver") String semver,
            @JsonProperty("entryPoint") String entryPoint,
            @JsonProperty("bytecodeFormat") String bytecodeFormat,
            @JsonProperty("bytecodeSha256") String bytecodeSha256,
            @JsonProperty("defaultBudget") Long defaultBudget,
            @JsonProperty("defaultMemoryBytes") Long defaultMemoryBytes,
            @JsonProperty("defaultTimeoutMs") Integer defaultTimeoutMs,
            @JsonProperty("createdAt") Object createdAt,
            @JsonProperty("tags") String tags,
            @JsonProperty("description") String description,
            @JsonProperty("invocationsLastHour") Long invocationsLastHour
    ) {
        public SolixFunctionDto(java.util.UUID functionId, String name, String semver, String entryPoint,
                                String bytecodeFormat, String bytecodeSha256, Long defaultBudget,
                                Long defaultMemoryBytes, Integer defaultTimeoutMs, Object createdAt) {
            this(functionId, name, semver, entryPoint, bytecodeFormat, bytecodeSha256, defaultBudget, defaultMemoryBytes, defaultTimeoutMs, createdAt, null, null, 0L);
        }

        public String effectiveSha256() {
            return bytecodeSha256 != null ? bytecodeSha256 : "";
        }
        public String effectiveEntryPoint() {
            return entryPoint != null && !entryPoint.isBlank() ? entryPoint : "main";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExecutionProfileDto(
            @JsonProperty("profile_id") String profile_id,
            @JsonProperty("profile_name") String profile_name,
            @JsonProperty("function_id") java.util.UUID function_id,
            @JsonProperty("arguments_json") String arguments_json,
            @JsonProperty("instruction_budget") Long instruction_budget,
            @JsonProperty("unlimited_instructions") Boolean unlimited_instructions,
            @JsonProperty("time_limit_ms") Long time_limit_ms,
            @JsonProperty("unlimited_time") Boolean unlimited_time,
            @JsonProperty("memory_mb") Long memory_mb,
            @JsonProperty("priority") String priority,
            @JsonProperty("notify_on_failure") Boolean notify_on_failure,
            @JsonProperty("created_at") Object created_at,
            @JsonProperty("updated_at") Object updated_at,
            @JsonProperty("version") Long version
    ) {
        public ExecutionProfileDto(String profile_id, String profile_name, java.util.UUID function_id,
                                   String arguments_json, Long instruction_budget, Boolean unlimited_instructions,
                                   Long time_limit_ms, Boolean unlimited_time, Long memory_mb,
                                   String priority, Boolean notify_on_failure, Long version) {
            this(profile_id, profile_name, function_id, arguments_json, instruction_budget, unlimited_instructions,
                 time_limit_ms, unlimited_time, memory_mb, priority, notify_on_failure, null, null, version);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateFunctionMetadataRequest(
            @JsonProperty("description") String description,
            @JsonProperty("tags") String tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskMetricsDto(
            @JsonProperty("instructions_consumed") Long instructions_consumed,
            @JsonProperty("peak_memory_bytes") Long peak_memory_bytes,
            @JsonProperty("execution_wall_time_ms") Long execution_wall_time_ms
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskStatusResponse(
            @JsonProperty("task_id") String task_id,
            @JsonProperty("name") String name,
            @JsonProperty("status") String status,
            @JsonProperty("exit_code") Integer exit_code,
            @JsonProperty("assigned_worker_id") String assigned_worker_id,
            @JsonProperty("instructions_consumed") Long instructions_consumed,
            @JsonProperty("peak_memory_bytes") Long peak_memory_bytes,
            @JsonProperty("execution_wall_time_ms") Long execution_wall_time_ms,
            @JsonProperty("created_at") Object created_at,
            @JsonProperty("started_at") Object started_at,
            @JsonProperty("completed_at") Object completed_at,
            @JsonProperty("finished_at") Object finished_at,
            @JsonProperty("worker_friendly_name") String worker_friendly_name,
            @JsonProperty("function_name") String function_name,
            @JsonProperty("profile_name") String profile_name,
            @JsonProperty("metrics") TaskMetricsDto metrics
    ) {
        public TaskStatusResponse(String task_id, String name, String status, Integer exit_code, String assigned_worker_id,
                                  Long instructions_consumed, Long peak_memory_bytes, Long execution_wall_time_ms,
                                  String created_at, String finished_at) {
            this(task_id, name, status, exit_code, assigned_worker_id, instructions_consumed, peak_memory_bytes, execution_wall_time_ms, created_at, null, null, finished_at, null, null, null, null);
        }

        public long effectiveInstructions() {
            if (instructions_consumed != null) return instructions_consumed;
            if (metrics != null && metrics.instructions_consumed != null) return metrics.instructions_consumed;
            return 0L;
        }

        public long effectiveMemory() {
            if (peak_memory_bytes != null) return peak_memory_bytes;
            if (metrics != null && metrics.peak_memory_bytes != null) return metrics.peak_memory_bytes;
            return 0L;
        }

        public long effectiveWallTime() {
            if (execution_wall_time_ms != null) return execution_wall_time_ms;
            if (metrics != null && metrics.execution_wall_time_ms != null) return metrics.execution_wall_time_ms;
            return 0L;
        }
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateAccountRequest(
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("initial_password") @JsonAlias({ "initial_password", "password", "initialPassword" }) String password,
            @JsonProperty("role") String role
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateAccountRequest(
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("role") String role
    ) {
        public UpdateAccountRequest(String email, String role) {
            this(null, email, role);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateAccountStatusRequest(
            @JsonProperty("status") String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResetPasswordRequest(
            @JsonProperty("new_password") @JsonAlias({ "new_password", "newPassword", "password" }) String new_password
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserStateChangedPayload(
            @JsonProperty("account_id") String account_id,
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("role") String role,
            @JsonProperty("status") String status,
            @JsonProperty("action") String action
    ) {}

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
