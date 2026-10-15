package dev.legatus.server.service.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import java.util.Map;

/**
 * Detailed task status, metrics, and terminal execution outcome (api-endpoint.md Section 4.2).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskStatusResponse(
    @JsonProperty("task_id")
    String taskId,

    @JsonProperty("name")
    String name,

    @JsonProperty("status")
    TaskStatus status,

    @JsonProperty("exit_code")
    Integer exitCode,

    @JsonProperty("priority")
    TaskPriority priority,

    @JsonProperty("assigned_worker_id")
    String assignedWorkerId,

    @JsonProperty("attempt_count")
    int attemptCount,

    @JsonProperty("submitted_at")
    long submittedAt,

    @JsonProperty("started_at")
    Long startedAt,

    @JsonProperty("completed_at")
    Long completedAt,

    @JsonProperty("metrics")
    TaskMetrics metrics,

    @JsonProperty("output")
    Map<String, Object> output,

    @JsonProperty("error")
    Map<String, Object> error
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaskMetrics(
        @JsonProperty("instructions_consumed")
        Long instructionsConsumed,

        @JsonProperty("peak_memory_bytes")
        Long peakMemoryBytes,

        @JsonProperty("execution_wall_time_ms")
        Long executionWallTimeMs
    ) {}
}
