package dev.legatus.server.domain.enumeration;

/**
 * Operator account lifecycle states (server-spec.md Section 4.5)
 */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED,
}
