package dev.legatus.server.service.scheduler;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic Bin-Packing Task Scheduler Engine.
 * <p>
 * Complies with {@code server-spec.md} Section 4.3 &amp; Section 7 (Table 7 line 445):
 * <ul>
 *   <li>Enforces the 5-predicate worker eligibility filter.</li>
 *   <li>Supports pluggable strategies: {@code LEAST_LOADED}, {@code BIN_PACK}, {@code ROUND_ROBIN}, {@code LOCALITY_FIRST}.</li>
 *   <li>Provides JPA {@code @Version} optimistic locking retry logic catching {@link OptimisticLockingFailureException}.</li>
 *   <li>Dispatches scheduled tasks via {@link TaskDispatcher}.</li>
 * </ul>
 */
@Service
public class TaskSchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskSchedulerService.class);

    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(TaskStatus.DISPATCHED, TaskStatus.EXECUTING);
    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 3;

    private final WorkerNodeRepository workerNodeRepository;
    private final ComputeTaskRepository computeTaskRepository;
    private final TaskDispatcher taskDispatcher;
    private final WorkerEligibilityFilter workerEligibilityFilter;
    private final LegatusProperties legatusProperties;
    private final AtomicInteger roundRobinCursor = new AtomicInteger(0);

    public TaskSchedulerService(
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        TaskDispatcher taskDispatcher,
        WorkerEligibilityFilter workerEligibilityFilter,
        LegatusProperties legatusProperties
    ) {
        this.workerNodeRepository = workerNodeRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.taskDispatcher = taskDispatcher;
        this.workerEligibilityFilter = workerEligibilityFilter;
        this.legatusProperties = legatusProperties;
    }

    /**
     * Resolves the configured scheduling strategy from application properties.
     */
    public SchedulingStrategy getActiveStrategy() {
        if (legatusProperties != null && legatusProperties.getScheduler() != null) {
            return SchedulingStrategy.fromString(legatusProperties.getScheduler().getStrategy());
        }
        return SchedulingStrategy.LEAST_LOADED;
    }

    /**
     * Selects an eligible worker node for the candidate task based on the specified scheduling strategy.
     *
     * @param candidateTask task awaiting assignment
     * @param candidateSnapshots snapshots of active worker nodes
     * @param strategy scheduling algorithm to execute
     * @return chosen {@link WorkerNode}, or empty if no node is eligible
     */
    public Optional<WorkerNode> selectWorker(
        ComputeTask candidateTask,
        List<WorkerSnapshot> candidateSnapshots,
        SchedulingStrategy strategy
    ) {
        if (candidateTask == null || candidateSnapshots == null || candidateSnapshots.isEmpty()) {
            return Optional.empty();
        }

        List<WorkerSnapshot> eligible = candidateSnapshots.stream()
            .filter(s -> workerEligibilityFilter.isEligible(
                s.worker(),
                candidateTask,
                s.activeTaskCount(),
                s.allocatedMemoryBytes(),
                s.freeHostMemoryBytes()
            ))
            .toList();

        if (eligible.isEmpty()) {
            LOG.debug("No eligible worker available for candidate task {}", candidateTask.getTaskId());
            return Optional.empty();
        }

        if (eligible.size() == 1) {
            return Optional.of(eligible.get(0).worker());
        }

        SchedulingStrategy effectiveStrategy = strategy != null ? strategy : SchedulingStrategy.LEAST_LOADED;

        return switch (effectiveStrategy) {
            case LEAST_LOADED -> selectLeastLoaded(eligible);
            case BIN_PACK -> selectBinPack(eligible);
            case ROUND_ROBIN -> selectRoundRobin(eligible);
            case LOCALITY_FIRST -> selectLocalityFirst(eligible, candidateTask.getBytecodeSha256());
        };
    }

    private Optional<WorkerNode> selectLeastLoaded(List<WorkerSnapshot> eligible) {
        return eligible.stream()
            .min(Comparator.comparingDouble(WorkerSnapshot::loadScore)
                .thenComparing(s -> s.worker().getWorkerId()))
            .map(WorkerSnapshot::worker);
    }

    private Optional<WorkerNode> selectBinPack(List<WorkerSnapshot> eligible) {
        return eligible.stream()
            .min(Comparator.comparingLong(WorkerSnapshot::availableMemoryBytes)
                .thenComparing(s -> s.worker().getWorkerId()))
            .map(WorkerSnapshot::worker);
    }

    private Optional<WorkerNode> selectRoundRobin(List<WorkerSnapshot> eligible) {
        List<WorkerSnapshot> sorted = eligible.stream()
            .sorted(Comparator.comparing(s -> s.worker().getWorkerId()))
            .toList();
        int index = (roundRobinCursor.getAndIncrement() & Integer.MAX_VALUE) % sorted.size();
        return Optional.of(sorted.get(index).worker());
    }

    private Optional<WorkerNode> selectLocalityFirst(List<WorkerSnapshot> eligible, String bytecodeSha256) {
        List<WorkerSnapshot> local = eligible.stream()
            .filter(WorkerSnapshot::hasBytecodeCached)
            .toList();
        List<WorkerSnapshot> pool = !local.isEmpty() ? local : eligible;
        return selectLeastLoaded(pool);
    }

    /**
     * Assembles a snapshot of active candidate workers with their current capacity and active tasks.
     */
    public List<WorkerSnapshot> buildWorkerSnapshots() {
        List<WorkerNode> onlineWorkers = workerNodeRepository.findAllByStateAndDrainingFalse(WorkerState.ONLINE);
        List<WorkerSnapshot> snapshots = new ArrayList<>(onlineWorkers.size());

        for (WorkerNode worker : onlineWorkers) {
            List<ComputeTask> activeTasks = computeTaskRepository.findAllByAssignedWorkerWorkerIdAndStatusIn(
                worker.getWorkerId(),
                ACTIVE_STATUSES
            );
            int activeCount = activeTasks.size();
            long allocatedMemory = activeTasks.stream()
                .mapToLong(t -> t.getMaxMemoryBytes() != null ? t.getMaxMemoryBytes() : 0L)
                .sum();

            snapshots.add(new WorkerSnapshot(
                worker,
                activeCount,
                allocatedMemory,
                null, // live host telemetry if available
                0.0,
                false // will be queried per task
            ));
        }

        return snapshots;
    }

    /**
     * Assigns and dispatches a task with optimistic locking retry logic.
     * Complies with failure mode matrix: ERR_CONCURRENT_STATE_MUTATION retry.
     *
     * @param taskId task identifier to schedule
     * @return assigned worker if successfully dispatched, empty otherwise
     */
    public Optional<WorkerNode> assignAndDispatchTaskWithRetry(String taskId) {
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_RETRIES; attempt++) {
            try {
                return executeSchedulingAttempt(taskId);
            } catch (OptimisticLockingFailureException e) {
                LOG.warn("Optimistic lock conflict for task {}, attempt {}/{}", taskId, attempt, MAX_OPTIMISTIC_LOCK_RETRIES);
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    LOG.error("Exhausted optimistic locking retries for task {}: ERR_CONCURRENT_STATE_MUTATION", taskId, e);
                    throw e;
                }
                try {
                    Thread.sleep(25L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Scheduling interrupted during optimistic lock backoff", ie);
                }
            }
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<WorkerNode> executeSchedulingAttempt(String taskId) {
        Optional<ComputeTask> taskOpt = computeTaskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            LOG.warn("Task {} not found during scheduling attempt", taskId);
            return Optional.empty();
        }

        ComputeTask task = taskOpt.get();
        if (task.getStatus() != TaskStatus.QUEUED) {
            LOG.debug("Task {} is no longer in QUEUED state (current: {})", taskId, task.getStatus());
            return Optional.empty();
        }

        List<WorkerNode> onlineWorkers = workerNodeRepository.findAllByStateAndDrainingFalse(WorkerState.ONLINE);
        if (onlineWorkers.isEmpty()) {
            LOG.debug("No ONLINE worker nodes available in cluster for task {}", taskId);
            return Optional.empty();
        }

        List<WorkerSnapshot> snapshots = new ArrayList<>(onlineWorkers.size());
        for (WorkerNode worker : onlineWorkers) {
            List<ComputeTask> activeTasks = computeTaskRepository.findAllByAssignedWorkerWorkerIdAndStatusIn(
                worker.getWorkerId(),
                ACTIVE_STATUSES
            );
            int activeCount = activeTasks.size();
            long allocatedMemory = activeTasks.stream()
                .mapToLong(t -> t.getMaxMemoryBytes() != null ? t.getMaxMemoryBytes() : 0L)
                .sum();
            boolean isCached = taskDispatcher.isBytecodeCached(worker.getWorkerId(), task.getBytecodeSha256());

            snapshots.add(new WorkerSnapshot(
                worker,
                activeCount,
                allocatedMemory,
                null,
                0.0,
                isCached
            ));
        }

        Optional<WorkerNode> selectedWorkerOpt = selectWorker(task, snapshots, getActiveStrategy());
        if (selectedWorkerOpt.isEmpty()) {
            return Optional.empty();
        }

        WorkerNode selectedWorker = selectedWorkerOpt.get();
        task.setAssignedWorker(selectedWorker);
        task.setStatus(TaskStatus.DISPATCHED);
        task.setStartedAt(Instant.now());

        // Save with optimistic locking check
        computeTaskRepository.saveAndFlush(task);

        boolean dispatched = taskDispatcher.dispatchTask(task, selectedWorker);
        if (dispatched) {
            LOG.info("Successfully scheduled and dispatched task {} to worker {}", taskId, selectedWorker.getWorkerId());
            return Optional.of(selectedWorker);
        } else {
            LOG.warn("Worker {} failed to accept task {} dispatch (socket unreachable). Re-queueing.",
                selectedWorker.getWorkerId(), taskId);
            task.setAssignedWorker(null);
            task.setStatus(TaskStatus.QUEUED);
            computeTaskRepository.saveAndFlush(task);
            return Optional.empty();
        }
    }

    /**
     * Schedules the next batch of queued tasks ordered by priority weight descending and creation timestamp ascending.
     *
     * @return number of successfully dispatched tasks
     */
    public int scheduleQueuedTasks() {
        int batchSize = (legatusProperties != null && legatusProperties.getScheduler() != null)
            ? legatusProperties.getScheduler().getDispatchBatchSize()
            : 64;

        List<ComputeTask> queuedTasks = computeTaskRepository.findAllByStatusOrderByPriorityWeightDescCreatedAtAsc(
            TaskStatus.QUEUED,
            PageRequest.of(0, batchSize)
        );

        int dispatchedCount = 0;
        for (ComputeTask task : queuedTasks) {
            Optional<WorkerNode> assigned = assignAndDispatchTaskWithRetry(task.getTaskId());
            if (assigned.isPresent()) {
                dispatchedCount++;
            }
        }

        return dispatchedCount;
    }
}
