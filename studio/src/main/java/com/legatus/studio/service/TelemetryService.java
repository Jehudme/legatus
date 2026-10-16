package com.legatus.studio.service;

import com.legatus.studio.model.ClusterMetrics;
import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.protocol.ClusterMetricTickPayload;
import com.legatus.studio.protocol.EnrollmentRequestedPayload;
import com.legatus.studio.protocol.TaskLifecyclePayload;
import com.legatus.studio.protocol.WorkerStateChangedPayload;
import com.legatus.studio.transport.LstcpClient;
import com.legatus.studio.transport.TelemetryCoalescer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.Objects;

/**
 * Service orchestrating 60Hz telemetry batches between LSTCP and observable JavaFX models.
 */
public class TelemetryService implements AutoCloseable {

    private final ClusterMetrics clusterMetrics = new ClusterMetrics();
    private final ObservableMap<String, WorkerNode> workersMap = FXCollections.observableHashMap();
    private final ObservableList<WorkerNode> workersList = FXCollections.observableArrayList();
    private final ObservableList<QuarantineEnrollment> enrollmentsList = FXCollections.observableArrayList();
    private final ObservableList<TaskRecord> tasksList = FXCollections.observableArrayList();

    private final TelemetryCoalescer coalescer;
    private final LstcpClient lstcpClient;
    private final com.legatus.studio.transport.LogStreamBuffer logStreamBuffer;

    public TelemetryService(LstcpClient lstcpClient) {
        this(lstcpClient, true, null);
    }

    public TelemetryService(LstcpClient lstcpClient, boolean dispatchOnFxThread) {
        this(lstcpClient, dispatchOnFxThread, null);
    }

    public TelemetryService(LstcpClient lstcpClient, boolean dispatchOnFxThread, com.legatus.studio.transport.LogStreamBuffer logStreamBuffer) {
        this.lstcpClient = Objects.requireNonNull(lstcpClient, "lstcpClient must not be null");
        this.logStreamBuffer = logStreamBuffer;
        this.coalescer = new TelemetryCoalescer(this::applyBatch, dispatchOnFxThread, TelemetryCoalescer.COALESCE_INTERVAL_MS);

        // Attach listeners from LstcpClient directly into Coalescer ring buffer
        this.lstcpClient.setMetricTickListener(this.coalescer::pushClusterMetricTick);
        this.lstcpClient.setWorkerStateListener(this.coalescer::pushWorkerStateChanged);
        this.lstcpClient.setTaskLifecycleListener(this.coalescer::pushTaskLifecycle);
        this.lstcpClient.setTaskLogChunkListener(chunk -> {
            if (logStreamBuffer != null && chunk != null && chunk.log_chunk() != null) {
                logStreamBuffer.appendLine(String.format("[%s] [TASK %s] %s",
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        chunk.task_id(), chunk.log_chunk()));
            }
        });
        this.lstcpClient.setEnrollmentListener(this.coalescer::pushEnrollmentRequested);
        this.lstcpClient.setRttListener(rtt -> FxAsync.runOnFxThread(() -> clusterMetrics.setRttLatencyMs(rtt)));
    }

    private void applyBatch(TelemetryCoalescer.TelemetryBatch batch) {
        // 1. Cluster metrics
        ClusterMetricTickPayload tick = batch.latestMetricTick();
        if (tick != null) {
            clusterMetrics.setClusterCpuPercent(tick.cluster_cpu_load_pct());
            clusterMetrics.setClusterUsedMemoryBytes(tick.active_used_memory_bytes());
            clusterMetrics.setClusterTotalMemoryBytes(tick.total_allocatable_memory_bytes());
            clusterMetrics.setOnlineWorkersCount(tick.nodes_online());
            clusterMetrics.setDrainingWorkersCount(tick.nodes_draining());
            clusterMetrics.setOfflineWorkersCount(tick.nodes_offline());
            clusterMetrics.setQueuedTasksCount(tick.tasks_queued());
            clusterMetrics.setExecutingTasksCount(tick.tasks_executing());
            clusterMetrics.setTasksCompletedLast60s(tick.tasks_completed_last_60s());
            clusterMetrics.setTasksFailedLast60s(tick.tasks_failed_last_60s());
            if (tick.threshold_breach_warning() != null) {
                clusterMetrics.setThresholdBreachWarning(tick.threshold_breach_warning());
            }
            if (tick.queue_saturated_error() != null) {
                clusterMetrics.setQueueSaturatedError(tick.queue_saturated_error());
            }
            if (tick.breached_worker_ids() != null) {
                clusterMetrics.setBreachedWorkerIds(tick.breached_worker_ids());
            }
        }

        // 2. Worker updates
        batch.workerStateChanges().values().forEach(update -> {
            WorkerNode existing = workersMap.get(update.worker_id());
            if (existing != null) {
                existing.setState(update.current_state());
                existing.setCpuLoadPct(update.cpu_load_pct());
                existing.setMemoryUsedBytes(update.memory_used_bytes());
                existing.setActiveTasksCount(update.active_tasks_count());
            } else {
                WorkerNode created = new WorkerNode(
                        update.worker_id(), update.worker_id(), "", "linux", "x86_64",
                        update.current_state(), 0, 0, update.cpu_load_pct(),
                        update.memory_used_bytes(), update.active_tasks_count(), ""
                );
                workersMap.put(update.worker_id(), created);
                workersList.add(created);
            }
            if (logStreamBuffer != null) {
                logStreamBuffer.appendLine(String.format("[%s] [WORKER] Node %s -> %s (CPU: %.1f%%, Memory: %d MB, Tasks: %d)",
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        update.worker_id(), update.current_state(), update.cpu_load_pct(),
                        update.memory_used_bytes() / (1024 * 1024), update.active_tasks_count()));
            }
        });

        // 3. Task lifecycle events
        for (TaskLifecyclePayload taskEvent : batch.taskLifecycleEvents()) {
            boolean found = false;
            for (TaskRecord existing : tasksList) {
                if (existing.getTaskId().equals(taskEvent.task_id())) {
                    existing.setStatus(taskEvent.status());
                    if (taskEvent.name() != null) existing.setName(taskEvent.name());
                    if (taskEvent.exit_code() != null) existing.setExitCode(taskEvent.exit_code());
                    if (taskEvent.assigned_worker_id() != null) existing.setAssignedWorkerId(taskEvent.assigned_worker_id());
                    if (taskEvent.worker_friendly_name() != null) existing.setWorkerFriendlyName(taskEvent.worker_friendly_name());
                    if (taskEvent.profile_name() != null) existing.setProfileName(taskEvent.profile_name());
                    if (taskEvent.instructions_consumed() != null) existing.setInstructionsConsumed(taskEvent.instructions_consumed());
                    if (taskEvent.peak_memory_bytes() != null) existing.setPeakMemoryBytes(taskEvent.peak_memory_bytes());
                    if (taskEvent.execution_wall_time_ms() != null) existing.setExecutionWallTimeMs(taskEvent.execution_wall_time_ms());
                    if (taskEvent.started_at() != null) existing.setStartedAt(taskEvent.started_at().toString());
                    if (taskEvent.ended_at() != null) existing.setFinishedAt(taskEvent.ended_at().toString());
                    found = true;
                    break;
                }
            }
            if (!found) {
                TaskRecord newRecord = new TaskRecord(
                        taskEvent.task_id(), taskEvent.name(), taskEvent.status(), taskEvent.exit_code(),
                        taskEvent.assigned_worker_id(), taskEvent.instructions_consumed(),
                        taskEvent.peak_memory_bytes(), taskEvent.execution_wall_time_ms(),
                        "",
                        taskEvent.started_at() != null ? taskEvent.started_at().toString() : "",
                        taskEvent.ended_at() != null ? taskEvent.ended_at().toString() : "",
                        taskEvent.worker_friendly_name() != null ? taskEvent.worker_friendly_name() : "",
                        taskEvent.profile_name() != null ? taskEvent.profile_name() : ""
                );
                tasksList.add(newRecord);
            }
            if (logStreamBuffer != null) {
                logStreamBuffer.appendLine(String.format("[%s] [TASK] Task %s status: %s (Worker: %s)",
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        taskEvent.task_id(), taskEvent.status(),
                        taskEvent.assigned_worker_id() != null ? taskEvent.assigned_worker_id() : "unassigned"));
            }
        }

        // Live task duration streaming for executing tasks
        for (TaskRecord task : tasksList) {
            if ("EXECUTING".equalsIgnoreCase(task.getStatus())) {
                task.setExecutionWallTimeMs(task.getExecutionWallTimeMs() + TelemetryCoalescer.COALESCE_INTERVAL_MS);
            }
        }

        // 4. Quarantine enrollments
        for (EnrollmentRequestedPayload enr : batch.enrollmentRequests()) {
            boolean exists = enrollmentsList.stream().anyMatch(e -> e.getEnrollmentId().equals(enr.enrollment_id()));
            if (!exists) {
                QuarantineEnrollment model = new QuarantineEnrollment(
                        enr.enrollment_id(), enr.worker_id(), enr.hostname(), enr.source_ip(),
                        enr.os(), enr.architecture(), enr.fingerprint_hex(), enr.visual_code(),
                        enr.allocatable_cores(), enr.max_memory_bytes(), String.valueOf(enr.expires_at())
                );
                enrollmentsList.add(model);
            }
            if (logStreamBuffer != null) {
                logStreamBuffer.appendLine(String.format("[%s] [QUARANTINE] Worker enrollment requested: %s (%s, IP: %s)",
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        enr.worker_id(), enr.visual_code(), enr.source_ip()));
            }
        }
    }

    public ClusterMetrics getClusterMetrics() { return clusterMetrics; }
    public ObservableList<WorkerNode> getWorkersList() { return workersList; }
    public ObservableMap<String, WorkerNode> getWorkersMap() { return workersMap; }
    public ObservableList<QuarantineEnrollment> getEnrollmentsList() { return enrollmentsList; }
    public ObservableList<TaskRecord> getTasksList() { return tasksList; }
    public TelemetryCoalescer getCoalescer() { return coalescer; }

    @Override
    public void close() {
        coalescer.close();
    }
}
