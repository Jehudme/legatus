package dev.legatus.server.service.dto;

import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link dev.legatus.server.domain.WorkerNode} entity.
 */
@Schema(
    description = "Distributed C++20 worker compute nodes, Ed25519 visual attestation identity,\nand self-declared allocatable hardware quotas (Table: workers)"
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkerNodeDTO implements Serializable {

    @NotNull
    @Size(max = 64)
    private String workerId;

    @Size(max = 64)
    private String enrollmentId;

    @NotNull
    @Size(max = 255)
    private String hostname;

    @NotNull
    private OperatingSystem os;

    @NotNull
    private CpuArchitecture arch;

    @NotNull
    @Size(max = 32)
    private String agentVersion;

    @NotNull
    @Size(max = 128)
    @Schema(description = "RFC 4648 Base64-encoded 32-byte Ed25519 public key", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ed25519Pubkey;

    @NotNull
    @Size(max = 64)
    @Schema(
        description = "Uppercase colon-separated SHA-256 fingerprint (XX:XX:XX:XX:XX:XX:XX:XX)",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String fingerprint;

    @NotNull
    @Size(max = 64)
    @Schema(
        description = "Deterministic 3-word visual confirmation code (e.g., Tiger-Galaxy-77)",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String visualCode;

    @Size(max = 255)
    @Schema(description = "Argon2id hash of the issued 256-bit wk_sec_... token")
    private String secretHash;

    @NotNull
    private WorkerState state;

    @Size(max = 64)
    private String assignedCluster;

    @Size(max = 45)
    private String sourceIp;

    @NotNull
    @Min(value = 1)
    private Integer physicalCores;

    @NotNull
    @Min(value = 1)
    private Integer logicalCores;

    @NotNull
    @Min(value = 1L)
    @Max(value = 1099511627776L)
    private Long totalMemoryBytes;

    @NotNull
    private Boolean gpuPresent;

    @Size(max = 128)
    private String gpuModel;

    @NotNull
    @Min(value = 1)
    private Integer allocatableCores;

    @NotNull
    @Min(value = 1048576L)
    @Max(value = 1099511627776L)
    private Long maxMemoryBytes;

    @NotNull
    @Min(value = 1000L)
    @Max(value = 1000000000L)
    private Long maxInstructionBudget;

    @NotNull
    private Boolean draining;

    @Size(max = 64)
    private String activeSessionId;

    private Instant enrollmentExpiresAt;

    private Instant lastSeenAt;

    @NotNull
    private Instant createdAt;

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public OperatingSystem getOs() {
        return os;
    }

    public void setOs(OperatingSystem os) {
        this.os = os;
    }

    public CpuArchitecture getArch() {
        return arch;
    }

    public void setArch(CpuArchitecture arch) {
        this.arch = arch;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getEd25519Pubkey() {
        return ed25519Pubkey;
    }

    public void setEd25519Pubkey(String ed25519Pubkey) {
        this.ed25519Pubkey = ed25519Pubkey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getVisualCode() {
        return visualCode;
    }

    public void setVisualCode(String visualCode) {
        this.visualCode = visualCode;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public WorkerState getState() {
        return state;
    }

    public void setState(WorkerState state) {
        this.state = state;
    }

    public String getAssignedCluster() {
        return assignedCluster;
    }

    public void setAssignedCluster(String assignedCluster) {
        this.assignedCluster = assignedCluster;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public Integer getPhysicalCores() {
        return physicalCores;
    }

    public void setPhysicalCores(Integer physicalCores) {
        this.physicalCores = physicalCores;
    }

    public Integer getLogicalCores() {
        return logicalCores;
    }

    public void setLogicalCores(Integer logicalCores) {
        this.logicalCores = logicalCores;
    }

    public Long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(Long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public Boolean getGpuPresent() {
        return gpuPresent;
    }

    public void setGpuPresent(Boolean gpuPresent) {
        this.gpuPresent = gpuPresent;
    }

    public String getGpuModel() {
        return gpuModel;
    }

    public void setGpuModel(String gpuModel) {
        this.gpuModel = gpuModel;
    }

    public Integer getAllocatableCores() {
        return allocatableCores;
    }

    public void setAllocatableCores(Integer allocatableCores) {
        this.allocatableCores = allocatableCores;
    }

    public Long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(Long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public Long getMaxInstructionBudget() {
        return maxInstructionBudget;
    }

    public void setMaxInstructionBudget(Long maxInstructionBudget) {
        this.maxInstructionBudget = maxInstructionBudget;
    }

    public Boolean getDraining() {
        return draining;
    }

    public void setDraining(Boolean draining) {
        this.draining = draining;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public Instant getEnrollmentExpiresAt() {
        return enrollmentExpiresAt;
    }

    public void setEnrollmentExpiresAt(Instant enrollmentExpiresAt) {
        this.enrollmentExpiresAt = enrollmentExpiresAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkerNodeDTO)) {
            return false;
        }

        WorkerNodeDTO workerNodeDTO = (WorkerNodeDTO) o;
        if (this.workerId == null) {
            return false;
        }
        return Objects.equals(this.workerId, workerNodeDTO.workerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.workerId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkerNodeDTO{" +
            "workerId='" + getWorkerId() + "'" +
            ", enrollmentId='" + getEnrollmentId() + "'" +
            ", hostname='" + getHostname() + "'" +
            ", os='" + getOs() + "'" +
            ", arch='" + getArch() + "'" +
            ", agentVersion='" + getAgentVersion() + "'" +
            ", ed25519Pubkey='" + getEd25519Pubkey() + "'" +
            ", fingerprint='" + getFingerprint() + "'" +
            ", visualCode='" + getVisualCode() + "'" +
            ", secretHash='" + getSecretHash() + "'" +
            ", state='" + getState() + "'" +
            ", assignedCluster='" + getAssignedCluster() + "'" +
            ", sourceIp='" + getSourceIp() + "'" +
            ", physicalCores=" + getPhysicalCores() +
            ", logicalCores=" + getLogicalCores() +
            ", totalMemoryBytes=" + getTotalMemoryBytes() +
            ", gpuPresent='" + getGpuPresent() + "'" +
            ", gpuModel='" + getGpuModel() + "'" +
            ", allocatableCores=" + getAllocatableCores() +
            ", maxMemoryBytes=" + getMaxMemoryBytes() +
            ", maxInstructionBudget=" + getMaxInstructionBudget() +
            ", draining='" + getDraining() + "'" +
            ", activeSessionId='" + getActiveSessionId() + "'" +
            ", enrollmentExpiresAt='" + getEnrollmentExpiresAt() + "'" +
            ", lastSeenAt='" + getLastSeenAt() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
