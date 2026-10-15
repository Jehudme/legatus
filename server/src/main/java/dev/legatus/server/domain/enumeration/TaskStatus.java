package dev.legatus.server.domain.enumeration;

/**
 * Deterministic task lifecycle states & terminal outcomes
 * (server-spec.md Section 4.3 & worker-wire.md Section 4.7)
 */
public enum TaskStatus {
    QUEUED,
    DISPATCHED,
    EXECUTING,
    ABORTING,
    COMPLETED,
    FAILED,
    ERR_INSTRUCTION_BUDGET_EXCEEDED,
    ERR_OUT_OF_MEMORY,
    ERR_TIMEOUT,
    ERR_BYTECODE_INVALID,
    ERR_WORKER_LOST,
    ABORTED,
    KILLED_FORCIBLY,
    CRASHED,
}
