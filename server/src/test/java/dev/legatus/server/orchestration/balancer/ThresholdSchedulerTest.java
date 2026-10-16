package dev.legatus.server.orchestration.balancer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.service.scheduler.SchedulingStrategy;
import dev.legatus.server.service.scheduler.WorkerSnapshot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThresholdSchedulerTest {

    private ThresholdScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ThresholdScheduler();
    }

    @Test
    @DisplayName("Should smoothly update CPU and RAM metrics with alpha = 0.3 EMA")
    void testEmaSmoothing() {
        // Initial tick: raw=100.0, rawRam=50.0
        scheduler.recordTelemetry("w-1", 100.0, 50.0, 1, 75.0, 80.0, 10);
        assertThat(scheduler.getSmoothedCpu("w-1")).isEqualTo(100.0);
        assertThat(scheduler.getSmoothedRam("w-1")).isEqualTo(50.0);

        // Feed sample of 0.0 CPU: 0.3 * 0.0 + 0.7 * 100.0 = 70.0
        scheduler.recordTelemetry("w-1", 0.0, 100.0, 1, 75.0, 80.0, 10);
        assertThat(scheduler.getSmoothedCpu("w-1")).isCloseTo(70.0, org.assertj.core.data.Offset.offset(0.001));
        // RAM: 0.3 * 100.0 + 0.7 * 50.0 = 65.0
        assertThat(scheduler.getSmoothedRam("w-1")).isCloseTo(65.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Should select worker with headroom and not set warning flag when within thresholds")
    void testSelectWorkerWithinThresholds() {
        WorkerNode w1 = createWorker("w-1", 75.0, 80.0, 10);
        WorkerNode w2 = createWorker("w-2", 75.0, 80.0, 10);

        // w-1 has 20% CPU EMA, w-2 has 60% CPU EMA
        scheduler.recordTelemetry("w-1", 20.0, 30.0, 1, 75.0, 80.0, 10);
        scheduler.recordTelemetry("w-2", 60.0, 70.0, 2, 75.0, 80.0, 10);

        WorkerSnapshot s1 = new WorkerSnapshot(w1, 1, 1024L, 4L * 1024 * 1024 * 1024, 20.0, true);
        WorkerSnapshot s2 = new WorkerSnapshot(w2, 2, 2048L, 4L * 1024 * 1024 * 1024, 60.0, true);

        ComputeTask task = new ComputeTask();
        task.setTaskId("task-test-1");

        var selected = scheduler.selectWorkerWithThresholds(task, List.of(s1, s2), SchedulingStrategy.LEAST_LOADED);

        assertThat(selected).isPresent();
        assertThat(selected.get().getWorkerId()).isEqualTo("w-1");
        assertThat(scheduler.isThresholdBreachActive()).isFalse();
        assertThat(scheduler.isQueueSaturatedActive()).isFalse();
    }

    @Test
    @DisplayName("Should trigger amber breach warning when demand forces threshold breach but capacity exists")
    void testBreachThresholdsWhenNecessary() {
        WorkerNode w1 = createWorker("w-1", 50.0, 50.0, 10);
        // Worker is currently at 80% CPU and 80% RAM (breaching 50% target + 5% hysteresis)
        scheduler.recordTelemetry("w-1", 80.0, 80.0, 2, 50.0, 50.0, 10);

        WorkerSnapshot s1 = new WorkerSnapshot(w1, 2, 2048L, 4L * 1024 * 1024 * 1024, 80.0, true);

        ComputeTask task = new ComputeTask();
        task.setTaskId("task-test-2");

        var selected = scheduler.selectWorkerWithThresholds(task, List.of(s1), SchedulingStrategy.LEAST_LOADED);

        // w-1 still has task capacity (2 active < 10 max), so it is selected with breach warning
        assertThat(selected).isPresent();
        assertThat(selected.get().getWorkerId()).isEqualTo("w-1");
        assertThat(scheduler.isThresholdBreachActive()).isTrue();
        assertThat(scheduler.isQueueSaturatedActive()).isFalse();
        assertThat(scheduler.getBreachedWorkerIds()).contains("w-1");
    }

    @Test
    @DisplayName("Should trigger red queue saturated error when max tasks is reached and task must queue")
    void testQueueSaturationError() {
        WorkerNode w1 = createWorker("w-1", 50.0, 50.0, 4);
        // Worker active tasks == 8 (exceeding hard capacity cap)
        scheduler.recordTelemetry("w-1", 90.0, 90.0, 8, 50.0, 50.0, 4);

        WorkerSnapshot s1 = new WorkerSnapshot(w1, 8, 4096L, 4L * 1024 * 1024 * 1024, 90.0, true);

        ComputeTask task = new ComputeTask();
        task.setTaskId("task-test-3");

        var selected = scheduler.selectWorkerWithThresholds(task, List.of(s1), SchedulingStrategy.LEAST_LOADED);

        // No worker can accept more tasks
        assertThat(selected).isEmpty();
        assertThat(scheduler.isQueueSaturatedActive()).isTrue();
    }

    private WorkerNode createWorker(String id, double targetCpu, double targetRam, int maxTasks) {
        WorkerNode node = new WorkerNode();
        node.setWorkerId(id);
        node.setHostname(id + ".local");
        node.setState(WorkerState.ONLINE);
        node.setActive(true);
        node.setDraining(false);
        node.setTargetCpuPct(targetCpu);
        node.setTargetRamPct(targetRam);
        node.setMaxConcurrentTasks(maxTasks);
        node.setAllocatableCores(4);
        node.setMaxMemoryBytes(8L * 1024 * 1024 * 1024);
        return node;
    }
}
