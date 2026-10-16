package dev.legatus.server.features.workflows;

public enum StepStatus {
    WAITING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}
