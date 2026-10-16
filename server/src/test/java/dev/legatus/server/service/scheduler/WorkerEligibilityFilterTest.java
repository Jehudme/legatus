package dev.legatus.server.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.WorkerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WorkerEligibilityFilter} verifying all 5 scheduling predicates.
 */
class WorkerEligibilityFilterTest {

    private WorkerEligibilityFilter filter;
    private WorkerNode worker;
    private ComputeTask task;

    @BeforeEach
    void setUp() {
        filter = new WorkerEligibilityFilter();

        worker = new WorkerNode()
            .workerId("wk-node-001")
            .state(WorkerState.ONLINE)
            .draining(false)
            .allocatableCores(4)
            .maxMemoryBytes(4L * 1024 * 1024 * 1024) // 4 GB
            .maxInstructionBudget(100_000_000L); // 100M

        task = new ComputeTask()
            .taskId("task-cand-001")
            .maxMemoryBytes(512L * 1024 * 1024) // 512 MB
            .instructionBudget(50_000_000L); // 50M
    }

    @Test
    @DisplayName("Should accept worker when all 5 scheduling predicates hold")
    void testAllPredicatesSatisfied() {
        boolean eligible = filter.isEligible(worker, task, 2, 1024L * 1024 * 1024, null);
        assertThat(eligible).isTrue();
    }

    @Test
    @DisplayName("Predicate 1: Should reject worker when state is not ONLINE")
    void testPredicate1StateNotOnline() {
        worker.setState(WorkerState.OFFLINE);
        assertThat(filter.isEligible(worker, task, 0, 0L, null)).isFalse();

        worker.setState(WorkerState.PENDING_APPROVAL);
        assertThat(filter.isEligible(worker, task, 0, 0L, null)).isFalse();
    }

    @Test
    @DisplayName("Predicate 2: Should reject worker when draining is true")
    void testPredicate2Draining() {
        worker.setDraining(true);
        assertThat(filter.isEligible(worker, task, 0, 0L, null)).isFalse();
    }

    @Test
    @DisplayName("Predicate 3: Should accept up to maxConcurrentTasks (16) regardless of core count (4), rejecting on reaching limit")
    void testPredicate3MaxConcurrentTasksQuota() {
        worker.setMaxConcurrentTasks(16);
        // Allocatable cores is 4, but maxConcurrentTasks is 16: tasks 1 to 16 are accepted
        assertThat(filter.isEligible(worker, task, 0, 0L, null)).isTrue();
        assertThat(filter.isEligible(worker, task, 4, 0L, null)).isTrue();
        assertThat(filter.isEligible(worker, task, 15, 0L, null)).isTrue();

        // 16 active tasks -> limit reached -> ineligible for 17th
        assertThat(filter.isEligible(worker, task, 16, 0L, null)).isFalse();
    }

    @Test
    @DisplayName("Predicate 3: Should fall back to allocatable cores when maxConcurrentTasks is unspecified")
    void testPredicate3FallbackToCores() {
        worker.setMaxConcurrentTasks(null);
        worker.setAllocatableCores(4);
        assertThat(filter.isEligible(worker, task, 3, 0L, null)).isTrue();
        assertThat(filter.isEligible(worker, task, 4, 0L, null)).isFalse();
    }

    @Test
    @DisplayName("Predicate 4: Should reject worker when available memory is less than required")
    void testPredicate4InsufficientAvailableMemory() {
        // 4GB max, 3.6GB allocated -> 400MB available (< 512MB required) -> ineligible
        long allocated = (long) (3.6 * 1024 * 1024 * 1024);
        assertThat(filter.isEligible(worker, task, 1, allocated, null)).isFalse();

        // Host free memory lower than required -> ineligible
        Long lowHostFree = 256L * 1024 * 1024;
        assertThat(filter.isEligible(worker, task, 1, 1024L * 1024 * 1024, lowHostFree)).isFalse();
    }

    @Test
    @DisplayName("Predicate 5: Should reject worker when max instruction budget is less than task budget")
    void testPredicate5InsufficientInstructionBudget() {
        // Worker max is 100M, task requires 120M -> ineligible
        task.setInstructionBudget(120_000_000L);
        assertThat(filter.isEligible(worker, task, 1, 1024L * 1024 * 1024, null)).isFalse();
    }
}
