package com.legatus.studio.model;

import com.legatus.studio.protocol.RestDto;
import javafx.beans.property.*;

import java.util.UUID;

/**
 * JavaFX Observable Model for an Execution Profile (MS-009).
 */
public class ExecutionProfile {

    private final StringProperty profileId = new SimpleStringProperty("");
    private final StringProperty profileName = new SimpleStringProperty("");
    private final ObjectProperty<UUID> functionId = new SimpleObjectProperty<>();
    private final StringProperty functionName = new SimpleStringProperty("");
    private final StringProperty argumentsJson = new SimpleStringProperty("{}");
    private final LongProperty instructionBudget = new SimpleLongProperty(10_000_000L);
    private final BooleanProperty unlimitedInstructions = new SimpleBooleanProperty(false);
    private final LongProperty timeLimitMs = new SimpleLongProperty(60_000L);
    private final BooleanProperty unlimitedTime = new SimpleBooleanProperty(false);
    private final LongProperty memoryMb = new SimpleLongProperty(64L);
    private final StringProperty priority = new SimpleStringProperty("NORMAL");
    private final BooleanProperty notifyOnFailure = new SimpleBooleanProperty(false);
    private final LongProperty version = new SimpleLongProperty(0L);

    public ExecutionProfile() {}

    public ExecutionProfile(String profileId, String profileName, UUID functionId) {
        setProfileId(profileId);
        setProfileName(profileName);
        setFunctionId(functionId);
    }

    public static ExecutionProfile fromDto(RestDto.ExecutionProfileDto dto) {
        ExecutionProfile p = new ExecutionProfile();
        if (dto != null) {
            p.setProfileId(dto.profile_id() != null ? dto.profile_id() : "");
            p.setProfileName(dto.profile_name() != null ? dto.profile_name() : "");
            p.setFunctionId(dto.function_id());
            p.setArgumentsJson(dto.arguments_json() != null ? dto.arguments_json() : "{}");
            p.setInstructionBudget(dto.instruction_budget() != null ? dto.instruction_budget() : 10_000_000L);
            p.setUnlimitedInstructions(Boolean.TRUE.equals(dto.unlimited_instructions()));
            p.setTimeLimitMs(dto.time_limit_ms() != null ? dto.time_limit_ms() : 60_000L);
            p.setUnlimitedTime(Boolean.TRUE.equals(dto.unlimited_time()));
            p.setMemoryMb(dto.memory_mb() != null ? dto.memory_mb() : 64L);
            p.setPriority(dto.priority() != null ? dto.priority() : "NORMAL");
            p.setNotifyOnFailure(Boolean.TRUE.equals(dto.notify_on_failure()));
            p.setVersion(dto.version() != null ? dto.version() : 0L);
        }
        return p;
    }

    public RestDto.ExecutionProfileDto toDto() {
        return new RestDto.ExecutionProfileDto(
                getProfileId().isBlank() ? null : getProfileId(),
                getProfileName(),
                getFunctionId(),
                getArgumentsJson(),
                getInstructionBudget(),
                isUnlimitedInstructions(),
                getTimeLimitMs(),
                isUnlimitedTime(),
                getMemoryMb(),
                getPriority(),
                isNotifyOnFailure(),
                null,
                null,
                getVersion()
        );
    }

    public StringProperty profileIdProperty() { return profileId; }
    public String getProfileId() { return profileId.get(); }
    public void setProfileId(String value) { this.profileId.set(value != null ? value : ""); }

    public StringProperty profileNameProperty() { return profileName; }
    public String getProfileName() { return profileName.get(); }
    public void setProfileName(String value) { this.profileName.set(value != null ? value : ""); }

    public ObjectProperty<UUID> functionIdProperty() { return functionId; }
    public UUID getFunctionId() { return functionId.get(); }
    public void setFunctionId(UUID value) { this.functionId.set(value); }

    public StringProperty functionNameProperty() { return functionName; }
    public String getFunctionName() { return functionName.get(); }
    public void setFunctionName(String value) { this.functionName.set(value != null ? value : ""); }

    public StringProperty argumentsJsonProperty() { return argumentsJson; }
    public String getArgumentsJson() { return argumentsJson.get(); }
    public void setArgumentsJson(String value) { this.argumentsJson.set(value != null ? value : "{}"); }

    public LongProperty instructionBudgetProperty() { return instructionBudget; }
    public long getInstructionBudget() { return instructionBudget.get(); }
    public void setInstructionBudget(long value) { this.instructionBudget.set(value); }

    public BooleanProperty unlimitedInstructionsProperty() { return unlimitedInstructions; }
    public boolean isUnlimitedInstructions() { return unlimitedInstructions.get(); }
    public void setUnlimitedInstructions(boolean value) { this.unlimitedInstructions.set(value); }

    public LongProperty timeLimitMsProperty() { return timeLimitMs; }
    public long getTimeLimitMs() { return timeLimitMs.get(); }
    public void setTimeLimitMs(long value) { this.timeLimitMs.set(value); }

    public BooleanProperty unlimitedTimeProperty() { return unlimitedTime; }
    public boolean isUnlimitedTime() { return unlimitedTime.get(); }
    public void setUnlimitedTime(boolean value) { this.unlimitedTime.set(value); }

    public LongProperty memoryMbProperty() { return memoryMb; }
    public long getMemoryMb() { return memoryMb.get(); }
    public void setMemoryMb(long value) { this.memoryMb.set(value); }

    public StringProperty priorityProperty() { return priority; }
    public String getPriority() { return priority.get(); }
    public void setPriority(String value) { this.priority.set(value != null ? value : "NORMAL"); }

    public BooleanProperty notifyOnFailureProperty() { return notifyOnFailure; }
    public boolean isNotifyOnFailure() { return notifyOnFailure.get(); }
    public void setNotifyOnFailure(boolean value) { this.notifyOnFailure.set(value); }

    public LongProperty versionProperty() { return version; }
    public long getVersion() { return version.get(); }
    public void setVersion(long value) { this.version.set(value); }

    @Override
    public String toString() {
        return getProfileName();
    }
}
