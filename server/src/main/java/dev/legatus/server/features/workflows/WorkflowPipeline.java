package dev.legatus.server.features.workflows;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorkflowPipeline {

    @JsonProperty("pipeline_id")
    private String pipelineId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private PipelineStatus status = PipelineStatus.PENDING;

    @JsonProperty("failure_policy")
    private FailurePolicy failurePolicy = FailurePolicy.ABORT_PIPELINE;

    @JsonProperty("steps")
    private Map<String, WorkflowStep> steps = new ConcurrentHashMap<>();

    @JsonProperty("created_at")
    private Instant createdAt = Instant.now();

    @JsonProperty("completed_at")
    private Instant completedAt;

    public WorkflowPipeline() {}

    public WorkflowPipeline(String pipelineId, String name, FailurePolicy failurePolicy, Map<String, WorkflowStep> steps) {
        this.pipelineId = pipelineId;
        this.name = name;
        this.failurePolicy = failurePolicy != null ? failurePolicy : FailurePolicy.ABORT_PIPELINE;
        if (steps != null) {
            this.steps.putAll(steps);
        }
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PipelineStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineStatus status) {
        this.status = status;
    }

    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public Map<String, WorkflowStep> getSteps() {
        return steps;
    }

    public void setSteps(Map<String, WorkflowStep> steps) {
        this.steps = steps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
