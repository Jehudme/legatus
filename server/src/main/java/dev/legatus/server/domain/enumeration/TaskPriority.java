package dev.legatus.server.domain.enumeration;

/**
 * Task scheduling queue priority tiers (api-endpoint.md Section 4.1)
 */
public enum TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
}
