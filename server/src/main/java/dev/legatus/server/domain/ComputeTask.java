package dev.legatus.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.domain.Persistable;

/**
 * Metered guest Solix task executions, constraints, inputs, outputs,
 * and terminal execution telemetry (Table: tasks)
 */
@Entity
@Table(name = "tasks")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonIgnoreProperties(value = { "new", "id" })
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ComputeTask implements Serializable, Persistable<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 64)
    @Id
    @Column(name = "task_id", length = 64, nullable = false, unique = true)
    private String taskId;

    @NotNull
    @Size(max = 128)
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TaskPriority priority;

    @NotNull
    @Min(value = 0)
    @Max(value = 100)
    @Column(name = "priority_weight", nullable = false)
    private Integer priorityWeight;

    @NotNull
    @Column(name = "idempotent", nullable = false)
    private Boolean idempotent;

    @NotNull
    @Size(max = 128)
    @Column(name = "entry_point", length = 128, nullable = false)
    private String entryPoint;

    @NotNull
    @Size(max = 64)
    @Column(name = "bytecode_sha_256", length = 64, nullable = false)
    private String bytecodeSha256;

    @NotNull
    @Min(value = 0L)
    @Max(value = 1000000000L)
    @Column(name = "instruction_budget", nullable = false)
    private Long instructionBudget;

    @NotNull
    @Min(value = 8388608L)
    @Max(value = 1099511627776L)
    @Column(name = "max_memory_bytes", nullable = false)
    private Long maxMemoryBytes;

    @NotNull
    @Min(value = 0)
    @Max(value = 3600000)
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Min(value = 0L)
    @Column(name = "instructions_consumed")
    private Long instructionsConsumed;

    @Min(value = 0L)
    @Column(name = "peak_memory_bytes")
    private Long peakMemoryBytes;

    @Min(value = 0L)
    @Column(name = "wall_time_ms")
    private Long wallTimeMs;

    @Lob
    @Column(name = "input_json")
    private String inputJson;

    @Lob
    @Column(name = "env_json")
    private String envJson;

    @Lob
    @Column(name = "secret_refs_json")
    private String secretRefsJson;

    @Lob
    @Column(name = "output_json")
    private String outputJson;

    @Lob
    @Column(name = "error_json")
    private String errorJson;

    @NotNull
    @Min(value = 0)
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @NotNull
    @Min(value = 0)
    @Max(value = 10)
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @org.springframework.data.annotation.Transient
    @Transient
    private boolean isPersisted;

    @ManyToOne(fetch = FetchType.LAZY)
    private SolixFunction function;

    @ManyToOne(fetch = FetchType.LAZY)
    private WorkerNode assignedWorker;

    @Column(name = "notify_on_failure")
    private Boolean notifyOnFailure = false;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getTaskId() {
        return this.taskId;
    }

    public ComputeTask taskId(String taskId) {
        this.setTaskId(taskId);
        return this;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getName() {
        return this.name;
    }

    public ComputeTask name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskStatus getStatus() {
        return this.status;
    }

    public ComputeTask status(TaskStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return this.priority;
    }

    public ComputeTask priority(TaskPriority priority) {
        this.setPriority(priority);
        return this;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public Integer getPriorityWeight() {
        return this.priorityWeight;
    }

    public ComputeTask priorityWeight(Integer priorityWeight) {
        this.setPriorityWeight(priorityWeight);
        return this;
    }

    public void setPriorityWeight(Integer priorityWeight) {
        this.priorityWeight = priorityWeight;
    }

    public Boolean getIdempotent() {
        return this.idempotent;
    }

    public ComputeTask idempotent(Boolean idempotent) {
        this.setIdempotent(idempotent);
        return this;
    }

    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }

    public String getEntryPoint() {
        return this.entryPoint;
    }

    public ComputeTask entryPoint(String entryPoint) {
        this.setEntryPoint(entryPoint);
        return this;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    public String getBytecodeSha256() {
        return this.bytecodeSha256;
    }

    public ComputeTask bytecodeSha256(String bytecodeSha256) {
        this.setBytecodeSha256(bytecodeSha256);
        return this;
    }

    public void setBytecodeSha256(String bytecodeSha256) {
        this.bytecodeSha256 = bytecodeSha256;
    }

    public Long getInstructionBudget() {
        return this.instructionBudget;
    }

    public ComputeTask instructionBudget(Long instructionBudget) {
        this.setInstructionBudget(instructionBudget);
        return this;
    }

    public void setInstructionBudget(Long instructionBudget) {
        this.instructionBudget = instructionBudget;
    }

    public Long getMaxMemoryBytes() {
        return this.maxMemoryBytes;
    }

    public ComputeTask maxMemoryBytes(Long maxMemoryBytes) {
        this.setMaxMemoryBytes(maxMemoryBytes);
        return this;
    }

    public void setMaxMemoryBytes(Long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public Integer getTimeoutMs() {
        return this.timeoutMs;
    }

    public ComputeTask timeoutMs(Integer timeoutMs) {
        this.setTimeoutMs(timeoutMs);
        return this;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getExitCode() {
        return this.exitCode;
    }

    public ComputeTask exitCode(Integer exitCode) {
        this.setExitCode(exitCode);
        return this;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Long getInstructionsConsumed() {
        return this.instructionsConsumed;
    }

    public ComputeTask instructionsConsumed(Long instructionsConsumed) {
        this.setInstructionsConsumed(instructionsConsumed);
        return this;
    }

    public void setInstructionsConsumed(Long instructionsConsumed) {
        this.instructionsConsumed = instructionsConsumed;
    }

    public Long getPeakMemoryBytes() {
        return this.peakMemoryBytes;
    }

    public ComputeTask peakMemoryBytes(Long peakMemoryBytes) {
        this.setPeakMemoryBytes(peakMemoryBytes);
        return this;
    }

    public void setPeakMemoryBytes(Long peakMemoryBytes) {
        this.peakMemoryBytes = peakMemoryBytes;
    }

    public Long getWallTimeMs() {
        return this.wallTimeMs;
    }

    public ComputeTask wallTimeMs(Long wallTimeMs) {
        this.setWallTimeMs(wallTimeMs);
        return this;
    }

    public void setWallTimeMs(Long wallTimeMs) {
        this.wallTimeMs = wallTimeMs;
    }

    public String getInputJson() {
        return this.inputJson;
    }

    public ComputeTask inputJson(String inputJson) {
        this.setInputJson(inputJson);
        return this;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getEnvJson() {
        return this.envJson;
    }

    public ComputeTask envJson(String envJson) {
        this.setEnvJson(envJson);
        return this;
    }

    public void setEnvJson(String envJson) {
        this.envJson = envJson;
    }

    public String getSecretRefsJson() {
        return this.secretRefsJson;
    }

    public ComputeTask secretRefsJson(String secretRefsJson) {
        this.setSecretRefsJson(secretRefsJson);
        return this;
    }

    public void setSecretRefsJson(String secretRefsJson) {
        this.secretRefsJson = secretRefsJson;
    }

    public String getOutputJson() {
        return this.outputJson;
    }

    public ComputeTask outputJson(String outputJson) {
        this.setOutputJson(outputJson);
        return this;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public String getErrorJson() {
        return this.errorJson;
    }

    public ComputeTask errorJson(String errorJson) {
        this.setErrorJson(errorJson);
        return this;
    }

    public void setErrorJson(String errorJson) {
        this.errorJson = errorJson;
    }

    public Integer getAttemptCount() {
        return this.attemptCount;
    }

    public ComputeTask attemptCount(Integer attemptCount) {
        this.setAttemptCount(attemptCount);
        return this;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxRetries() {
        return this.maxRetries;
    }

    public ComputeTask maxRetries(Integer maxRetries) {
        this.setMaxRetries(maxRetries);
        return this;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public ComputeTask createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return this.startedAt;
    }

    public ComputeTask startedAt(Instant startedAt) {
        this.setStartedAt(startedAt);
        return this;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public ComputeTask completedAt(Instant completedAt) {
        this.setCompletedAt(completedAt);
        return this;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @PostLoad
    @PostPersist
    public void updateEntityState() {
        this.setIsPersisted();
    }

    @Override
    public String getId() {
        return this.taskId;
    }

    @org.springframework.data.annotation.Transient
    @Transient
    @Override
    public boolean isNew() {
        return !this.isPersisted;
    }

    public ComputeTask setIsPersisted() {
        this.isPersisted = true;
        return this;
    }

    public SolixFunction getFunction() {
        return this.function;
    }

    public void setFunction(SolixFunction solixFunction) {
        this.function = solixFunction;
    }

    public ComputeTask function(SolixFunction solixFunction) {
        this.setFunction(solixFunction);
        return this;
    }

    public WorkerNode getAssignedWorker() {
        return this.assignedWorker;
    }

    public void setAssignedWorker(WorkerNode workerNode) {
        this.assignedWorker = workerNode;
    }

    public ComputeTask assignedWorker(WorkerNode workerNode) {
        this.setAssignedWorker(workerNode);
        return this;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Boolean getNotifyOnFailure() {
        return this.notifyOnFailure;
    }

    public void setNotifyOnFailure(Boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }

    public ComputeTask notifyOnFailure(Boolean notifyOnFailure) {
        this.setNotifyOnFailure(notifyOnFailure);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComputeTask)) {
            return false;
        }
        return getTaskId() != null && getTaskId().equals(((ComputeTask) o).getTaskId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ComputeTask{" +
            "taskId=" + getTaskId() +
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
            "}";
    }
}
