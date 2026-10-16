package dev.legatus.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Named, persistent function execution profiles for repeatable dispatch
 * with custom quotas and alerts (Table: execution_profiles).
 */
@Entity
@Table(
    name = "execution_profiles",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_exec_profile_func_name", columnNames = { "function_id", "profile_name" })
    }
)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ExecutionProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 64)
    @Id
    @Column(name = "profile_id", length = 64, nullable = false, unique = true)
    private String profileId;

    @NotNull
    @Size(max = 128)
    @Column(name = "profile_name", length = 128, nullable = false)
    private String profileName;

    @NotNull
    @Column(name = "function_id", nullable = false)
    private UUID functionId;

    @Lob
    @Column(name = "arguments_json")
    private String argumentsJson;

    @Column(name = "instruction_budget")
    private Long instructionBudget;

    @NotNull
    @Column(name = "unlimited_instructions", nullable = false)
    private Boolean unlimitedInstructions = false;

    @Column(name = "time_limit_ms")
    private Long timeLimitMs;

    @NotNull
    @Column(name = "unlimited_time", nullable = false)
    private Boolean unlimitedTime = false;

    @Column(name = "memory_mb")
    private Long memoryMb;

    @Size(max = 32)
    @Column(name = "priority", length = 32)
    private String priority;

    @NotNull
    @Column(name = "notify_on_failure", nullable = false)
    private Boolean notifyOnFailure = false;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public ExecutionProfile() {}

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public ExecutionProfile profileId(String profileId) {
        this.profileId = profileId;
        return this;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public ExecutionProfile profileName(String profileName) {
        this.profileName = profileName;
        return this;
    }

    public UUID getFunctionId() {
        return functionId;
    }

    public void setFunctionId(UUID functionId) {
        this.functionId = functionId;
    }

    public ExecutionProfile functionId(UUID functionId) {
        this.functionId = functionId;
        return this;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public ExecutionProfile argumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
        return this;
    }

    public Long getInstructionBudget() {
        return instructionBudget;
    }

    public void setInstructionBudget(Long instructionBudget) {
        this.instructionBudget = instructionBudget;
    }

    public ExecutionProfile instructionBudget(Long instructionBudget) {
        this.instructionBudget = instructionBudget;
        return this;
    }

    public Boolean getUnlimitedInstructions() {
        return unlimitedInstructions;
    }

    public void setUnlimitedInstructions(Boolean unlimitedInstructions) {
        this.unlimitedInstructions = unlimitedInstructions;
    }

    public ExecutionProfile unlimitedInstructions(Boolean unlimitedInstructions) {
        this.unlimitedInstructions = unlimitedInstructions;
        return this;
    }

    public Long getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(Long timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public ExecutionProfile timeLimitMs(Long timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
        return this;
    }

    public Boolean getUnlimitedTime() {
        return unlimitedTime;
    }

    public void setUnlimitedTime(Boolean unlimitedTime) {
        this.unlimitedTime = unlimitedTime;
    }

    public ExecutionProfile unlimitedTime(Boolean unlimitedTime) {
        this.unlimitedTime = unlimitedTime;
        return this;
    }

    public Long getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(Long memoryMb) {
        this.memoryMb = memoryMb;
    }

    public ExecutionProfile memoryMb(Long memoryMb) {
        this.memoryMb = memoryMb;
        return this;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public ExecutionProfile priority(String priority) {
        this.priority = priority;
        return this;
    }

    public Boolean getNotifyOnFailure() {
        return notifyOnFailure;
    }

    public void setNotifyOnFailure(Boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }

    public ExecutionProfile notifyOnFailure(Boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ExecutionProfile createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ExecutionProfile updatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public ExecutionProfile version(Long version) {
        this.version = version;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExecutionProfile that)) return false;
        return Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(profileId);
    }

    @Override
    public String toString() {
        return "ExecutionProfile{" +
            "profileId='" + profileId + '\'' +
            ", profileName='" + profileName + '\'' +
            ", functionId=" + functionId +
            ", unlimitedInstructions=" + unlimitedInstructions +
            ", unlimitedTime=" + unlimitedTime +
            ", notifyOnFailure=" + notifyOnFailure +
            ", version=" + version +
            '}';
    }
}
