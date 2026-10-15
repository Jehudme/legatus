package dev.legatus.server.domain.enumeration;

/**
 * Worker node lifecycle and quarantine enrollment states
 * (server-spec.md Section 4.1)
 */
public enum WorkerState {
    PENDING_APPROVAL,
    APPROVED,
    ONLINE,
    DRAINING,
    OFFLINE,
    REVOKED,
    EXPIRED,
}
