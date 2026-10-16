package dev.legatus.server.orchestration.balancer;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.service.scheduler.SchedulingStrategy;
import dev.legatus.server.service.scheduler.WorkerSnapshot;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Server-Centric Threshold-Aware Auto-Balancing Scheduler Engine with EMA Smoothing and Hysteresis.
 * <p>
 * Complies with {@code ADR-0010} and {@code MS-007} Section 3.2 [P1]:
 * <ul>
 *   <li>Enforces soft per-worker thresholds: Target CPU %, Target RAM %, Max Concurrent Functions.</li>
 *   <li>Calculates Exponential Moving Average (EMA, alpha=0.3) over consecutive telemetry ticks.</li>
 *   <li>Applies +/-5% hysteresis window to prevent rapid scheduler oscillation.</li>
 *   <li>Prioritizes workers operating within configured headroom thresholds.</li>
 *   <li>Emits THRESHOLD_BREACH_WARNING if all available workers operate above thresholds.</li>
 *   <li>Emits CLUSTER_QUEUE_SATURATED_ERROR when total capacity is exhausted and queueing is unavoidable.</li>
 * </ul>
 */
@Component
public class ThresholdScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ThresholdScheduler.class);

    public static final double EMA_ALPHA = 0.3;
    public static final double HYSTERESIS_PCT = 5.0;

    public record WorkerMetricsState(
        String workerId,
        double smoothedCpuPct,
        double smoothedRamPct,
        int activeTasksCount,
        boolean saturated,
        long lastUpdateTimestamp
    ) {}

    private final Map<String, WorkerMetricsState> metricsStateMap = new ConcurrentHashMap<>();
    private final AtomicBoolean thresholdBreachWarning = new AtomicBoolean(false);
    private final AtomicBoolean queueSaturatedError = new AtomicBoolean(false);

    /**
     * Records fresh telemetry for a worker and computes EMA smoothing with hysteresis.
     *
     * @param workerId worker node identifier
     * @param rawCpuPct latest sampled CPU percentage [0.0, 100.0]
     * @param rawRamPct latest sampled RAM percentage [0.0, 100.0]
     * @param concurrentTasks active executing task count
     * @param targetCpuPct configured target CPU threshold
     * @param targetRamPct configured target RAM threshold
     * @param maxConcurrentTasks configured maximum concurrent tasks threshold
     */
    public synchronized void recordTelemetry(
        String workerId,
        double rawCpuPct,
        double rawRamPct,
        int concurrentTasks,
        double targetCpuPct,
        double targetRamPct,
        int maxConcurrentTasks
    ) {
        if (workerId == null) return;

        WorkerMetricsState prev = metricsStateMap.get(workerId);
        double smoothedCpu;
        double smoothedRam;

        if (prev == null) {
            smoothedCpu = rawCpuPct;
            smoothedRam = rawRamPct;
        } else {
            smoothedCpu = (EMA_ALPHA * rawCpuPct) + ((1.0 - EMA_ALPHA) * prev.smoothedCpuPct());
            smoothedRam = (EMA_ALPHA * rawRamPct) + ((1.0 - EMA_ALPHA) * prev.smoothedRamPct());
        }

        boolean prevSaturated = prev != null && prev.saturated();
        boolean newSaturated;

        if (prevSaturated) {
            // Must recover below (target - hysteresis) to clear saturation
            boolean cpuCleared = smoothedCpu < (targetCpuPct - HYSTERESIS_PCT);
            boolean ramCleared = smoothedRam < (targetRamPct - HYSTERESIS_PCT);
            boolean tasksCleared = concurrentTasks < maxConcurrentTasks;
            newSaturated = !(cpuCleared && ramCleared && tasksCleared);
        } else {
            // Saturated if any metric exceeds (target + hysteresis) or concurrent tasks reached
            boolean cpuBreached = smoothedCpu > (targetCpuPct + HYSTERESIS_PCT);
            boolean ramBreached = smoothedRam > (targetRamPct + HYSTERESIS_PCT);
            boolean tasksBreached = concurrentTasks >= maxConcurrentTasks;
            newSaturated = cpuBreached || ramBreached || tasksBreached;
        }

        WorkerMetricsState updated = new WorkerMetricsState(
            workerId,
            smoothedCpu,
            smoothedRam,
            concurrentTasks,
            newSaturated,
            System.currentTimeMillis()
        );
        metricsStateMap.put(workerId, updated);
    }

    /**
     * Checks if a specific worker is currently in a threshold breach/saturation state.
     */
    public boolean isBreaching(String workerId) {
        WorkerMetricsState state = metricsStateMap.get(workerId);
        return state != null && state.saturated();
    }

    public double getSmoothedCpu(String workerId) {
        WorkerMetricsState state = metricsStateMap.get(workerId);
        return state != null ? state.smoothedCpuPct() : 0.0;
    }

    public double getSmoothedRam(String workerId) {
        WorkerMetricsState state = metricsStateMap.get(workerId);
        return state != null ? state.smoothedRamPct() : 0.0;
    }

    public List<String> getBreachedWorkerIds() {
        return metricsStateMap.values().stream()
            .filter(WorkerMetricsState::saturated)
            .map(WorkerMetricsState::workerId)
            .toList();
    }

    public boolean isThresholdBreachActive() {
        return thresholdBreachWarning.get();
    }

    public void setThresholdBreachActive(boolean active) {
        thresholdBreachWarning.set(active);
    }

    public boolean isQueueSaturatedActive() {
        return queueSaturatedError.get();
    }

    public void setQueueSaturatedActive(boolean active) {
        queueSaturatedError.set(active);
    }

    public void clearWorker(String workerId) {
        if (workerId != null) {
            metricsStateMap.remove(workerId);
        }
    }

    /**
     * Selects an eligible worker prioritizing nodes with headroom below thresholds.
     * <p>
     * 1. Filters candidate snapshots for active, online workers.
     * 2. Categorizes eligible nodes into Headroom Workers (below threshold) and Breached Workers (above threshold).
     * 3. If Headroom Workers exist, dispatches to the least loaded headroom worker.
     * 4. If all workers are above threshold but still have physical capacity, flags THRESHOLD_BREACH_WARNING and dispatches to the least loaded worker.
     * 5. If cluster capacity is completely exhausted, flags CLUSTER_QUEUE_SATURATED_ERROR and returns empty (queueing as last resort).
     */
    public Optional<WorkerNode> selectWorkerWithThresholds(
        ComputeTask candidateTask,
        List<WorkerSnapshot> candidateSnapshots,
        SchedulingStrategy strategy
    ) {
        if (candidateTask == null || candidateSnapshots == null || candidateSnapshots.isEmpty()) {
            queueSaturatedError.set(true);
            return Optional.empty();
        }

        // Active filter: worker must be active and not draining
        List<WorkerSnapshot> activeEligible = candidateSnapshots.stream()
            .filter(s -> {
                WorkerNode w = s.worker();
                return w != null &&
                    Boolean.TRUE.equals(w.getActive()) &&
                    !Boolean.TRUE.equals(w.getDraining());
            })
            .toList();

        if (activeEligible.isEmpty()) {
            LOG.debug("No active, non-draining workers available for task {}", candidateTask.getTaskId());
            queueSaturatedError.set(true);
            return Optional.empty();
        }

        // Partition into headroom vs breached
        List<WorkerSnapshot> headroomWorkers = new ArrayList<>();
        List<WorkerSnapshot> breachedWorkers = new ArrayList<>();

        for (WorkerSnapshot snapshot : activeEligible) {
            WorkerNode worker = snapshot.worker();
            String wid = worker.getWorkerId();
            boolean breached = isBreaching(wid);

            // Also check current active task count against max concurrent tasks
            int maxConcurrent = worker.getMaxConcurrentTasks() != null ? worker.getMaxConcurrentTasks() : 16;
            if (snapshot.activeTaskCount() >= maxConcurrent) {
                breached = true;
            }

            if (breached) {
                breachedWorkers.add(snapshot);
            } else {
                headroomWorkers.add(snapshot);
            }
        }

        // 1. Preferred path: dispatch to headroom worker
        if (!headroomWorkers.isEmpty()) {
            thresholdBreachWarning.set(false);
            queueSaturatedError.set(false);
            return selectByStrategy(headroomWorkers, strategy, candidateTask);
        }

        // 2. Secondary path: dispatch to breached worker if capacity headroom permits
        List<WorkerSnapshot> stillDispatchable = breachedWorkers.stream()
            .filter(s -> {
                WorkerNode w = s.worker();
                int allocCores = w.getAllocatableCores() != null ? w.getAllocatableCores() : 1;
                // Hard limit: allow over-threshold dispatch up to 2x allocatable cores or max configured limit
                int hardTaskCap = Math.max(allocCores * 2, w.getMaxConcurrentTasks() != null ? w.getMaxConcurrentTasks() * 2 : 32);
                return s.activeTaskCount() < hardTaskCap;
            })
            .toList();

        if (!stillDispatchable.isEmpty()) {
            LOG.warn("Auto-balancer threshold breach: all {} active workers operating above thresholds. Dispatching to least-loaded.",
                activeEligible.size());
            thresholdBreachWarning.set(true);
            queueSaturatedError.set(false);
            return selectByStrategy(stillDispatchable, strategy, candidateTask);
        }

        // 3. Absolute last resort: cluster saturated, queue the task
        LOG.error("Cluster queue saturated error: all workers operating at absolute capacity. Queueing task {}",
            candidateTask.getTaskId());
        thresholdBreachWarning.set(true);
        queueSaturatedError.set(true);
        return Optional.empty();
    }

    private Optional<WorkerNode> selectByStrategy(
        List<WorkerSnapshot> snapshots,
        SchedulingStrategy strategy,
        ComputeTask task
    ) {
        if (snapshots.isEmpty()) return Optional.empty();
        if (snapshots.size() == 1) return Optional.of(snapshots.get(0).worker());

        // Default to least loaded (based on active tasks and smoothed CPU)
        return snapshots.stream()
            .min(Comparator.comparingDouble((WorkerSnapshot s) -> {
                double cpu = getSmoothedCpu(s.worker().getWorkerId());
                return (double) s.activeTaskCount() + 0.5 * (cpu / 100.0);
            }).thenComparing(s -> s.worker().getWorkerId()))
            .map(WorkerSnapshot::worker);
    }
}
