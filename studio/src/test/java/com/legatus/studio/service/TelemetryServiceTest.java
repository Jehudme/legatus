package com.legatus.studio.service;

import com.legatus.studio.protocol.ClusterMetricTickPayload;
import com.legatus.studio.protocol.EnrollmentRequestedPayload;
import com.legatus.studio.protocol.TaskLifecyclePayload;
import com.legatus.studio.protocol.WorkerStateChangedPayload;
import com.legatus.studio.transport.LstcpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TelemetryServiceTest {

    @Test
    void testTelemetryIngestionAndModelUpdates() {
        LstcpClient mockClient = new LstcpClient("http://127.0.0.1:9999", true);

        try (TelemetryService telemetryService = new TelemetryService(mockClient, false)) {
            // Push metric tick
            ClusterMetricTickPayload tick = new ClusterMetricTickPayload(
                    12, 2, 1, 128, 64, 55.5, 128000000000L, 64000000000L, 5, 20, 500, 3
            );
            telemetryService.getCoalescer().pushClusterMetricTick(tick);

            // Push worker update
            WorkerStateChangedPayload workerUpdate = new WorkerStateChangedPayload(
                    "worker-alpha", "ONLINE", "DRAINING", 33.3, 8000000000L, 4
            );
            telemetryService.getCoalescer().pushWorkerStateChanged(workerUpdate);

            // Push task lifecycle
            TaskLifecyclePayload task = new TaskLifecyclePayload(
                    "task-100", "compute-pi", "EXECUTING", null, "worker-alpha",
                    50000L, 1000000L, 250L
            );
            telemetryService.getCoalescer().pushTaskLifecycle(task);

            // Push enrollment request
            EnrollmentRequestedPayload enr = new EnrollmentRequestedPayload(
                    "enr-999", "worker-beta", "beta.corp", "10.0.0.99", "linux", "x86_64",
                    "11:22:33:44", "Echo-Bravo-01", 16, 32000000000L, 1728909000000L
            );
            telemetryService.getCoalescer().pushEnrollmentRequested(enr);

            // Flush coalescer
            telemetryService.getCoalescer().flush();

            // Verify ClusterMetrics updated
            assertEquals(55.5, telemetryService.getClusterMetrics().getClusterCpuPercent());
            assertEquals(12, telemetryService.getClusterMetrics().getOnlineWorkersCount());
            assertEquals(2, telemetryService.getClusterMetrics().getDrainingWorkersCount());
            assertEquals(5, telemetryService.getClusterMetrics().getQueuedTasksCount());

            // Verify WorkerNode created/updated
            assertEquals(1, telemetryService.getWorkersList().size());
            assertEquals("worker-alpha", telemetryService.getWorkersList().get(0).getWorkerId());
            assertEquals("DRAINING", telemetryService.getWorkersList().get(0).getState());
            assertEquals(33.3, telemetryService.getWorkersList().get(0).getCpuLoadPct());

            // Verify TaskRecord created
            assertEquals(1, telemetryService.getTasksList().size());
            assertEquals("task-100", telemetryService.getTasksList().get(0).getTaskId());
            assertEquals("EXECUTING", telemetryService.getTasksList().get(0).getStatus());

            // Verify QuarantineEnrollment created
            assertEquals(1, telemetryService.getEnrollmentsList().size());
            assertEquals("enr-999", telemetryService.getEnrollmentsList().get(0).getEnrollmentId());
            assertEquals("Echo-Bravo-01", telemetryService.getEnrollmentsList().get(0).getVisualCode());
        }
    }
}
