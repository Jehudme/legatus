package dev.legatus.server.service.dto;

import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link dev.legatus.server.domain.ComputeTask} entity.
 */
@Schema(description = "Metered guest Solix task executions, constraints, inputs, outputs,\nand terminal execution telemetry (Table: tasks)")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ComputeTaskDTO implements Serializable {

    @NotNull
    @Size(max = 64)
    private String taskId;

    @NotNull
    @Size(max = 128)
    private String name;

    @NotNull
    private TaskStatus status;

    @NotNull
    private TaskPriority priority;

    @NotNull
    @Min(value = 0)
    @Max(value = 100)
    private Integer priorityWeight;

    @NotNull
    private Boolean idempotent;

    @NotNull
    @Size(max = 128)
    private String entryPoint;

    @NotNull
    @Size(max = 64)
    private String bytecodeSha256;

    @NotNull
    @Min(value = 1000L)
    @Max(value = 1000000000L)
    private Long instructionBudget;

    @NotNull
    @Min(value = 8388608L)
    @Max(value = 1099511627776L)
    private Long maxMemoryBytes;

    @NotNull
    @Min(value = 100)
    @Max(value = 3600000)
    private Integer timeoutMs;

    private Integer exitCode;

    @Min(value = 0L)
    private Long instructionsConsumed;

    @Min(value = 0L)
    private Long peakMemoryBytes;

    @Min(value = 0L)
    private Long wallTimeMs;

    @Lob
    private String inputJson;

    @Lob
    private String envJson;

    @Lob
    private String secretRefsJson;

    @Lob
    private String outputJson;

    @Lob
    private String errorJson;

    @NotNull
    @Min(value = 0)
    private Integer attemptCount;

    @NotNull
    @Min(value = 0)
    @Max(value = 10)
    private Integer maxRetries;

    @NotNull
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private SolixFunctionDTO function;

    private WorkerNodeDTO assignedWorker;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public Integer getPriorityWeight() {
        return priorityWeight;
    }

    public void setPriorityWeight(Integer priorityWeight) {
        this.priorityWeight = priorityWeight;
    }

    public Boolean getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    public String getBytecodeSha256() {
        return bytecodeSha256;
    }

    public void setBytecodeSha256(String bytecodeSha256) {
        this.bytecodeSha256 = bytecodeSha256;
    }

    public Long getInstructionBudget() {
        return instructionBudget;
    }

    public void setInstructionBudget(Long instructionBudget) {
        this.instructionBudget = instructionBudget;
    }

    public Long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(Long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Long getInstructionsConsumed() {
        return instructionsConsumed;
    }

    public void setInstructionsConsumed(Long instructionsConsumed) {
        this.instructionsConsumed = instructionsConsumed;
    }

    public Long getPeakMemoryBytes() {
        return peakMemoryBytes;
    }

    public void setPeakMemoryBytes(Long peakMemoryBytes) {
        this.peakMemoryBytes = peakMemoryBytes;
    }

    public Long getWallTimeMs() {
        return wallTimeMs;
    }

    public void setWallTimeMs(Long wallTimeMs) {
        this.wallTimeMs = wallTimeMs;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getEnvJson() {
        return envJson;
    }

    public void setEnvJson(String envJson) {
        this.envJson = envJson;
    }

    public String getSecretRefsJson() {
        return secretRefsJson;
    }

    public void setSecretRefsJson(String secretRefsJson) {
        this.secretRefsJson = secretRefsJson;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public String getErrorJson() {
        return errorJson;
    }

    public void setErrorJson(String errorJson) {
        this.errorJson = errorJson;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public SolixFunctionDTO getFunction() {
        return function;
    }

    public void setFunction(SolixFunctionDTO function) {
        this.function = function;
    }

    public WorkerNodeDTO getAssignedWorker() {
        return assignedWorker;
    }

    public void setAssignedWorker(WorkerNodeDTO assignedWorker) {
        this.assignedWorker = assignedWorker;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComputeTaskDTO)) {
            return false;
        }

        ComputeTaskDTO computeTaskDTO = (ComputeTaskDTO) o;
        if (this.taskId == null) {
            return false;
        }
        return Objects.equals(this.taskId, computeTaskDTO.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.taskId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ComputeTaskDTO{" +
            "taskId='" + getTaskId() + "'" +
            ", name='" + getName() + "'" +
            ", status='" + getStatus() + "'" +
            ", priority='" + getPriority() + "'" +
            ", priorityWeight=" + getPriorityWeight() +
            ", idempotent='" + getIdempotent() + "'" +
            ", entryPoint='" + getEntryPoint() + "'" +
            ", bytecodeSha256='" + getBytecodeSha256() + "'" +
            ", instructionBudget=" + getInstructionBudget() +
            ", maxMemoryBytes=" + getMaxMemoryBytes() +
            ", timeoutMs=" + getTimeoutMs() +
            ", exitCode=" + getExitCode() +
            ", instructionsConsumed=" + getInstructionsConsumed() +
            ", peakMemoryBytes=" + getPeakMemoryBytes() +
            ", wallTimeMs=" + getWallTimeMs() +
            ", inputJson='" + getInputJson() + "'" +
            ", envJson='" + getEnvJson() + "'" +
            ", secretRefsJson='" + getSecretRefsJson() + "'" +
            ", outputJson='" + getOutputJson() + "'" +
            ", errorJson='" + getErrorJson() + "'" +
            ", attemptCount=" + getAttemptCount() +
            ", maxRetries=" + getMaxRetries() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", startedAt='" + getStartedAt() + "'" +
            ", completedAt='" + getCompletedAt() + "'" +
            ", function=" + getFunction() +
            ", assignedWorker=" + getAssignedWorker() +
            "}";
    }
}
