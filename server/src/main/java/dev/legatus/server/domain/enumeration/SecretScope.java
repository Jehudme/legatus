package dev.legatus.server.domain.enumeration;

/**
 * Secret vault access scoping tiers (server-spec.md Section 5.1)
 */
public enum SecretScope {
    GLOBAL,
    CLUSTER,
    FUNCTION,
}
