package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for EVENT_TASK_FAILED_ALERT frame broadcast over LSTCP (MS-009).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskFailedAlertPayload(
        @JsonProperty("task_id") String task_id,
        @JsonProperty("task_name") String task_name,
        @JsonProperty("function_name") String function_name,
        @JsonProperty("worker_id") String worker_id,
        @JsonProperty("exit_code") Integer exit_code,
        @JsonProperty("error_message") String error_message,
        @JsonProperty("failed_at") Long failed_at
) {
    public String taskId() { return task_id; }
    public String taskName() { return task_name; }
    public String functionName() { return function_name; }
    public String workerId() { return worker_id; }
    public Integer exitCode() { return exit_code; }
    public String errorMessage() { return error_message; }
    public Long failedAt() { return failed_at; }
}
