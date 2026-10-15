package dev.legatus.server.service.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response payload returned upon aborting a compute task (api-endpoint.md Section 4.3).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AbortTaskResponse(
    @JsonProperty("task_id")
    String taskId,

    @JsonProperty("status")
    String status,

    @JsonProperty("assigned_worker_id")
    String assignedWorkerId,

    @JsonProperty("escalation_deadline_utc")
    Long escalationDeadlineUtc
) {}
