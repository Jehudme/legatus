package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable task execution record driving the Task Explorer and Live Inspector.
 */
public class TaskRecord {

    private final StringProperty taskId = new SimpleStringProperty("");
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty status = new SimpleStringProperty("QUEUED");
    private final IntegerProperty exitCode = new SimpleIntegerProperty(-1);
    private final StringProperty assignedWorkerId = new SimpleStringProperty("");
    private final LongProperty instructionsConsumed = new SimpleLongProperty(0);
    private final LongProperty instructionBudget = new SimpleLongProperty(10_000_000);
    private final LongProperty peakMemoryBytes = new SimpleLongProperty(0);
    private final LongProperty maxMemoryBytes = new SimpleLongProperty(67_108_864); // 64 MB
    private final LongProperty executionWallTimeMs = new SimpleLongProperty(0);
    private final DoubleProperty progressPct = new SimpleDoubleProperty(0.0);
    private final IntegerProperty abortCountdownSeconds = new SimpleIntegerProperty(10);
    private final BooleanProperty isAborting = new SimpleBooleanProperty(false);
    private final StringProperty createdAt = new SimpleStringProperty("");
    private final StringProperty finishedAt = new SimpleStringProperty("");

    public TaskRecord() {}

    public TaskRecord(String taskId, String name, String status, Integer exitCode, String assignedWorkerId,
                      Long instructionsConsumed, Long peakMemoryBytes, Long executionWallTimeMs,
                      String createdAt, String finishedAt) {
        this.taskId.set(taskId);
        this.name.set(name);
        this.status.set(status != null ? status : "QUEUED");
        if (exitCode != null) this.exitCode.set(exitCode);
        if (assignedWorkerId != null) this.assignedWorkerId.set(assignedWorkerId);
        if (instructionsConsumed != null) this.instructionsConsumed.set(instructionsConsumed);
        if (peakMemoryBytes != null) this.peakMemoryBytes.set(peakMemoryBytes);
        if (executionWallTimeMs != null) this.executionWallTimeMs.set(executionWallTimeMs);
        if (createdAt != null) this.createdAt.set(createdAt);
        if (finishedAt != null) this.finishedAt.set(finishedAt);
    }

    public StringProperty taskIdProperty() { return taskId; }
    public String getTaskId() { return taskId.get(); }
    public void setTaskId(String value) { this.taskId.set(value); }

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String value) { this.name.set(value); }

    public StringProperty statusProperty() { return status; }
    public String getStatus() { return status.get(); }
    public void setStatus(String value) { this.status.set(value); }

    public IntegerProperty exitCodeProperty() { return exitCode; }
    public int getExitCode() { return exitCode.get(); }
    public void setExitCode(int value) { this.exitCode.set(value); }

    public StringProperty assignedWorkerIdProperty() { return assignedWorkerId; }
    public String getAssignedWorkerId() { return assignedWorkerId.get(); }
    public void setAssignedWorkerId(String value) { this.assignedWorkerId.set(value); }

    public LongProperty instructionsConsumedProperty() { return instructionsConsumed; }
    public long getInstructionsConsumed() { return instructionsConsumed.get(); }
    public void setInstructionsConsumed(long value) { this.instructionsConsumed.set(value); }

    public LongProperty instructionBudgetProperty() { return instructionBudget; }
    public long getInstructionBudget() { return instructionBudget.get(); }
    public void setInstructionBudget(long value) { this.instructionBudget.set(value); }

    public LongProperty peakMemoryBytesProperty() { return peakMemoryBytes; }
    public long getPeakMemoryBytes() { return peakMemoryBytes.get(); }
    public void setPeakMemoryBytes(long value) { this.peakMemoryBytes.set(value); }

    public LongProperty maxMemoryBytesProperty() { return maxMemoryBytes; }
    public long getMaxMemoryBytes() { return maxMemoryBytes.get(); }
    public void setMaxMemoryBytes(long value) { this.maxMemoryBytes.set(value); }

    public LongProperty executionWallTimeMsProperty() { return executionWallTimeMs; }
    public long getExecutionWallTimeMs() { return executionWallTimeMs.get(); }
    public void setExecutionWallTimeMs(long value) { this.executionWallTimeMs.set(value); }

    public DoubleProperty progressPctProperty() { return progressPct; }
    public double getProgressPct() { return progressPct.get(); }
    public void setProgressPct(double value) { this.progressPct.set(value); }

    public IntegerProperty abortCountdownSecondsProperty() { return abortCountdownSeconds; }
    public int getAbortCountdownSeconds() { return abortCountdownSeconds.get(); }
    public void setAbortCountdownSeconds(int value) { this.abortCountdownSeconds.set(value); }

    public BooleanProperty isAbortingProperty() { return isAborting; }
    public boolean isIsAborting() { return isAborting.get(); }
    public void setIsAborting(boolean value) { this.isAborting.set(value); }

    public StringProperty createdAtProperty() { return createdAt; }
    public String getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(String value) { this.createdAt.set(value); }

    public StringProperty finishedAtProperty() { return finishedAt; }
    public String getFinishedAt() { return finishedAt.get(); }
    public void setFinishedAt(String value) { this.finishedAt.set(value); }
}
