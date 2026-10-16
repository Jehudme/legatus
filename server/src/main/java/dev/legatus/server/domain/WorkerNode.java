package dev.legatus.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.domain.Persistable;

/**
 * Distributed C++20 worker compute nodes, Ed25519 visual attestation identity,
 * and self-declared allocatable hardware quotas (Table: workers)
 */
@Entity
@Table(name = "workers")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonIgnoreProperties(value = { "new", "id" })
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WorkerNode implements Serializable, Persistable<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 64)
    @Id
    @Column(name = "worker_id", length = 64, nullable = false, unique = true)
    private String workerId;

    @Size(max = 64)
    @Column(name = "enrollment_id", length = 64, unique = true)
    private String enrollmentId;

    @NotNull
    @Size(max = 255)
    @Column(name = "hostname", length = 255, nullable = false)
    private String hostname;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "os", nullable = false)
    private OperatingSystem os;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "arch", nullable = false)
    private CpuArchitecture arch;

    @NotNull
    @Size(max = 32)
    @Column(name = "agent_version", length = 32, nullable = false)
    private String agentVersion;

    /**
     * RFC 4648 Base64-encoded 32-byte Ed25519 public key
     */
    @NotNull
    @Size(max = 128)
    @Column(name = "ed_25519_pubkey", length = 128, nullable = false)
    private String ed25519Pubkey;

    /**
     * Uppercase colon-separated SHA-256 fingerprint (XX:XX:XX:XX:XX:XX:XX:XX)
     */
    @NotNull
    @Size(max = 64)
    @Column(name = "fingerprint", length = 64, nullable = false)
    private String fingerprint;

    /**
     * Deterministic 3-word visual confirmation code (e.g., Tiger-Galaxy-77)
     */
    @NotNull
    @Size(max = 64)
    @Column(name = "visual_code", length = 64, nullable = false)
    private String visualCode;

    /**
     * Argon2id hash of the issued 256-bit wk_sec_... token
     */
    @Size(max = 255)
    @Column(name = "secret_hash", length = 255)
    private String secretHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private WorkerState state;

    @Size(max = 64)
    @Column(name = "assigned_cluster", length = 64)
    private String assignedCluster;

    @Size(max = 45)
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @NotNull
    @Min(value = 1)
    @Column(name = "physical_cores", nullable = false)
    private Integer physicalCores;

    @NotNull
    @Min(value = 1)
    @Column(name = "logical_cores", nullable = false)
    private Integer logicalCores;

    @NotNull
    @Min(value = 1L)
    @Max(value = 1099511627776L)
    @Column(name = "total_memory_bytes", nullable = false)
    private Long totalMemoryBytes;

    @NotNull
    @Column(name = "gpu_present", nullable = false)
    private Boolean gpuPresent;

    @Size(max = 128)
    @Column(name = "gpu_model", length = 128)
    private String gpuModel;

    @NotNull
    @Min(value = 1)
    @Column(name = "allocatable_cores", nullable = false)
    private Integer allocatableCores;

    @NotNull
    @Min(value = 1048576L)
    @Max(value = 1099511627776L)
    @Column(name = "max_memory_bytes", nullable = false)
    private Long maxMemoryBytes;

    @NotNull
    @Min(value = 1000L)
    @Max(value = 1000000000L)
    @Column(name = "max_instruction_budget", nullable = false)
    private Long maxInstructionBudget;

    @NotNull
    @Column(name = "draining", nullable = false)
    private Boolean draining;

    @Size(max = 64)
    @Column(name = "active_session_id", length = 64)
    private String activeSessionId;

    @Column(name = "enrollment_expires_at")
    private Instant enrollmentExpiresAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Size(max = 255)
    @Column(name = "friendly_name", length = 255)
    private String friendlyName;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @NotNull
    @Column(name = "target_cpu_pct", nullable = false)
    private Double targetCpuPct = 75.0;

    @NotNull
    @Column(name = "target_ram_pct", nullable = false)
    private Double targetRamPct = 80.0;

    @NotNull
    @Column(name = "max_concurrent_tasks", nullable = false)
    private Integer maxConcurrentTasks = 16;

    @NotNull
    @Column(name = "auto_renew_lease", nullable = false)
    private Boolean autoRenewLease = true;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @NotNull
    @Column(name = "config_version", nullable = false)
    private Long configVersion = 1L;

    @Lob
    @Column(name = "runtime_config_json")
    private String runtimeConfigJson;

    @org.springframework.data.annotation.Transient
    @Transient
    private boolean isPersisted;

    // jhipster-needle-entity-add-field - JHipster will add fields here


    public String getWorkerId() {
        return this.workerId;
    }

    public WorkerNode workerId(String workerId) {
        this.setWorkerId(workerId);
        return this;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getEnrollmentId() {
        return this.enrollmentId;
    }

    public WorkerNode enrollmentId(String enrollmentId) {
        this.setEnrollmentId(enrollmentId);
        return this;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getHostname() {
        return this.hostname;
    }

    public WorkerNode hostname(String hostname) {
        this.setHostname(hostname);
        return this;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public OperatingSystem getOs() {
        return this.os;
    }

    public WorkerNode os(OperatingSystem os) {
        this.setOs(os);
        return this;
    }

    public void setOs(OperatingSystem os) {
        this.os = os;
    }

    public CpuArchitecture getArch() {
        return this.arch;
    }

    public WorkerNode arch(CpuArchitecture arch) {
        this.setArch(arch);
        return this;
    }

    public void setArch(CpuArchitecture arch) {
        this.arch = arch;
    }

    public String getAgentVersion() {
        return this.agentVersion;
    }

    public WorkerNode agentVersion(String agentVersion) {
        this.setAgentVersion(agentVersion);
        return this;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getEd25519Pubkey() {
        return this.ed25519Pubkey;
    }

    public WorkerNode ed25519Pubkey(String ed25519Pubkey) {
        this.setEd25519Pubkey(ed25519Pubkey);
        return this;
    }

    public void setEd25519Pubkey(String ed25519Pubkey) {
        this.ed25519Pubkey = ed25519Pubkey;
    }

    public String getFingerprint() {
        return this.fingerprint;
    }

    public WorkerNode fingerprint(String fingerprint) {
        this.setFingerprint(fingerprint);
        return this;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getVisualCode() {
        return this.visualCode;
    }

    public WorkerNode visualCode(String visualCode) {
        this.setVisualCode(visualCode);
        return this;
    }

    public void setVisualCode(String visualCode) {
        this.visualCode = visualCode;
    }

    public String getSecretHash() {
        return this.secretHash;
    }

    public WorkerNode secretHash(String secretHash) {
        this.setSecretHash(secretHash);
        return this;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public WorkerState getState() {
        return this.state;
    }

    public WorkerNode state(WorkerState state) {
        this.setState(state);
        return this;
    }

    public void setState(WorkerState state) {
        this.state = state;
    }

    public String getAssignedCluster() {
        return this.assignedCluster;
    }

    public WorkerNode assignedCluster(String assignedCluster) {
        this.setAssignedCluster(assignedCluster);
        return this;
    }

    public void setAssignedCluster(String assignedCluster) {
        this.assignedCluster = assignedCluster;
    }

    public String getSourceIp() {
        return this.sourceIp;
    }

    public WorkerNode sourceIp(String sourceIp) {
        this.setSourceIp(sourceIp);
        return this;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public Integer getPhysicalCores() {
        return this.physicalCores;
    }

    public WorkerNode physicalCores(Integer physicalCores) {
        this.setPhysicalCores(physicalCores);
        return this;
    }

    public void setPhysicalCores(Integer physicalCores) {
        this.physicalCores = physicalCores;
    }

    public Integer getLogicalCores() {
        return this.logicalCores;
    }

    public WorkerNode logicalCores(Integer logicalCores) {
        this.setLogicalCores(logicalCores);
        return this;
    }

    public void setLogicalCores(Integer logicalCores) {
        this.logicalCores = logicalCores;
    }

    public Long getTotalMemoryBytes() {
        return this.totalMemoryBytes;
    }

    public WorkerNode totalMemoryBytes(Long totalMemoryBytes) {
        this.setTotalMemoryBytes(totalMemoryBytes);
        return this;
    }

    public void setTotalMemoryBytes(Long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public Boolean getGpuPresent() {
        return this.gpuPresent;
    }

    public WorkerNode gpuPresent(Boolean gpuPresent) {
        this.setGpuPresent(gpuPresent);
        return this;
    }

    public void setGpuPresent(Boolean gpuPresent) {
        this.gpuPresent = gpuPresent;
    }

    public String getGpuModel() {
        return this.gpuModel;
    }

    public WorkerNode gpuModel(String gpuModel) {
        this.setGpuModel(gpuModel);
        return this;
    }

    public void setGpuModel(String gpuModel) {
        this.gpuModel = gpuModel;
    }

    public Integer getAllocatableCores() {
        return this.allocatableCores;
    }

    public WorkerNode allocatableCores(Integer allocatableCores) {
        this.setAllocatableCores(allocatableCores);
        return this;
    }

    public void setAllocatableCores(Integer allocatableCores) {
        this.allocatableCores = allocatableCores;
    }

    public Long getMaxMemoryBytes() {
        return this.maxMemoryBytes;
    }

    public WorkerNode maxMemoryBytes(Long maxMemoryBytes) {
        this.setMaxMemoryBytes(maxMemoryBytes);
        return this;
    }

    public void setMaxMemoryBytes(Long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public Long getMaxInstructionBudget() {
        return this.maxInstructionBudget;
    }

    public WorkerNode maxInstructionBudget(Long maxInstructionBudget) {
        this.setMaxInstructionBudget(maxInstructionBudget);
        return this;
    }

    public void setMaxInstructionBudget(Long maxInstructionBudget) {
        this.maxInstructionBudget = maxInstructionBudget;
    }

    public Boolean getDraining() {
        return this.draining;
    }

    public WorkerNode draining(Boolean draining) {
        this.setDraining(draining);
        return this;
    }

    public void setDraining(Boolean draining) {
        this.draining = draining;
    }

    public String getActiveSessionId() {
        return this.activeSessionId;
    }

    public WorkerNode activeSessionId(String activeSessionId) {
        this.setActiveSessionId(activeSessionId);
        return this;
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public Instant getEnrollmentExpiresAt() {
        return this.enrollmentExpiresAt;
    }

    public WorkerNode enrollmentExpiresAt(Instant enrollmentExpiresAt) {
        this.setEnrollmentExpiresAt(enrollmentExpiresAt);
        return this;
    }

    public void setEnrollmentExpiresAt(Instant enrollmentExpiresAt) {
        this.enrollmentExpiresAt = enrollmentExpiresAt;
    }

    public Instant getLastSeenAt() {
        return this.lastSeenAt;
    }

    public WorkerNode lastSeenAt(Instant lastSeenAt) {
        this.setLastSeenAt(lastSeenAt);
        return this;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public WorkerNode createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getFriendlyName() {
        return this.friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public WorkerNode friendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
        return this;
    }

    public Boolean getActive() {
        return this.active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public WorkerNode active(Boolean active) {
        this.active = active;
        return this;
    }

    public Double getTargetCpuPct() {
        return this.targetCpuPct;
    }

    public void setTargetCpuPct(Double targetCpuPct) {
        this.targetCpuPct = targetCpuPct;
    }

    public WorkerNode targetCpuPct(Double targetCpuPct) {
        this.targetCpuPct = targetCpuPct;
        return this;
    }

    public Double getTargetRamPct() {
        return this.targetRamPct;
    }

    public void setTargetRamPct(Double targetRamPct) {
        this.targetRamPct = targetRamPct;
    }

    public WorkerNode targetRamPct(Double targetRamPct) {
        this.targetRamPct = targetRamPct;
        return this;
    }

    public Integer getMaxConcurrentTasks() {
        return this.maxConcurrentTasks;
    }

    public void setMaxConcurrentTasks(Integer maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public WorkerNode maxConcurrentTasks(Integer maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
        return this;
    }

    public Boolean getAutoRenewLease() {
        return this.autoRenewLease;
    }

    public void setAutoRenewLease(Boolean autoRenewLease) {
        this.autoRenewLease = autoRenewLease;
    }

    public WorkerNode autoRenewLease(Boolean autoRenewLease) {
        this.autoRenewLease = autoRenewLease;
        return this;
    }

    public Instant getLeaseExpiresAt() {
        return this.leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public WorkerNode leaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
        return this;
    }

    public Long getConfigVersion() {
        return this.configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public WorkerNode configVersion(Long configVersion) {
        this.configVersion = configVersion;
        return this;
    }

    public String getRuntimeConfigJson() {
        return this.runtimeConfigJson;
    }

    public void setRuntimeConfigJson(String runtimeConfigJson) {
        this.runtimeConfigJson = runtimeConfigJson;
    }

    public WorkerNode runtimeConfigJson(String runtimeConfigJson) {
        this.runtimeConfigJson = runtimeConfigJson;
        return this;
    }


    @PostLoad
    @PostPersist
    public void updateEntityState() {
        this.setIsPersisted();
    }

    @Override
    public String getId() {
        return this.workerId;
    }

    @org.springframework.data.annotation.Transient
    @Transient
    @Override
    public boolean isNew() {
        return !this.isPersisted;
    }

    public WorkerNode setIsPersisted() {
        this.isPersisted = true;
        return this;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkerNode)) {
            return false;
        }
        return getWorkerId() != null && getWorkerId().equals(((WorkerNode) o).getWorkerId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WorkerNode{" +
            "workerId=" + getWorkerId() +
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
