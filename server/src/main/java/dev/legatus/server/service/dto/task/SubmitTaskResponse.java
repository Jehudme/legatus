package dev.legatus.server.service.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.TaskStatus;

/**
 * Response payload returned upon task submission (api-endpoint.md Section 4.1).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SubmitTaskResponse(
    @JsonProperty("task_id")
    String taskId,

    @JsonProperty("status")
    TaskStatus status,

    @JsonProperty("submitted_at")
    long submittedAt
) {}
