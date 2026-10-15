package dev.legatus.server.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit tests for {@link TaskSchedulerService} verifying scheduling algorithms and optimistic locking retry.
 */
class TaskSchedulerServiceTest {

    private WorkerNodeRepository workerNodeRepository;
    private ComputeTaskRepository computeTaskRepository;
    private TaskDispatcher taskDispatcher;
    private WorkerEligibilityFilter workerEligibilityFilter;
    private LegatusProperties legatusProperties;
    private TaskSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        workerNodeRepository = mock(WorkerNodeRepository.class);
        computeTaskRepository = mock(ComputeTaskRepository.class);
        taskDispatcher = mock(TaskDispatcher.class);
        workerEligibilityFilter = new WorkerEligibilityFilter();
        legatusProperties = new LegatusProperties();

        schedulerService = new TaskSchedulerService(
            workerNodeRepository,
            computeTaskRepository,
            taskDispatcher,
            workerEligibilityFilter,
            legatusProperties
        );
    }

    private WorkerNode createWorker(String id, int cores, long maxMemory) {
        return new WorkerNode()
            .workerId(id)
            .state(WorkerState.ONLINE)
            .draining(false)
            .allocatableCores(cores)
            .maxMemoryBytes(maxMemory)
            .maxInstructionBudget(100_000_000L);
    }

    private ComputeTask createTask(String id, long reqMemory, long budget) {
        return new ComputeTask()
            .taskId(id)
            .name("test-task")
            .status(TaskStatus.QUEUED)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(true)
            .entryPoint("main")
            .bytecodeSha256("sha256-test-bc")
            .maxMemoryBytes(reqMemory)
            .instructionBudget(budget)
            .attemptCount(0)
            .maxRetries(2);
    }

    @Test
    @DisplayName("LEAST_LOADED strategy should select worker with lowest load ratio")
    void testLeastLoadedStrategy() {
        WorkerNode w1 = createWorker("wk-1", 4, 8L * 1024 * 1024 * 1024);
        WorkerNode w2 = createWorker("wk-2", 4, 8L * 1024 * 1024 * 1024);

        // w1 has 2 active tasks (load = 2/4 = 0.5)
        WorkerSnapshot s1 = new WorkerSnapshot(w1, 2, 2L * 1024 * 1024 * 1024, null, 10.0, false);
        // w2 has 1 active task (load = 1/4 = 0.25)
        WorkerSnapshot s2 = new WorkerSnapshot(w2, 1, 1L * 1024 * 1024 * 1024, null, 10.0, false);

        ComputeTask task = createTask("t1", 512L * 1024 * 1024, 10_000_000L);

        Optional<WorkerNode> selected = schedulerService.selectWorker(task, List.of(s1, s2), SchedulingStrategy.LEAST_LOADED);
        assertThat(selected).contains(w2);
    }

    @Test
    @DisplayName("BIN_PACK strategy should select worker with least remaining available memory")
    void testBinPackStrategy() {
        WorkerNode w1 = createWorker("wk-1", 4, 8L * 1024 * 1024 * 1024);
        WorkerNode w2 = createWorker("wk-2", 4, 8L * 1024 * 1024 * 1024);

        // w1 has 2GB allocated -> 6GB available
        WorkerSnapshot s1 = new WorkerSnapshot(w1, 1, 2L * 1024 * 1024 * 1024, null, 0.0, false);
        // w2 has 6GB allocated -> 2GB available
        WorkerSnapshot s2 = new WorkerSnapshot(w2, 1, 6L * 1024 * 1024 * 1024, null, 0.0, false);

        ComputeTask task = createTask("t1", 512L * 1024 * 1024, 10_000_000L);

        // BIN_PACK packs onto w2 because w2 has least available memory (2GB < 6GB)
        Optional<WorkerNode> selected = schedulerService.selectWorker(task, List.of(s1, s2), SchedulingStrategy.BIN_PACK);
        assertThat(selected).contains(w2);
    }

    @Test
    @DisplayName("ROUND_ROBIN strategy should cycle across eligible workers")
    void testRoundRobinStrategy() {
        WorkerNode w1 = createWorker("wk-1", 4, 8L * 1024 * 1024 * 1024);
        WorkerNode w2 = createWorker("wk-2", 4, 8L * 1024 * 1024 * 1024);

        WorkerSnapshot s1 = new WorkerSnapshot(w1, 0, 0L, null, 0.0, false);
        WorkerSnapshot s2 = new WorkerSnapshot(w2, 0, 0L, null, 0.0, false);

        ComputeTask task = createTask("t1", 512L * 1024 * 1024, 10_000_000L);

        WorkerNode first = schedulerService.selectWorker(task, List.of(s1, s2), SchedulingStrategy.ROUND_ROBIN).orElseThrow();
        WorkerNode second = schedulerService.selectWorker(task, List.of(s1, s2), SchedulingStrategy.ROUND_ROBIN).orElseThrow();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("LOCALITY_FIRST strategy should prefer worker with cached bytecode")
    void testLocalityFirstStrategy() {
        WorkerNode w1 = createWorker("wk-1", 4, 8L * 1024 * 1024 * 1024);
        WorkerNode w2 = createWorker("wk-2", 4, 8L * 1024 * 1024 * 1024);

        // w1 has higher load (2 tasks) BUT has bytecode cached
        WorkerSnapshot s1 = new WorkerSnapshot(w1, 2, 2L * 1024 * 1024 * 1024, null, 0.0, true);
        // w2 has lower load (0 tasks) but NO bytecode cached
        WorkerSnapshot s2 = new WorkerSnapshot(w2, 0, 0L, null, 0.0, false);

        ComputeTask task = createTask("t1", 512L * 1024 * 1024, 10_000_000L);

        Optional<WorkerNode> selected = schedulerService.selectWorker(task, List.of(s1, s2), SchedulingStrategy.LOCALITY_FIRST);
        assertThat(selected).contains(w1);
    }

    @Test
    @DisplayName("Should retry on OptimisticLockingFailureException and succeed")
    void testOptimisticLockingRetry() {
        WorkerNode worker = createWorker("wk-retry", 4, 8L * 1024 * 1024 * 1024);
        ComputeTask task = createTask("task-opt-lock", 512L * 1024 * 1024, 10_000_000L);

        when(computeTaskRepository.findById("task-opt-lock")).thenAnswer(inv ->
            Optional.of(createTask("task-opt-lock", 512L * 1024 * 1024, 10_000_000L))
        );
        when(workerNodeRepository.findAllByStateAndDrainingFalse(WorkerState.ONLINE)).thenReturn(List.of(worker));
        when(computeTaskRepository.findAllByAssignedWorkerWorkerIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(taskDispatcher.isBytecodeCached(any(), any())).thenReturn(false);
        when(taskDispatcher.dispatchTask(any(), any())).thenReturn(true);

        // First save throws optimistic locking conflict, second save succeeds
        when(computeTaskRepository.saveAndFlush(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(ComputeTask.class, "task-opt-lock"))
            .thenReturn(task);

        Optional<WorkerNode> result = schedulerService.assignAndDispatchTaskWithRetry("task-opt-lock");

        assertThat(result).contains(worker);
        verify(computeTaskRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should return empty when no workers in cluster are eligible")
    void testNoEligibleWorkers() {
        // Worker is OFFLINE
        WorkerNode offlineWorker = createWorker("wk-offline", 4, 8L * 1024 * 1024 * 1024);
        offlineWorker.setState(WorkerState.OFFLINE);

        WorkerSnapshot snapshot = new WorkerSnapshot(offlineWorker, 0, 0L, null, 0.0, false);
        ComputeTask task = createTask("task-ineligible", 512L * 1024 * 1024, 10_000_000L);

        Optional<WorkerNode> selected = schedulerService.selectWorker(task, List.of(snapshot), SchedulingStrategy.LEAST_LOADED);
        assertThat(selected).isEmpty();
    }
}
