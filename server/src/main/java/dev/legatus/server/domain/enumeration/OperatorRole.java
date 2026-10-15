package dev.legatus.server.domain.enumeration;

/**
 * Multi-operator Role-Based Access Control (RBAC) privilege tiers
 * (server-spec.md Section 4.5.1)
 */
public enum OperatorRole {
    CLUSTER_ADMIN,
    OPERATOR,
    AUDITOR,
}
