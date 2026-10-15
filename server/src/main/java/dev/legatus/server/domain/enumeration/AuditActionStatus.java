package dev.legatus.server.domain.enumeration;

/**
 * Statutory audit trail action status outcomes (server-spec.md Section 5.3)
 */
public enum AuditActionStatus {
    SUCCESS,
    DENIED,
    FAILED,
}
