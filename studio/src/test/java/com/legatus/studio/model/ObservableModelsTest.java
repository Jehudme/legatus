package com.legatus.studio.model;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ObservableModelsTest {

    @Test
    void testClusterMetricsProperties() {
        ClusterMetrics metrics = new ClusterMetrics();
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        metrics.clusterCpuPercentProperty().addListener((obs, oldV, newV) -> listenerFired.set(true));

        metrics.setClusterCpuPercent(78.5);
        assertTrue(listenerFired.get());
        assertEquals(78.5, metrics.getClusterCpuPercent());

        metrics.setOnlineWorkersCount(10);
        assertEquals(10, metrics.getOnlineWorkersCount());

        metrics.setExecutingTasksCount(42);
        assertEquals(42, metrics.getExecutingTasksCount());
    }

    @Test
    void testWorkerNodeAndRollingHistory() {
        WorkerNode worker = new WorkerNode(
                "w-01", "worker-01.internal", "192.168.1.10", "linux", "x86_64",
                "ONLINE", 16, 32000000000L, 10.0, 4000000000L, 2, "2026-10-15T15:00:00Z"
        );

        assertEquals("w-01", worker.getWorkerId());
        assertEquals("ONLINE", worker.getState());
        assertEquals(1, worker.getCpuHistory().size());

        // Push 70 points to test max 60-point cap
        for (int i = 0; i < 70; i++) {
            worker.setCpuLoadPct(i);
        }
        assertEquals(60, worker.getCpuHistory().size());
        assertEquals(69.0, worker.getCpuHistory().get(59));
    }

    @Test
    void testQuarantineEnrollmentModel() {
        QuarantineEnrollment enr = new QuarantineEnrollment(
                "enr-01", "w-quarantine", "quar.node", "10.0.0.1", "linux", "arm64",
                "4E:8A:BC:31:09:FF:7D:12", "Tiger-Galaxy-77", 8, 16000000000L, "1728908100000"
        );

        assertEquals("enr-01", enr.getEnrollmentId());
        assertEquals("Tiger-Galaxy-77", enr.getVisualCode());
        assertEquals("4E:8A:BC:31:09:FF:7D:12", enr.getFingerprintHex());
    }

    @Test
    void testTaskRecordModel() {
        TaskRecord task = new TaskRecord(
                "task-01", "matrix-mult", "EXECUTING", null, "w-01",
                500000L, 8000000L, 3420L, "2026-10-15T15:00:00Z",
                "1774886400000", "1774886403420", "worker-alpha", "perf-profile"
        );

        assertEquals("task-01", task.getTaskId());
        assertEquals("matrix-mult", task.getName());
        assertEquals("matrix-mult", task.getFunctionName());
        assertEquals("EXECUTING", task.getStatus());
        assertEquals(500000L, task.getInstructionsConsumed());
        assertEquals("worker-alpha", task.getWorkerFriendlyName());
        assertEquals("perf-profile", task.getProfileName());
        assertEquals("3.42s", task.getFormattedDuration());
        assertNotNull(task.getFormattedStartTime());
        assertEquals("--", task.getFormattedEndTime()); // Executing tasks have '--' end time

        task.setTimeoutMs(30_000L);
        assertEquals(30_000L, task.getTimeoutMs());

        task.setStatus("COMPLETED");
        task.setExitCode(0);
        assertEquals("COMPLETED", task.getStatus());
        assertEquals(0, task.getExitCode());
        assertNotEquals("--", task.getFormattedEndTime());
    }

    @Test
    void testBytecodeSecretAccountAndAuditModels() {
        BytecodeItem b = new BytecodeItem("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "main", "main", 1024L, "2026-10-15T15:00:00Z");
        assertEquals("main", b.getFunctionName());

        SecretItem s = new SecretItem("DB_PASSWORD", "DATABASE", "2026-10-15T15:00:00Z", "2026-10-15T15:00:00Z");
        assertEquals("DB_PASSWORD", s.getKey());

        OperatorAccount a = new OperatorAccount("acc-1", "admin", "admin@legatus.internal", "CLUSTER_ADMIN", "ACTIVE", "2026-10-15T15:00:00Z", "");
        assertEquals("CLUSTER_ADMIN", a.getRole());

        AuditLogEntry audit = new AuditLogEntry(1L, "ev-1", "2026-10-15T15:00:00Z", "admin", "LOGIN", "AUTH", "acc-1", "SUCCESS", "hash1", "genesis");
        assertEquals(1L, audit.getSequenceNumber());
        assertEquals("SUCCESS", audit.getStatus());
    }
}
