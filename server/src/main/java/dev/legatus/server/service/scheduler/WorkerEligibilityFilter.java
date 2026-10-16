package dev.legatus.server.service.scheduler;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.WorkerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates the 5 deterministic scheduling predicates (server-spec.md Section 4.3).
 * <ol>
 *   <li>State(w) == ONLINE</li>
 *   <li>Draining(w) == false</li>
 *   <li>C_avail(w) >= 1 (where C_avail(w) = C_allocatable(w) - |T_w|)</li>
 *   <li>M_avail(w) >= M_req(t_cand) AND M_free_host(w) >= M_req(t_cand) (where M_avail(w) = M_max(w) - sum_{t in T_w} M_req(t))</li>
 *   <li>I_max_per_task(w) >= I_budget(t_cand)</li>
 * </ol>
 */
@Component
public class WorkerEligibilityFilter {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerEligibilityFilter.class);

    /**
     * Asserts whether a compute node satisfies all five scheduling predicates for a candidate task.
     *
     * @param worker candidate compute node
     * @param candidateTask task awaiting assignment
     * @param activeTaskCount current number of active (DISPATCHED/EXECUTING) tasks on worker (|T_w|)
     * @param currentAllocatedMemoryBytes sum of max_memory_bytes across currently active tasks
     * @param freeHostMemoryBytes optional live free host RAM reported in heartbeat telemetry
     * @return true if all 5 predicates hold, false otherwise
     */
    public boolean isEligible(
        WorkerNode worker,
        ComputeTask candidateTask,
        int activeTaskCount,
        long currentAllocatedMemoryBytes,
        Long freeHostMemoryBytes
    ) {
        if (worker == null || candidateTask == null) {
            return false;
        }

        // Predicate 1: State(w) == ONLINE
        if (worker.getState() != WorkerState.ONLINE) {
            LOG.debug("Worker {} rejected: state is {} (expected ONLINE)", worker.getWorkerId(), worker.getState());
            return false;
        }

        // Predicate 2: Draining(w) == false
        if (Boolean.TRUE.equals(worker.getDraining())) {
            LOG.debug("Worker {} rejected: node is draining", worker.getWorkerId());
            return false;
        }

        // Predicate 3: Task concurrency capacity check: activeTaskCount < maxConcurrentTasks
        int maxConcurrentTasks = worker.getMaxConcurrentTasks() != null && worker.getMaxConcurrentTasks() > 0
            ? worker.getMaxConcurrentTasks()
            : (worker.getAllocatableCores() != null ? worker.getAllocatableCores() : 1);
        if (activeTaskCount >= maxConcurrentTasks) {
            LOG.debug("Worker {} rejected: max concurrency reached (maxConcurrentTasks: {}, active: {})",
                worker.getWorkerId(), maxConcurrentTasks, activeTaskCount);
            return false;
        }

        // Predicate 4: M_avail(w) >= M_req(t_cand) AND M_free_host(w) >= M_req(t_cand)
        long requiredMemory = candidateTask.getMaxMemoryBytes() != null ? candidateTask.getMaxMemoryBytes() : 0L;
        long maxMemory = worker.getMaxMemoryBytes() != null ? worker.getMaxMemoryBytes() : 0L;
        long availableMemory = maxMemory - currentAllocatedMemoryBytes;
        if (availableMemory < requiredMemory) {
            LOG.debug("Worker {} rejected: insufficient available memory quota (avail: {}, req: {})",
                worker.getWorkerId(), availableMemory, requiredMemory);
            return false;
        }
        if (freeHostMemoryBytes != null && freeHostMemoryBytes < requiredMemory) {
            LOG.debug("Worker {} rejected: insufficient host free memory (free: {}, req: {})",
                worker.getWorkerId(), freeHostMemoryBytes, requiredMemory);
            return false;
        }

        // Predicate 5: I_max_per_task(w) >= I_budget(t_cand)
        long maxInstructionBudget = worker.getMaxInstructionBudget() != null ? worker.getMaxInstructionBudget() : 0L;
        long requiredInstructionBudget = candidateTask.getInstructionBudget() != null ? candidateTask.getInstructionBudget() : 0L;
        if (maxInstructionBudget < requiredInstructionBudget) {
            LOG.debug("Worker {} rejected: instruction budget quota exceeded (worker max: {}, task req: {})",
                worker.getWorkerId(), maxInstructionBudget, requiredInstructionBudget);
            return false;
        }

        return true;
    }
}
