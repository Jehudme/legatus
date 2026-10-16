package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP frame broadcast when a task configured with notify_on_failure fails (MS-009).
 */
public record EventTaskFailedAlertFrame(
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    @Override
    public String type() {
        return "EVENT_TASK_FAILED_ALERT";
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("task_name") String taskName,
        @JsonProperty("function_name") String functionName,
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("exit_code") Integer exitCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("failed_at") long failedAt
    ) {}
}
