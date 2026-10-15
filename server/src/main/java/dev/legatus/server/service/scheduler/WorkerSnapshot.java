package dev.legatus.server.service.scheduler;

import dev.legatus.server.domain.WorkerNode;

/**
 * Snapshot of a worker node's current capacity, load, and cache status during a scheduling cycle.
 */
public record WorkerSnapshot(
    WorkerNode worker,
    int activeTaskCount,
    long allocatedMemoryBytes,
    Long freeHostMemoryBytes,
    double hostCpuPercent,
    boolean hasBytecodeCached
) {
    public long availableMemoryBytes() {
        long max = worker.getMaxMemoryBytes() != null ? worker.getMaxMemoryBytes() : 0L;
        return max - allocatedMemoryBytes;
    }

    /**
     * Mathematical load score according to server-spec.md Section 4.3:
     * |T_w| / C_allocatable(w) + 0.5 * (CPU_host(w) / 100)
     */
    public double loadScore() {
        int allocatableCores = worker.getAllocatableCores() != null && worker.getAllocatableCores() > 0
            ? worker.getAllocatableCores()
            : 1;
        return ((double) activeTaskCount / allocatableCores) + (0.5 * (hostCpuPercent / 100.0));
    }
}
