package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A DTO representing an {@link dev.legatus.server.domain.ExecutionProfile}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExecutionProfileDTO implements Serializable {

    private String profileId;

    @NotNull
    @Size(min = 1, max = 128)
    private String profileName;

    @NotNull
    private UUID functionId;

    private String argumentsJson;

    private Long instructionBudget;

    private Boolean unlimitedInstructions = false;

    private Long timeLimitMs;

    private Boolean unlimitedTime = false;

    private Long memoryMb;

    @Size(max = 32)
    private String priority;

    private Boolean notifyOnFailure = false;

    private Instant createdAt;

    private Instant updatedAt;

    private Long version = 0L;

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public UUID getFunctionId() {
        return functionId;
    }

    public void setFunctionId(UUID functionId) {
        this.functionId = functionId;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public Long getInstructionBudget() {
        return instructionBudget;
    }

    public void setInstructionBudget(Long instructionBudget) {
        this.instructionBudget = instructionBudget;
    }

    public Boolean getUnlimitedInstructions() {
        return unlimitedInstructions;
    }

    public void setUnlimitedInstructions(Boolean unlimitedInstructions) {
        this.unlimitedInstructions = unlimitedInstructions;
    }

    public Long getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(Long timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public Boolean getUnlimitedTime() {
        return unlimitedTime;
    }

    public void setUnlimitedTime(Boolean unlimitedTime) {
        this.unlimitedTime = unlimitedTime;
    }

    public Long getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(Long memoryMb) {
        this.memoryMb = memoryMb;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Boolean getNotifyOnFailure() {
        return notifyOnFailure;
    }

    public void setNotifyOnFailure(Boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExecutionProfileDTO that)) return false;
        return Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(profileId);
    }

    @Override
    public String toString() {
        return "ExecutionProfileDTO{" +
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
