package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for EVENT_TASK_LIFECYCLE frame.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskLifecyclePayload(
        @JsonProperty("task_id") String task_id,
        @JsonProperty("name") String name,
        @JsonProperty("status") String status,
        @JsonProperty("exit_code") Integer exit_code,
        @JsonProperty("assigned_worker_id") String assigned_worker_id,
        @JsonProperty("instructions_consumed") Long instructions_consumed,
        @JsonProperty("peak_memory_bytes") Long peak_memory_bytes,
        @JsonProperty("execution_wall_time_ms") Long execution_wall_time_ms,
        @JsonProperty("started_at") Object started_at,
        @JsonProperty("ended_at") @JsonAlias({ "ended_at", "completed_at", "finished_at" }) Object ended_at,
        @JsonProperty("worker_friendly_name") String worker_friendly_name,
        @JsonProperty("profile_name") String profile_name,
        @JsonProperty("instruction_budget") Long instruction_budget,
        @JsonProperty("timeout_ms") Integer timeout_ms
) {
    public TaskLifecyclePayload(String task_id, String name, String status, Integer exit_code,
                                String assigned_worker_id, Long instructions_consumed,
                                Long peak_memory_bytes, Long execution_wall_time_ms) {
        this(task_id, name, status, exit_code, assigned_worker_id, instructions_consumed, peak_memory_bytes, execution_wall_time_ms, null, null, null, null, null, null);
    }

    public TaskLifecyclePayload(String task_id, String name, String status, Integer exit_code,
                                String assigned_worker_id, Long instructions_consumed,
                                Long peak_memory_bytes, Long execution_wall_time_ms,
                                Object started_at, Object ended_at,
                                String worker_friendly_name, String profile_name) {
        this(task_id, name, status, exit_code, assigned_worker_id, instructions_consumed, peak_memory_bytes, execution_wall_time_ms, started_at, ended_at, worker_friendly_name, profile_name, null, null);
    }
}
