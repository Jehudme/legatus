package com.legatus.studio.transport;

import com.legatus.studio.protocol.ClusterMetricTickPayload;
import com.legatus.studio.protocol.EnrollmentRequestedPayload;
import com.legatus.studio.protocol.TaskLifecyclePayload;
import com.legatus.studio.protocol.WorkerStateChangedPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class TelemetryCoalescerTest {

    @Test
    void testCoalescingAndFlush() {
        List<TelemetryCoalescer.TelemetryBatch> batches = new CopyOnWriteArrayList<>();

        // Test with 1000ms flush period, manual flush
        try (TelemetryCoalescer coalescer = new TelemetryCoalescer(batches::add, false, 1000)) {
            // Push several events
            ClusterMetricTickPayload tick1 = new ClusterMetricTickPayload(10, 0, 0, 100, 50, 45.0, 100000L, 50000L, 2, 10, 100, 0);
            coalescer.pushClusterMetricTick(tick1);

            WorkerStateChangedPayload w1 = new WorkerStateChangedPayload("wk-1", "ONLINE", "DRAINING", 25.0, 4000L, 1);
            coalescer.pushWorkerStateChanged(w1);

            TaskLifecyclePayload t1 = new TaskLifecyclePayload("task-1", "fib", "COMPLETED", 0, "wk-1", 1000L, 2000L, 50L);
            coalescer.pushTaskLifecycle(t1);

            EnrollmentRequestedPayload e1 = new EnrollmentRequestedPayload("enr-1", "wk-2", "wk2.int", "1.1.1.1", "linux", "x86_64", "AA:BB", "Alpha-Beta-1", 4, 8000L, 123456L);
            coalescer.pushEnrollmentRequested(e1);

            // Manual flush
            coalescer.flush();

            assertEquals(1, batches.size());
            TelemetryCoalescer.TelemetryBatch batch = batches.get(0);
            assertFalse(batch.isEmpty());
            assertEquals(10, batch.latestMetricTick().nodes_online());
            assertEquals(1, batch.workerStateChanges().size());
            assertEquals("DRAINING", batch.workerStateChanges().get("wk-1").current_state());
            assertEquals(1, batch.taskLifecycleEvents().size());
            assertEquals("task-1", batch.taskLifecycleEvents().get(0).task_id());
            assertEquals(1, batch.enrollmentRequests().size());
            assertEquals("enr-1", batch.enrollmentRequests().get(0).enrollment_id());

            // Second flush when empty produces no extra batch
            coalescer.flush();
            assertEquals(1, batches.size());
        }
    }

    @Test
    void testWorkerStateDeduplicationInBatch() {
        List<TelemetryCoalescer.TelemetryBatch> batches = new CopyOnWriteArrayList<>();

        try (TelemetryCoalescer coalescer = new TelemetryCoalescer(batches::add, false, 1000)) {
            // Multiple updates to the same worker within one coalesce window
            WorkerStateChangedPayload w1 = new WorkerStateChangedPayload("wk-1", "ONLINE", "DRAINING", 20.0, 1000L, 2);
            WorkerStateChangedPayload w2 = new WorkerStateChangedPayload("wk-1", "DRAINING", "OFFLINE", 0.0, 0L, 0);

            coalescer.pushWorkerStateChanged(w1);
            coalescer.pushWorkerStateChanged(w2);

            coalescer.flush();

            assertEquals(1, batches.size());
            TelemetryCoalescer.TelemetryBatch batch = batches.get(0);
            assertEquals(1, batch.workerStateChanges().size());
            // Only the latest state is retained for the worker
            assertEquals("OFFLINE", batch.workerStateChanges().get("wk-1").current_state());
        }
    }
}
