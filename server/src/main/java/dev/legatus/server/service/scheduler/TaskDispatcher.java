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

    /**
     * Checks whether the specified worker currently has an active, connected transport session.
     *
     * @param workerId worker node identifier
     * @return true if an active transport session exists
     */
    default boolean hasActiveSession(String workerId) {
        return true;
    }

    /**
     * Transmits an ABORT_TASK frame to cancel an in-flight task on a worker node.
     *
     * @param workerId worker node identifier
     * @param taskId task identifier to abort
     * @param reason cancellation reason
     * @param gracePeriodMs graceful termination deadline in milliseconds
     * @return true if frame successfully transmitted over active transport session
     */
    boolean abortTask(String workerId, String taskId, String reason, int gracePeriodMs);
}
