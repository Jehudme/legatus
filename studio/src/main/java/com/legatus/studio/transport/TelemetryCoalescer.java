package com.legatus.studio.transport;

import com.legatus.studio.protocol.ClusterMetricTickPayload;
import com.legatus.studio.protocol.EnrollmentRequestedPayload;
import com.legatus.studio.protocol.TaskLifecyclePayload;
import com.legatus.studio.protocol.WorkerStateChangedPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 60Hz (16.6ms) Lock-Free Telemetry Coalescing Buffer (Issue #401, GL-0011).
 * Buffers incoming high-frequency telemetry events from LSTCP and batches updates
 * to the JavaFX Application Thread at most once every 16.6ms to prevent UI thread starvation.
 */
public class TelemetryCoalescer implements AutoCloseable {

    public static final long COALESCE_INTERVAL_MS = 16; // ~60Hz

    public record TelemetryBatch(
            ClusterMetricTickPayload latestMetricTick,
            Map<String, WorkerStateChangedPayload> workerStateChanges,
            List<TaskLifecyclePayload> taskLifecycleEvents,
            List<EnrollmentRequestedPayload> enrollmentRequests
    ) {
        public boolean isEmpty() {
            return latestMetricTick == null &&
                    workerStateChanges.isEmpty() &&
                    taskLifecycleEvents.isEmpty() &&
                    enrollmentRequests.isEmpty();
        }
    }

    private final AtomicReference<ClusterMetricTickPayload> latestMetricTick = new AtomicReference<>();
    private final ConcurrentHashMap<String, WorkerStateChangedPayload> workerStateChanges = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<TaskLifecyclePayload> taskLifecycleEvents = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EnrollmentRequestedPayload> enrollmentRequests = new ConcurrentLinkedQueue<>();

    private final ScheduledExecutorService scheduler;
    private final Consumer<TelemetryBatch> batchConsumer;
    private final boolean dispatchOnFxThread;

    public TelemetryCoalescer(Consumer<TelemetryBatch> batchConsumer) {
        this(batchConsumer, false, COALESCE_INTERVAL_MS);
    }

    public TelemetryCoalescer(Consumer<TelemetryBatch> batchConsumer, boolean dispatchOnFxThread, long flushIntervalMs) {
        this.batchConsumer = batchConsumer;
        this.dispatchOnFxThread = dispatchOnFxThread;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "telemetry-coalescer");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void pushClusterMetricTick(ClusterMetricTickPayload tick) {
        latestMetricTick.set(tick);
    }

    public void pushWorkerStateChanged(WorkerStateChangedPayload payload) {
        if (payload != null && payload.worker_id() != null) {
            workerStateChanges.put(payload.worker_id(), payload);
        }
    }

    public void pushTaskLifecycle(TaskLifecyclePayload payload) {
        if (payload != null) {
            taskLifecycleEvents.add(payload);
        }
    }

    public void pushEnrollmentRequested(EnrollmentRequestedPayload payload) {
        if (payload != null) {
            enrollmentRequests.add(payload);
        }
    }

    /**
     * Drains pending updates and dispatches them as an immutable batch.
     */
    public void flush() {
        ClusterMetricTickPayload tick = latestMetricTick.getAndSet(null);

        Map<String, WorkerStateChangedPayload> workers = new HashMap<>();
        if (!workerStateChanges.isEmpty()) {
            workerStateChanges.forEach((k, v) -> workers.put(k, v));
            workerStateChanges.clear();
        }

        List<TaskLifecyclePayload> tasks = new ArrayList<>();
        TaskLifecyclePayload taskItem;
        while ((taskItem = taskLifecycleEvents.poll()) != null) {
            tasks.add(taskItem);
        }

        List<EnrollmentRequestedPayload> enrollments = new ArrayList<>();
        EnrollmentRequestedPayload enrItem;
        while ((enrItem = enrollmentRequests.poll()) != null) {
            enrollments.add(enrItem);
        }

        TelemetryBatch batch = new TelemetryBatch(tick, workers, tasks, enrollments);
        if (batch.isEmpty() || batchConsumer == null) {
            return;
        }

        if (dispatchOnFxThread) {
            try {
                javafx.application.Platform.runLater(() -> batchConsumer.accept(batch));
            } catch (IllegalStateException ignored) {
                // Toolkit not initialized (e.g. headless unit tests)
                batchConsumer.accept(batch);
            }
        } else {
            batchConsumer.accept(batch);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        flush();
    }
}
