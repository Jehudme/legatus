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
    private final ObservableList<WorkerNode> workersList = FXCollections.observableArrayList(worker -> new javafx.beans.Observable[]{
            worker.friendlyNameProperty(),
            worker.hostnameProperty(),
            worker.stateProperty(),
            worker.cpuLoadPctProperty(),
            worker.memoryUsedBytesProperty(),
            worker.activeTasksCountProperty(),
            worker.remainingLeaseSecProperty(),
            worker.uptimeSecondsProperty(),
            worker.allocatableCoresProperty(),
            worker.maxMemoryBytesProperty(),
            worker.activeProperty()
    });
    private final ObservableList<QuarantineEnrollment> enrollmentsList = FXCollections.observableArrayList();
    private final ObservableList<TaskRecord> tasksList = FXCollections.observableArrayList(task -> new javafx.beans.Observable[]{
            task.nameProperty(),
            task.statusProperty(),
            task.exitCodeProperty(),
            task.assignedWorkerIdProperty(),
            task.instructionsConsumedProperty(),
            task.instructionBudgetProperty(),
            task.peakMemoryBytesProperty(),
            task.executionWallTimeMsProperty(),
            task.timeoutMsProperty(),
            task.startedAtProperty(),
            task.finishedAtProperty(),
            task.workerFriendlyNameProperty(),
            task.profileNameProperty()
    });

    private final TelemetryCoalescer coalescer;
    private final LstcpClient lstcpClient;
    private final com.legatus.studio.transport.LogStreamBuffer logStreamBuffer;
    private final java.util.concurrent.ScheduledExecutorService pulseExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "telemetry-1s-pulse");
        t.setDaemon(true);
        return t;
    });

    public static java.time.Instant parseInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) {
            long val = num.longValue();
            if (val <= 0) return null;
            return val < 10_000_000_000L ? java.time.Instant.ofEpochSecond(val) : java.time.Instant.ofEpochMilli(val);
        }
        String s = obj.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            long val = Long.parseLong(s);
            if (val <= 0) return null;
            return val < 10_000_000_000L ? java.time.Instant.ofEpochSecond(val) : java.time.Instant.ofEpochMilli(val);
        } catch (NumberFormatException ignored) {}
        try {
            return java.time.Instant.parse(s);
        } catch (Exception ignored) {}
        return null;
    }

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

        // Attach listeners from LstcpClient directly into Coalescer ring buffer via multicast methods
        this.lstcpClient.addMetricTickListener(this.coalescer::pushClusterMetricTick);
        this.lstcpClient.addWorkerStateListener(this.coalescer::pushWorkerStateChanged);
        this.lstcpClient.addTaskLifecycleListener(this.coalescer::pushTaskLifecycle);
        this.lstcpClient.addTaskLogChunkListener(chunk -> {
            if (logStreamBuffer != null && chunk != null && chunk.log_chunk() != null) {
                logStreamBuffer.appendLine(String.format("[%s] [TASK %s] %s",
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        chunk.task_id(), chunk.log_chunk()));
            }
        });
        this.lstcpClient.addEnrollmentListener(this.coalescer::pushEnrollmentRequested);
        this.lstcpClient.addRttListener(rtt -> FxAsync.runOnFxThread(() -> clusterMetrics.setRttLatencyMs(rtt)));

        // Continuous 1-second pulse for lease countdown and live running task duration
        this.pulseExecutor.scheduleAtFixedRate(this::onOneSecondPulse, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void onOneSecondPulse() {
        FxAsync.runOnFxThread(() -> {
            // 1. Decrement remaining lease seconds continuously
            for (WorkerNode worker : workersList) {
                long remaining = worker.getRemainingLeaseSec();
                if (remaining > 0) {
                    worker.setRemainingLeaseSec(remaining - 1);
                }
            }

            // 2. Update live wall-clock duration for executing tasks
            java.time.Instant now = java.time.Instant.now();
            for (TaskRecord task : tasksList) {
                String st = task.getStatus();
                if ("EXECUTING".equalsIgnoreCase(st) || "DISPATCHED".equalsIgnoreCase(st)) {
                    java.time.Instant started = parseInstant(task.getStartedAt());
                    if (started == null) {
                        started = parseInstant(task.getCreatedAt());
                    }
                    if (started != null) {
                        long elapsed = java.time.Duration.between(started, now).toMillis();
                        if (elapsed >= 0) {
                            task.setExecutionWallTimeMs(elapsed);
                        }
                    }
                }
            }
        });
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
                if (update.uptime_seconds() != null && update.uptime_seconds() > 0) {
                    existing.setUptimeSeconds(update.uptime_seconds());
                }
                if (update.remaining_lease_sec() != null) {
                    existing.setRemainingLeaseSec(update.remaining_lease_sec());
                }
                if (update.allocatable_cores() != null && update.allocatable_cores() > 0) {
                    existing.setAllocatableCores(update.allocatable_cores());
                }
                if (update.max_memory_bytes() != null && update.max_memory_bytes() > 0) {
                    existing.setMaxMemoryBytes(update.max_memory_bytes());
                }
                if (update.friendly_name() != null && !update.friendly_name().isBlank()) {
                    existing.setFriendlyName(update.friendly_name());
                }
            } else {
                WorkerNode created = new WorkerNode(
                        update.worker_id(),
                        update.friendly_name() != null ? update.friendly_name() : update.worker_id(),
                        update.worker_id(), "127.0.0.1", "linux", "x86_64",
                        update.current_state(), true,
                        update.allocatable_cores() != null ? update.allocatable_cores() : 4,
                        update.max_memory_bytes() != null ? update.max_memory_bytes() : (16L * 1024 * 1024 * 1024),
                        75.0, 80.0, 16, true,
                        update.remaining_lease_sec() != null ? update.remaining_lease_sec() : 86400L,
                        update.uptime_seconds() != null ? update.uptime_seconds() : 0L,
                        update.cpu_load_pct(),
                        update.memory_used_bytes(),
                        update.active_tasks_count(),
                        "", ""
                );
                workersMap.put(update.worker_id(), created);
                workersList.add(0, created);
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
                    if (taskEvent.execution_wall_time_ms() != null && taskEvent.execution_wall_time_ms() > 0) {
                        existing.setExecutionWallTimeMs(taskEvent.execution_wall_time_ms());
                    } else if (("COMPLETED".equalsIgnoreCase(taskEvent.status()) || "FAILED".equalsIgnoreCase(taskEvent.status()) || "ABORTED".equalsIgnoreCase(taskEvent.status()))
                            && existing.getExecutionWallTimeMs() <= 0) {
                        java.time.Instant s = parseInstant(existing.getStartedAt());
                        java.time.Instant f = parseInstant(existing.getFinishedAt());
                        if (f == null) f = java.time.Instant.now();
                        if (s != null) {
                            long dur = java.time.Duration.between(s, f).toMillis();
                            if (dur > 0) existing.setExecutionWallTimeMs(dur);
                        }
                    }
                    if (taskEvent.instruction_budget() != null && taskEvent.instruction_budget() >= 0) {
                        existing.setInstructionBudget(taskEvent.instruction_budget());
                    }
                    if (taskEvent.timeout_ms() != null) {
                        existing.setTimeoutMs(taskEvent.timeout_ms());
                    }
                    if (taskEvent.started_at() != null) {
                        java.time.Instant sInst = parseInstant(taskEvent.started_at());
                        if (sInst != null) existing.setStartedAt(sInst.toString());
                    }
                    if (taskEvent.ended_at() != null) {
                        java.time.Instant eInst = parseInstant(taskEvent.ended_at());
                        if (eInst != null) existing.setFinishedAt(eInst.toString());
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                java.time.Instant sInst = parseInstant(taskEvent.started_at());
                java.time.Instant eInst = parseInstant(taskEvent.ended_at());
                TaskRecord newRecord = new TaskRecord(
                        taskEvent.task_id(), taskEvent.name(), taskEvent.status(), taskEvent.exit_code(),
                        taskEvent.assigned_worker_id(), taskEvent.instructions_consumed(),
                        taskEvent.peak_memory_bytes(),
                        taskEvent.execution_wall_time_ms() != null ? taskEvent.execution_wall_time_ms() : 0L,
                        "",
                        sInst != null ? sInst.toString() : "",
                        eInst != null ? eInst.toString() : "",
                        taskEvent.worker_friendly_name() != null ? taskEvent.worker_friendly_name() : "",
                        taskEvent.profile_name() != null ? taskEvent.profile_name() : ""
                );
                if (taskEvent.instruction_budget() != null && taskEvent.instruction_budget() >= 0) {
                    newRecord.setInstructionBudget(taskEvent.instruction_budget());
                }
                if (taskEvent.timeout_ms() != null) {
                    newRecord.setTimeoutMs(taskEvent.timeout_ms());
                }
                tasksList.add(0, newRecord);
            }
            if (logStreamBuffer != null) {
                logStreamBuffer.appendLine(String.format("[%s] [TASK] Task %s status: %s (Worker: %s)",
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        taskEvent.task_id(), taskEvent.status(),
                        taskEvent.assigned_worker_id() != null ? taskEvent.assigned_worker_id() : "unassigned"));
            }
        }

        // Live task duration streaming for executing tasks
        java.time.Instant nowInstant = java.time.Instant.now();
        for (TaskRecord task : tasksList) {
            String st = task.getStatus();
            if ("EXECUTING".equalsIgnoreCase(st) || "DISPATCHED".equalsIgnoreCase(st)) {
                java.time.Instant started = parseInstant(task.getStartedAt());
                if (started == null) {
                    started = parseInstant(task.getCreatedAt());
                }
                if (started != null) {
                    long elapsed = java.time.Duration.between(started, nowInstant).toMillis();
                    if (elapsed >= 0) {
                        task.setExecutionWallTimeMs(elapsed);
                    }
                }
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
                enrollmentsList.add(0, model);
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
        pulseExecutor.shutdownNow();
        coalescer.close();
    }
}
