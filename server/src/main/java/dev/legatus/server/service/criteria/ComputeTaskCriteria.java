package dev.legatus.server.service.criteria;

import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link dev.legatus.server.domain.ComputeTask} entity. This class is used
 * in {@link dev.legatus.server.web.rest.ComputeTaskResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /compute-tasks?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ComputeTaskCriteria implements Serializable, Criteria {

    /**
     * Class for filtering TaskStatus
     */
    public static class TaskStatusFilter extends Filter<TaskStatus> {

        public TaskStatusFilter() {}

        public TaskStatusFilter(TaskStatusFilter filter) {
            super(filter);
        }

        @Override
        public TaskStatusFilter copy() {
            return new TaskStatusFilter(this);
        }
    }

    /**
     * Class for filtering TaskPriority
     */
    public static class TaskPriorityFilter extends Filter<TaskPriority> {

        public TaskPriorityFilter() {}

        public TaskPriorityFilter(TaskPriorityFilter filter) {
            super(filter);
        }

        @Override
        public TaskPriorityFilter copy() {
            return new TaskPriorityFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private StringFilter taskId;

    private StringFilter name;

    private TaskStatusFilter status;

    private TaskPriorityFilter priority;

    private IntegerFilter priorityWeight;

    private BooleanFilter idempotent;

    private StringFilter entryPoint;

    private StringFilter bytecodeSha256;

    private LongFilter instructionBudget;

    private LongFilter maxMemoryBytes;

    private IntegerFilter timeoutMs;

    private IntegerFilter exitCode;

    private LongFilter instructionsConsumed;

    private LongFilter peakMemoryBytes;

    private LongFilter wallTimeMs;

    private IntegerFilter attemptCount;

    private IntegerFilter maxRetries;

    private InstantFilter createdAt;

    private InstantFilter startedAt;

    private InstantFilter completedAt;

    private UUIDFilter functionId;

    private StringFilter assignedWorkerId;

    private Boolean distinct;

    public ComputeTaskCriteria() {}

    public ComputeTaskCriteria(ComputeTaskCriteria other) {
        this.taskId = other.optionalTaskId().map(StringFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(TaskStatusFilter::copy).orElse(null);
        this.priority = other.optionalPriority().map(TaskPriorityFilter::copy).orElse(null);
        this.priorityWeight = other.optionalPriorityWeight().map(IntegerFilter::copy).orElse(null);
        this.idempotent = other.optionalIdempotent().map(BooleanFilter::copy).orElse(null);
        this.entryPoint = other.optionalEntryPoint().map(StringFilter::copy).orElse(null);
        this.bytecodeSha256 = other.optionalBytecodeSha256().map(StringFilter::copy).orElse(null);
        this.instructionBudget = other.optionalInstructionBudget().map(LongFilter::copy).orElse(null);
        this.maxMemoryBytes = other.optionalMaxMemoryBytes().map(LongFilter::copy).orElse(null);
        this.timeoutMs = other.optionalTimeoutMs().map(IntegerFilter::copy).orElse(null);
        this.exitCode = other.optionalExitCode().map(IntegerFilter::copy).orElse(null);
        this.instructionsConsumed = other.optionalInstructionsConsumed().map(LongFilter::copy).orElse(null);
        this.peakMemoryBytes = other.optionalPeakMemoryBytes().map(LongFilter::copy).orElse(null);
        this.wallTimeMs = other.optionalWallTimeMs().map(LongFilter::copy).orElse(null);
        this.attemptCount = other.optionalAttemptCount().map(IntegerFilter::copy).orElse(null);
        this.maxRetries = other.optionalMaxRetries().map(IntegerFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.startedAt = other.optionalStartedAt().map(InstantFilter::copy).orElse(null);
        this.completedAt = other.optionalCompletedAt().map(InstantFilter::copy).orElse(null);
        this.functionId = other.optionalFunctionId().map(UUIDFilter::copy).orElse(null);
        this.assignedWorkerId = other.optionalAssignedWorkerId().map(StringFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public ComputeTaskCriteria copy() {
        return new ComputeTaskCriteria(this);
    }

    public StringFilter getTaskId() {
        return taskId;
    }

    public Optional<StringFilter> optionalTaskId() {
        return Optional.ofNullable(taskId);
    }

    public StringFilter taskId() {
        if (taskId == null) {
            setTaskId(new StringFilter());
        }
        return taskId;
    }

    public void setTaskId(StringFilter taskId) {
        this.taskId = taskId;
    }

    public StringFilter getName() {
        return name;
    }

    public Optional<StringFilter> optionalName() {
        return Optional.ofNullable(name);
    }

    public StringFilter name() {
        if (name == null) {
            setName(new StringFilter());
        }
        return name;
    }

    public void setName(StringFilter name) {
        this.name = name;
    }

    public TaskStatusFilter getStatus() {
        return status;
    }

    public Optional<TaskStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public TaskStatusFilter status() {
        if (status == null) {
            setStatus(new TaskStatusFilter());
        }
        return status;
    }

    public void setStatus(TaskStatusFilter status) {
        this.status = status;
    }

    public TaskPriorityFilter getPriority() {
        return priority;
    }

    public Optional<TaskPriorityFilter> optionalPriority() {
        return Optional.ofNullable(priority);
    }

    public TaskPriorityFilter priority() {
        if (priority == null) {
            setPriority(new TaskPriorityFilter());
        }
        return priority;
    }

    public void setPriority(TaskPriorityFilter priority) {
        this.priority = priority;
    }

    public IntegerFilter getPriorityWeight() {
        return priorityWeight;
    }

    public Optional<IntegerFilter> optionalPriorityWeight() {
        return Optional.ofNullable(priorityWeight);
    }

    public IntegerFilter priorityWeight() {
        if (priorityWeight == null) {
            setPriorityWeight(new IntegerFilter());
        }
        return priorityWeight;
    }

    public void setPriorityWeight(IntegerFilter priorityWeight) {
        this.priorityWeight = priorityWeight;
    }

    public BooleanFilter getIdempotent() {
        return idempotent;
    }

    public Optional<BooleanFilter> optionalIdempotent() {
        return Optional.ofNullable(idempotent);
    }

    public BooleanFilter idempotent() {
        if (idempotent == null) {
            setIdempotent(new BooleanFilter());
        }
        return idempotent;
    }

    public void setIdempotent(BooleanFilter idempotent) {
        this.idempotent = idempotent;
    }

    public StringFilter getEntryPoint() {
        return entryPoint;
    }

    public Optional<StringFilter> optionalEntryPoint() {
        return Optional.ofNullable(entryPoint);
    }

    public StringFilter entryPoint() {
        if (entryPoint == null) {
            setEntryPoint(new StringFilter());
        }
        return entryPoint;
    }

    public void setEntryPoint(StringFilter entryPoint) {
        this.entryPoint = entryPoint;
    }

    public StringFilter getBytecodeSha256() {
        return bytecodeSha256;
    }

    public Optional<StringFilter> optionalBytecodeSha256() {
        return Optional.ofNullable(bytecodeSha256);
    }

    public StringFilter bytecodeSha256() {
        if (bytecodeSha256 == null) {
            setBytecodeSha256(new StringFilter());
        }
        return bytecodeSha256;
    }

    public void setBytecodeSha256(StringFilter bytecodeSha256) {
        this.bytecodeSha256 = bytecodeSha256;
    }

    public LongFilter getInstructionBudget() {
        return instructionBudget;
    }

    public Optional<LongFilter> optionalInstructionBudget() {
        return Optional.ofNullable(instructionBudget);
    }

    public LongFilter instructionBudget() {
        if (instructionBudget == null) {
            setInstructionBudget(new LongFilter());
        }
        return instructionBudget;
    }

    public void setInstructionBudget(LongFilter instructionBudget) {
        this.instructionBudget = instructionBudget;
    }

    public LongFilter getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public Optional<LongFilter> optionalMaxMemoryBytes() {
        return Optional.ofNullable(maxMemoryBytes);
    }

    public LongFilter maxMemoryBytes() {
        if (maxMemoryBytes == null) {
            setMaxMemoryBytes(new LongFilter());
        }
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(LongFilter maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public IntegerFilter getTimeoutMs() {
        return timeoutMs;
    }

    public Optional<IntegerFilter> optionalTimeoutMs() {
        return Optional.ofNullable(timeoutMs);
    }

    public IntegerFilter timeoutMs() {
        if (timeoutMs == null) {
            setTimeoutMs(new IntegerFilter());
        }
        return timeoutMs;
    }

    public void setTimeoutMs(IntegerFilter timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public IntegerFilter getExitCode() {
        return exitCode;
    }

    public Optional<IntegerFilter> optionalExitCode() {
        return Optional.ofNullable(exitCode);
    }

    public IntegerFilter exitCode() {
        if (exitCode == null) {
            setExitCode(new IntegerFilter());
        }
        return exitCode;
    }

    public void setExitCode(IntegerFilter exitCode) {
        this.exitCode = exitCode;
    }

    public LongFilter getInstructionsConsumed() {
        return instructionsConsumed;
    }

    public Optional<LongFilter> optionalInstructionsConsumed() {
        return Optional.ofNullable(instructionsConsumed);
    }

    public LongFilter instructionsConsumed() {
        if (instructionsConsumed == null) {
            setInstructionsConsumed(new LongFilter());
        }
        return instructionsConsumed;
    }

    public void setInstructionsConsumed(LongFilter instructionsConsumed) {
        this.instructionsConsumed = instructionsConsumed;
    }

    public LongFilter getPeakMemoryBytes() {
        return peakMemoryBytes;
    }

    public Optional<LongFilter> optionalPeakMemoryBytes() {
        return Optional.ofNullable(peakMemoryBytes);
    }

    public LongFilter peakMemoryBytes() {
        if (peakMemoryBytes == null) {
            setPeakMemoryBytes(new LongFilter());
        }
        return peakMemoryBytes;
    }

    public void setPeakMemoryBytes(LongFilter peakMemoryBytes) {
        this.peakMemoryBytes = peakMemoryBytes;
    }

    public LongFilter getWallTimeMs() {
        return wallTimeMs;
    }

    public Optional<LongFilter> optionalWallTimeMs() {
        return Optional.ofNullable(wallTimeMs);
    }

    public LongFilter wallTimeMs() {
        if (wallTimeMs == null) {
            setWallTimeMs(new LongFilter());
        }
        return wallTimeMs;
    }

    public void setWallTimeMs(LongFilter wallTimeMs) {
        this.wallTimeMs = wallTimeMs;
    }

    public IntegerFilter getAttemptCount() {
        return attemptCount;
    }

    public Optional<IntegerFilter> optionalAttemptCount() {
        return Optional.ofNullable(attemptCount);
    }

    public IntegerFilter attemptCount() {
        if (attemptCount == null) {
            setAttemptCount(new IntegerFilter());
        }
        return attemptCount;
    }

    public void setAttemptCount(IntegerFilter attemptCount) {
        this.attemptCount = attemptCount;
    }

    public IntegerFilter getMaxRetries() {
        return maxRetries;
    }

    public Optional<IntegerFilter> optionalMaxRetries() {
        return Optional.ofNullable(maxRetries);
    }

    public IntegerFilter maxRetries() {
        if (maxRetries == null) {
            setMaxRetries(new IntegerFilter());
        }
        return maxRetries;
    }

    public void setMaxRetries(IntegerFilter maxRetries) {
        this.maxRetries = maxRetries;
    }

    public InstantFilter getCreatedAt() {
        return createdAt;
    }

    public Optional<InstantFilter> optionalCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public InstantFilter createdAt() {
        if (createdAt == null) {
            setCreatedAt(new InstantFilter());
        }
        return createdAt;
    }

    public void setCreatedAt(InstantFilter createdAt) {
        this.createdAt = createdAt;
    }

    public InstantFilter getStartedAt() {
        return startedAt;
    }

    public Optional<InstantFilter> optionalStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public InstantFilter startedAt() {
        if (startedAt == null) {
            setStartedAt(new InstantFilter());
        }
        return startedAt;
    }

    public void setStartedAt(InstantFilter startedAt) {
        this.startedAt = startedAt;
    }

    public InstantFilter getCompletedAt() {
        return completedAt;
    }

    public Optional<InstantFilter> optionalCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public InstantFilter completedAt() {
        if (completedAt == null) {
            setCompletedAt(new InstantFilter());
        }
        return completedAt;
    }

    public void setCompletedAt(InstantFilter completedAt) {
        this.completedAt = completedAt;
    }

    public UUIDFilter getFunctionId() {
        return functionId;
    }

    public Optional<UUIDFilter> optionalFunctionId() {
        return Optional.ofNullable(functionId);
    }

    public UUIDFilter functionId() {
        if (functionId == null) {
            setFunctionId(new UUIDFilter());
        }
        return functionId;
    }

    public void setFunctionId(UUIDFilter functionId) {
        this.functionId = functionId;
    }

    public StringFilter getAssignedWorkerId() {
        return assignedWorkerId;
    }

    public Optional<StringFilter> optionalAssignedWorkerId() {
        return Optional.ofNullable(assignedWorkerId);
    }

    public StringFilter assignedWorkerId() {
        if (assignedWorkerId == null) {
            setAssignedWorkerId(new StringFilter());
        }
        return assignedWorkerId;
    }

    public void setAssignedWorkerId(StringFilter assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ComputeTaskCriteria that = (ComputeTaskCriteria) o;
        return (
            Objects.equals(taskId, that.taskId) &&
            Objects.equals(name, that.name) &&
            Objects.equals(status, that.status) &&
            Objects.equals(priority, that.priority) &&
            Objects.equals(priorityWeight, that.priorityWeight) &&
            Objects.equals(idempotent, that.idempotent) &&
            Objects.equals(entryPoint, that.entryPoint) &&
            Objects.equals(bytecodeSha256, that.bytecodeSha256) &&
            Objects.equals(instructionBudget, that.instructionBudget) &&
            Objects.equals(maxMemoryBytes, that.maxMemoryBytes) &&
            Objects.equals(timeoutMs, that.timeoutMs) &&
            Objects.equals(exitCode, that.exitCode) &&
            Objects.equals(instructionsConsumed, that.instructionsConsumed) &&
            Objects.equals(peakMemoryBytes, that.peakMemoryBytes) &&
            Objects.equals(wallTimeMs, that.wallTimeMs) &&
            Objects.equals(attemptCount, that.attemptCount) &&
            Objects.equals(maxRetries, that.maxRetries) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(startedAt, that.startedAt) &&
            Objects.equals(completedAt, that.completedAt) &&
            Objects.equals(functionId, that.functionId) &&
            Objects.equals(assignedWorkerId, that.assignedWorkerId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            taskId,
            name,
            status,
            priority,
            priorityWeight,
            idempotent,
            entryPoint,
            bytecodeSha256,
            instructionBudget,
            maxMemoryBytes,
            timeoutMs,
            exitCode,
            instructionsConsumed,
            peakMemoryBytes,
            wallTimeMs,
            attemptCount,
            maxRetries,
            createdAt,
            startedAt,
            completedAt,
            functionId,
            assignedWorkerId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ComputeTaskCriteria{" +
            optionalTaskId().map(f -> "taskId=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalPriority().map(f -> "priority=" + f + ", ").orElse("") +
            optionalPriorityWeight().map(f -> "priorityWeight=" + f + ", ").orElse("") +
            optionalIdempotent().map(f -> "idempotent=" + f + ", ").orElse("") +
            optionalEntryPoint().map(f -> "entryPoint=" + f + ", ").orElse("") +
            optionalBytecodeSha256().map(f -> "bytecodeSha256=" + f + ", ").orElse("") +
            optionalInstructionBudget().map(f -> "instructionBudget=" + f + ", ").orElse("") +
            optionalMaxMemoryBytes().map(f -> "maxMemoryBytes=" + f + ", ").orElse("") +
            optionalTimeoutMs().map(f -> "timeoutMs=" + f + ", ").orElse("") +
            optionalExitCode().map(f -> "exitCode=" + f + ", ").orElse("") +
            optionalInstructionsConsumed().map(f -> "instructionsConsumed=" + f + ", ").orElse("") +
            optionalPeakMemoryBytes().map(f -> "peakMemoryBytes=" + f + ", ").orElse("") +
            optionalWallTimeMs().map(f -> "wallTimeMs=" + f + ", ").orElse("") +
            optionalAttemptCount().map(f -> "attemptCount=" + f + ", ").orElse("") +
            optionalMaxRetries().map(f -> "maxRetries=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalStartedAt().map(f -> "startedAt=" + f + ", ").orElse("") +
            optionalCompletedAt().map(f -> "completedAt=" + f + ", ").orElse("") +
            optionalFunctionId().map(f -> "functionId=" + f + ", ").orElse("") +
            optionalAssignedWorkerId().map(f -> "assignedWorkerId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
