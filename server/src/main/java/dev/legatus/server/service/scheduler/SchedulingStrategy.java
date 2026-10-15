package dev.legatus.server.service.scheduler;

/**
 * Pluggable scheduling algorithm options (server-spec.md Section 4.3).
 */
public enum SchedulingStrategy {
    /**
     * Spreads load evenly across all nodes based on allocatable core ratio and host CPU utilization.
     * arg min_w ( |T_w| / C_allocatable(w) + 0.5 * (CPU_host(w) / 100) )
     */
    LEAST_LOADED,

    /**
     * Maximizes node density by packing tasks onto partially full nodes so idle nodes can sleep.
     * arg min_w ( M_avail(w) )
     */
    BIN_PACK,

    /**
     * Cyclic cursor across the eligible worker ring.
     */
    ROUND_ROBIN,

    /**
     * Prefers workers that already hold the target bytecode.sha256 in local cache,
     * falling back to LEAST_LOADED.
     */
    LOCALITY_FIRST;

    public static SchedulingStrategy fromString(String name) {
        if (name == null || name.isBlank()) {
            return LEAST_LOADED;
        }
        try {
            return SchedulingStrategy.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LEAST_LOADED;
        }
    }
}
