package dev.legatus.server.service.scheduler;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import java.util.Set;

/**
 * SPI for dispatching scheduled tasks over transport channels (e.g. WebSocket).
 */
public interface TaskDispatcher {

    /**
     * Transmits a DISPATCH_TASK frame for the assigned task to the designated worker.
     *
     * @param task assigned task in DISPATCHED state
     * @param worker target compute node
     * @return true if successfully transmitted and transitioned to EXECUTING, false otherwise
     */
    boolean dispatchTask(ComputeTask task, WorkerNode worker);

    /**
     * Checks if the worker currently holds the specified bytecode SHA-256 binary in local cache.
     *
     * @param workerId worker node identifier
     * @param bytecodeSha256 hexadecimal SHA-256 digest of bytecode
     * @return true if cached, false otherwise
     */
    boolean isBytecodeCached(String workerId, String bytecodeSha256);

    /**
     * Returns the set of currently active task IDs on the worker.
     *
     * @param workerId worker node identifier
     * @return set of active task IDs
     */
    Set<String> getActiveTaskIds(String workerId);
}
