package dev.legatus.server.service.criteria;

import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link dev.legatus.server.domain.WorkerNode} entity. This class is used
 * in {@link dev.legatus.server.web.rest.WorkerNodeResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /worker-nodes?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkerNodeCriteria implements Serializable, Criteria {

    /**
     * Class for filtering OperatingSystem
     */
    public static class OperatingSystemFilter extends Filter<OperatingSystem> {

        public OperatingSystemFilter() {}

        public OperatingSystemFilter(OperatingSystemFilter filter) {
            super(filter);
        }

        @Override
        public OperatingSystemFilter copy() {
            return new OperatingSystemFilter(this);
        }
    }

    /**
     * Class for filtering CpuArchitecture
     */
    public static class CpuArchitectureFilter extends Filter<CpuArchitecture> {

        public CpuArchitectureFilter() {}

        public CpuArchitectureFilter(CpuArchitectureFilter filter) {
            super(filter);
        }

        @Override
        public CpuArchitectureFilter copy() {
            return new CpuArchitectureFilter(this);
        }
    }

    /**
     * Class for filtering WorkerState
     */
    public static class WorkerStateFilter extends Filter<WorkerState> {

        public WorkerStateFilter() {}

        public WorkerStateFilter(WorkerStateFilter filter) {
            super(filter);
        }

        @Override
        public WorkerStateFilter copy() {
            return new WorkerStateFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private StringFilter workerId;

    private StringFilter enrollmentId;

    private StringFilter hostname;

    private OperatingSystemFilter os;

    private CpuArchitectureFilter arch;

    private StringFilter agentVersion;

    private StringFilter ed25519Pubkey;

    private StringFilter fingerprint;

    private StringFilter visualCode;

    private StringFilter secretHash;

    private WorkerStateFilter state;

    private StringFilter assignedCluster;

    private StringFilter sourceIp;

    private IntegerFilter physicalCores;

    private IntegerFilter logicalCores;

    private LongFilter totalMemoryBytes;

    private BooleanFilter gpuPresent;

    private StringFilter gpuModel;

    private IntegerFilter allocatableCores;

    private LongFilter maxMemoryBytes;

    private LongFilter maxInstructionBudget;

    private BooleanFilter draining;

    private StringFilter activeSessionId;

    private InstantFilter enrollmentExpiresAt;

    private InstantFilter lastSeenAt;

    private InstantFilter createdAt;

    private Boolean distinct;

    public WorkerNodeCriteria() {}

    public WorkerNodeCriteria(WorkerNodeCriteria other) {
        this.workerId = other.optionalWorkerId().map(StringFilter::copy).orElse(null);
        this.enrollmentId = other.optionalEnrollmentId().map(StringFilter::copy).orElse(null);
        this.hostname = other.optionalHostname().map(StringFilter::copy).orElse(null);
        this.os = other.optionalOs().map(OperatingSystemFilter::copy).orElse(null);
        this.arch = other.optionalArch().map(CpuArchitectureFilter::copy).orElse(null);
        this.agentVersion = other.optionalAgentVersion().map(StringFilter::copy).orElse(null);
        this.ed25519Pubkey = other.optionalEd25519Pubkey().map(StringFilter::copy).orElse(null);
        this.fingerprint = other.optionalFingerprint().map(StringFilter::copy).orElse(null);
        this.visualCode = other.optionalVisualCode().map(StringFilter::copy).orElse(null);
        this.secretHash = other.optionalSecretHash().map(StringFilter::copy).orElse(null);
        this.state = other.optionalState().map(WorkerStateFilter::copy).orElse(null);
        this.assignedCluster = other.optionalAssignedCluster().map(StringFilter::copy).orElse(null);
        this.sourceIp = other.optionalSourceIp().map(StringFilter::copy).orElse(null);
        this.physicalCores = other.optionalPhysicalCores().map(IntegerFilter::copy).orElse(null);
        this.logicalCores = other.optionalLogicalCores().map(IntegerFilter::copy).orElse(null);
        this.totalMemoryBytes = other.optionalTotalMemoryBytes().map(LongFilter::copy).orElse(null);
        this.gpuPresent = other.optionalGpuPresent().map(BooleanFilter::copy).orElse(null);
        this.gpuModel = other.optionalGpuModel().map(StringFilter::copy).orElse(null);
        this.allocatableCores = other.optionalAllocatableCores().map(IntegerFilter::copy).orElse(null);
        this.maxMemoryBytes = other.optionalMaxMemoryBytes().map(LongFilter::copy).orElse(null);
        this.maxInstructionBudget = other.optionalMaxInstructionBudget().map(LongFilter::copy).orElse(null);
        this.draining = other.optionalDraining().map(BooleanFilter::copy).orElse(null);
        this.activeSessionId = other.optionalActiveSessionId().map(StringFilter::copy).orElse(null);
        this.enrollmentExpiresAt = other.optionalEnrollmentExpiresAt().map(InstantFilter::copy).orElse(null);
        this.lastSeenAt = other.optionalLastSeenAt().map(InstantFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public WorkerNodeCriteria copy() {
        return new WorkerNodeCriteria(this);
    }

    public StringFilter getWorkerId() {
        return workerId;
    }

    public Optional<StringFilter> optionalWorkerId() {
        return Optional.ofNullable(workerId);
    }

    public StringFilter workerId() {
        if (workerId == null) {
            setWorkerId(new StringFilter());
        }
        return workerId;
    }

    public void setWorkerId(StringFilter workerId) {
        this.workerId = workerId;
    }

    public StringFilter getEnrollmentId() {
        return enrollmentId;
    }

    public Optional<StringFilter> optionalEnrollmentId() {
        return Optional.ofNullable(enrollmentId);
    }

    public StringFilter enrollmentId() {
        if (enrollmentId == null) {
            setEnrollmentId(new StringFilter());
        }
        return enrollmentId;
    }

    public void setEnrollmentId(StringFilter enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public StringFilter getHostname() {
        return hostname;
    }

    public Optional<StringFilter> optionalHostname() {
        return Optional.ofNullable(hostname);
    }

    public StringFilter hostname() {
        if (hostname == null) {
            setHostname(new StringFilter());
        }
        return hostname;
    }

    public void setHostname(StringFilter hostname) {
        this.hostname = hostname;
    }

    public OperatingSystemFilter getOs() {
        return os;
    }

    public Optional<OperatingSystemFilter> optionalOs() {
        return Optional.ofNullable(os);
    }

    public OperatingSystemFilter os() {
        if (os == null) {
            setOs(new OperatingSystemFilter());
        }
        return os;
    }

    public void setOs(OperatingSystemFilter os) {
        this.os = os;
    }

    public CpuArchitectureFilter getArch() {
        return arch;
    }

    public Optional<CpuArchitectureFilter> optionalArch() {
        return Optional.ofNullable(arch);
    }

    public CpuArchitectureFilter arch() {
        if (arch == null) {
            setArch(new CpuArchitectureFilter());
        }
        return arch;
    }

    public void setArch(CpuArchitectureFilter arch) {
        this.arch = arch;
    }

    public StringFilter getAgentVersion() {
        return agentVersion;
    }

    public Optional<StringFilter> optionalAgentVersion() {
        return Optional.ofNullable(agentVersion);
    }

    public StringFilter agentVersion() {
        if (agentVersion == null) {
            setAgentVersion(new StringFilter());
        }
        return agentVersion;
    }

    public void setAgentVersion(StringFilter agentVersion) {
        this.agentVersion = agentVersion;
    }

    public StringFilter getEd25519Pubkey() {
        return ed25519Pubkey;
    }

    public Optional<StringFilter> optionalEd25519Pubkey() {
        return Optional.ofNullable(ed25519Pubkey);
    }

    public StringFilter ed25519Pubkey() {
        if (ed25519Pubkey == null) {
            setEd25519Pubkey(new StringFilter());
        }
        return ed25519Pubkey;
    }

    public void setEd25519Pubkey(StringFilter ed25519Pubkey) {
        this.ed25519Pubkey = ed25519Pubkey;
    }

    public StringFilter getFingerprint() {
        return fingerprint;
    }

    public Optional<StringFilter> optionalFingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public StringFilter fingerprint() {
        if (fingerprint == null) {
            setFingerprint(new StringFilter());
        }
        return fingerprint;
    }

    public void setFingerprint(StringFilter fingerprint) {
        this.fingerprint = fingerprint;
    }

    public StringFilter getVisualCode() {
        return visualCode;
    }

    public Optional<StringFilter> optionalVisualCode() {
        return Optional.ofNullable(visualCode);
    }

    public StringFilter visualCode() {
        if (visualCode == null) {
            setVisualCode(new StringFilter());
        }
        return visualCode;
    }

    public void setVisualCode(StringFilter visualCode) {
        this.visualCode = visualCode;
    }

    public StringFilter getSecretHash() {
        return secretHash;
    }

    public Optional<StringFilter> optionalSecretHash() {
        return Optional.ofNullable(secretHash);
    }

    public StringFilter secretHash() {
        if (secretHash == null) {
            setSecretHash(new StringFilter());
        }
        return secretHash;
    }

    public void setSecretHash(StringFilter secretHash) {
        this.secretHash = secretHash;
    }

    public WorkerStateFilter getState() {
        return state;
    }

    public Optional<WorkerStateFilter> optionalState() {
        return Optional.ofNullable(state);
    }

    public WorkerStateFilter state() {
        if (state == null) {
            setState(new WorkerStateFilter());
        }
        return state;
    }

    public void setState(WorkerStateFilter state) {
        this.state = state;
    }

    public StringFilter getAssignedCluster() {
        return assignedCluster;
    }

    public Optional<StringFilter> optionalAssignedCluster() {
        return Optional.ofNullable(assignedCluster);
    }

    public StringFilter assignedCluster() {
        if (assignedCluster == null) {
            setAssignedCluster(new StringFilter());
        }
        return assignedCluster;
    }

    public void setAssignedCluster(StringFilter assignedCluster) {
        this.assignedCluster = assignedCluster;
    }

    public StringFilter getSourceIp() {
        return sourceIp;
    }

    public Optional<StringFilter> optionalSourceIp() {
        return Optional.ofNullable(sourceIp);
    }

    public StringFilter sourceIp() {
        if (sourceIp == null) {
            setSourceIp(new StringFilter());
        }
        return sourceIp;
    }

    public void setSourceIp(StringFilter sourceIp) {
        this.sourceIp = sourceIp;
    }

    public IntegerFilter getPhysicalCores() {
        return physicalCores;
    }

    public Optional<IntegerFilter> optionalPhysicalCores() {
        return Optional.ofNullable(physicalCores);
    }

    public IntegerFilter physicalCores() {
        if (physicalCores == null) {
            setPhysicalCores(new IntegerFilter());
        }
        return physicalCores;
    }

    public void setPhysicalCores(IntegerFilter physicalCores) {
        this.physicalCores = physicalCores;
    }

    public IntegerFilter getLogicalCores() {
        return logicalCores;
    }

    public Optional<IntegerFilter> optionalLogicalCores() {
        return Optional.ofNullable(logicalCores);
    }

    public IntegerFilter logicalCores() {
        if (logicalCores == null) {
            setLogicalCores(new IntegerFilter());
        }
        return logicalCores;
    }

    public void setLogicalCores(IntegerFilter logicalCores) {
        this.logicalCores = logicalCores;
    }

    public LongFilter getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public Optional<LongFilter> optionalTotalMemoryBytes() {
        return Optional.ofNullable(totalMemoryBytes);
    }

    public LongFilter totalMemoryBytes() {
        if (totalMemoryBytes == null) {
            setTotalMemoryBytes(new LongFilter());
        }
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(LongFilter totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public BooleanFilter getGpuPresent() {
        return gpuPresent;
    }

    public Optional<BooleanFilter> optionalGpuPresent() {
        return Optional.ofNullable(gpuPresent);
    }

    public BooleanFilter gpuPresent() {
        if (gpuPresent == null) {
            setGpuPresent(new BooleanFilter());
        }
        return gpuPresent;
    }

    public void setGpuPresent(BooleanFilter gpuPresent) {
        this.gpuPresent = gpuPresent;
    }

    public StringFilter getGpuModel() {
        return gpuModel;
    }

    public Optional<StringFilter> optionalGpuModel() {
        return Optional.ofNullable(gpuModel);
    }

    public StringFilter gpuModel() {
        if (gpuModel == null) {
            setGpuModel(new StringFilter());
        }
        return gpuModel;
    }

    public void setGpuModel(StringFilter gpuModel) {
        this.gpuModel = gpuModel;
    }

    public IntegerFilter getAllocatableCores() {
        return allocatableCores;
    }

    public Optional<IntegerFilter> optionalAllocatableCores() {
        return Optional.ofNullable(allocatableCores);
    }

    public IntegerFilter allocatableCores() {
        if (allocatableCores == null) {
            setAllocatableCores(new IntegerFilter());
        }
        return allocatableCores;
    }

    public void setAllocatableCores(IntegerFilter allocatableCores) {
        this.allocatableCores = allocatableCores;
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

    public LongFilter getMaxInstructionBudget() {
        return maxInstructionBudget;
    }

    public Optional<LongFilter> optionalMaxInstructionBudget() {
        return Optional.ofNullable(maxInstructionBudget);
    }

    public LongFilter maxInstructionBudget() {
        if (maxInstructionBudget == null) {
            setMaxInstructionBudget(new LongFilter());
        }
        return maxInstructionBudget;
    }

    public void setMaxInstructionBudget(LongFilter maxInstructionBudget) {
        this.maxInstructionBudget = maxInstructionBudget;
    }

    public BooleanFilter getDraining() {
        return draining;
    }

    public Optional<BooleanFilter> optionalDraining() {
        return Optional.ofNullable(draining);
    }

    public BooleanFilter draining() {
        if (draining == null) {
            setDraining(new BooleanFilter());
        }
        return draining;
    }

    public void setDraining(BooleanFilter draining) {
        this.draining = draining;
    }

    public StringFilter getActiveSessionId() {
        return activeSessionId;
    }

    public Optional<StringFilter> optionalActiveSessionId() {
        return Optional.ofNullable(activeSessionId);
    }

    public StringFilter activeSessionId() {
        if (activeSessionId == null) {
            setActiveSessionId(new StringFilter());
        }
        return activeSessionId;
    }

    public void setActiveSessionId(StringFilter activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public InstantFilter getEnrollmentExpiresAt() {
        return enrollmentExpiresAt;
    }

    public Optional<InstantFilter> optionalEnrollmentExpiresAt() {
        return Optional.ofNullable(enrollmentExpiresAt);
    }

    public InstantFilter enrollmentExpiresAt() {
        if (enrollmentExpiresAt == null) {
            setEnrollmentExpiresAt(new InstantFilter());
        }
        return enrollmentExpiresAt;
    }

    public void setEnrollmentExpiresAt(InstantFilter enrollmentExpiresAt) {
        this.enrollmentExpiresAt = enrollmentExpiresAt;
    }

    public InstantFilter getLastSeenAt() {
        return lastSeenAt;
    }

    public Optional<InstantFilter> optionalLastSeenAt() {
        return Optional.ofNullable(lastSeenAt);
    }

    public InstantFilter lastSeenAt() {
        if (lastSeenAt == null) {
            setLastSeenAt(new InstantFilter());
        }
        return lastSeenAt;
    }

    public void setLastSeenAt(InstantFilter lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
        final WorkerNodeCriteria that = (WorkerNodeCriteria) o;
        return (
            Objects.equals(workerId, that.workerId) &&
            Objects.equals(enrollmentId, that.enrollmentId) &&
            Objects.equals(hostname, that.hostname) &&
            Objects.equals(os, that.os) &&
            Objects.equals(arch, that.arch) &&
            Objects.equals(agentVersion, that.agentVersion) &&
            Objects.equals(ed25519Pubkey, that.ed25519Pubkey) &&
            Objects.equals(fingerprint, that.fingerprint) &&
            Objects.equals(visualCode, that.visualCode) &&
            Objects.equals(secretHash, that.secretHash) &&
            Objects.equals(state, that.state) &&
            Objects.equals(assignedCluster, that.assignedCluster) &&
            Objects.equals(sourceIp, that.sourceIp) &&
            Objects.equals(physicalCores, that.physicalCores) &&
            Objects.equals(logicalCores, that.logicalCores) &&
            Objects.equals(totalMemoryBytes, that.totalMemoryBytes) &&
            Objects.equals(gpuPresent, that.gpuPresent) &&
            Objects.equals(gpuModel, that.gpuModel) &&
            Objects.equals(allocatableCores, that.allocatableCores) &&
            Objects.equals(maxMemoryBytes, that.maxMemoryBytes) &&
            Objects.equals(maxInstructionBudget, that.maxInstructionBudget) &&
            Objects.equals(draining, that.draining) &&
            Objects.equals(activeSessionId, that.activeSessionId) &&
            Objects.equals(enrollmentExpiresAt, that.enrollmentExpiresAt) &&
            Objects.equals(lastSeenAt, that.lastSeenAt) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            workerId,
            enrollmentId,
            hostname,
            os,
            arch,
            agentVersion,
            ed25519Pubkey,
            fingerprint,
            visualCode,
            secretHash,
            state,
            assignedCluster,
            sourceIp,
            physicalCores,
            logicalCores,
            totalMemoryBytes,
            gpuPresent,
            gpuModel,
            allocatableCores,
            maxMemoryBytes,
            maxInstructionBudget,
            draining,
            activeSessionId,
            enrollmentExpiresAt,
            lastSeenAt,
            createdAt,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkerNodeCriteria{" +
            optionalWorkerId().map(f -> "workerId=" + f + ", ").orElse("") +
            optionalEnrollmentId().map(f -> "enrollmentId=" + f + ", ").orElse("") +
            optionalHostname().map(f -> "hostname=" + f + ", ").orElse("") +
            optionalOs().map(f -> "os=" + f + ", ").orElse("") +
            optionalArch().map(f -> "arch=" + f + ", ").orElse("") +
            optionalAgentVersion().map(f -> "agentVersion=" + f + ", ").orElse("") +
            optionalEd25519Pubkey().map(f -> "ed25519Pubkey=" + f + ", ").orElse("") +
            optionalFingerprint().map(f -> "fingerprint=" + f + ", ").orElse("") +
            optionalVisualCode().map(f -> "visualCode=" + f + ", ").orElse("") +
            optionalSecretHash().map(f -> "secretHash=" + f + ", ").orElse("") +
            optionalState().map(f -> "state=" + f + ", ").orElse("") +
            optionalAssignedCluster().map(f -> "assignedCluster=" + f + ", ").orElse("") +
            optionalSourceIp().map(f -> "sourceIp=" + f + ", ").orElse("") +
            optionalPhysicalCores().map(f -> "physicalCores=" + f + ", ").orElse("") +
            optionalLogicalCores().map(f -> "logicalCores=" + f + ", ").orElse("") +
            optionalTotalMemoryBytes().map(f -> "totalMemoryBytes=" + f + ", ").orElse("") +
            optionalGpuPresent().map(f -> "gpuPresent=" + f + ", ").orElse("") +
            optionalGpuModel().map(f -> "gpuModel=" + f + ", ").orElse("") +
            optionalAllocatableCores().map(f -> "allocatableCores=" + f + ", ").orElse("") +
            optionalMaxMemoryBytes().map(f -> "maxMemoryBytes=" + f + ", ").orElse("") +
            optionalMaxInstructionBudget().map(f -> "maxInstructionBudget=" + f + ", ").orElse("") +
            optionalDraining().map(f -> "draining=" + f + ", ").orElse("") +
            optionalActiveSessionId().map(f -> "activeSessionId=" + f + ", ").orElse("") +
            optionalEnrollmentExpiresAt().map(f -> "enrollmentExpiresAt=" + f + ", ").orElse("") +
            optionalLastSeenAt().map(f -> "lastSeenAt=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
