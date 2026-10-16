package dev.legatus.server.orchestration.balancer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.WorkerNodeRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for ThresholdScheduler verifying RAM saturation math and hysteresis normalization.
 * Complies with MS-012 Section 4.1.
 */
@IntegrationTest
@Transactional
class ThresholdSchedulerIT {

    @Autowired
    private ThresholdScheduler thresholdScheduler;

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    public static WorkerNode createWorker(String workerId, long memoryBytes) {
        return new WorkerNode()
            .workerId(workerId)
            .enrollmentId("enr-" + UUID.randomUUID())
            .hostname("worker-" + workerId)
            .os(OperatingSystem.LINUX)
            .arch(CpuArchitecture.X86_64)
            .agentVersion("1.0.0")
            .ed25519Pubkey("MCowBQYDK2VwAyEA" + UUID.randomUUID().toString().replace("-", ""))
            .fingerprint("4E:8A:BC:31:09:FF:7D:12")
            .visualCode("TIGER-GALAXY-77")
            .state(WorkerState.ONLINE)
            .assignedCluster("production-us-east")
            .sourceIp("127.0.0.1")
            .physicalCores(8)
            .logicalCores(8)
            .allocatableCores(8)
            .totalMemoryBytes(memoryBytes)
            .maxMemoryBytes(memoryBytes)
            .maxInstructionBudget(100_000_000L)
            .gpuPresent(false)
            .draining(false)
            .active(true)
            .targetRamPct(80.0)
            .targetCpuPct(75.0)
            .maxConcurrentTasks(16)
            .createdAt(Instant.now());
    }

    @Test
    @DisplayName("RAM Saturation: 16 GB worker sending 10.5 GB used RAM computes ~65.6% and does NOT breach")
    void testHostMemoryThresholdCalculation() {
        String workerId = "wk-threshold-" + UUID.randomUUID().toString().substring(0, 8);
        long sixteenGb = 16L * 1024 * 1024 * 1024;
        long tenAndHalfGb = (long) (10.5 * 1024 * 1024 * 1024);

        WorkerNode worker = createWorker(workerId, sixteenGb);
        worker = workerNodeRepository.saveAndFlush(worker);

        // Calculate RAM percentage using worker's actual total memory
        double ramPct = ((double) tenAndHalfGb / sixteenGb) * 100.0;
        assertThat(ramPct).isBetween(65.0, 66.0);

        // Record telemetry: 40% CPU, 65.6% RAM, 4 active tasks
        thresholdScheduler.recordTelemetry(
            workerId,
            40.0,
            ramPct,
            4,
            worker.getTargetCpuPct(),
            worker.getTargetRamPct(),
            worker.getMaxConcurrentTasks()
        );

        // Asserts worker is NOT breaching
        assertThat(thresholdScheduler.isBreaching(workerId)).isFalse();
        assertThat(thresholdScheduler.getSmoothedRam(workerId)).isBetween(65.0, 66.0);
    }

    @Test
    @DisplayName("Hysteresis Normalization: Saturation clears when metric recovers below target - hysteresis")
    void testHysteresisRecovery() {
        String workerId = "wk-hysteresis-" + UUID.randomUUID().toString().substring(0, 8);
        double targetRam = 80.0;
        int maxTasks = 16;

        // 1. Send breach load (> target + 5% = 85%) -> should breach
        thresholdScheduler.recordTelemetry(workerId, 50.0, 88.0, 5, 75.0, targetRam, maxTasks);
        assertThat(thresholdScheduler.isBreaching(workerId)).isTrue();

        // 2. Telemetry recovers to 78% (below target 80%, but above target - 5% = 75%) -> must STAY saturated due to hysteresis
        for (int i = 0; i < 5; i++) {
            thresholdScheduler.recordTelemetry(workerId, 50.0, 78.0, 5, 75.0, targetRam, maxTasks);
        }
        assertThat(thresholdScheduler.isBreaching(workerId)).isTrue();

        // 3. Telemetry recovers to 70% (well below target - 5% = 75%) -> saturation clears
        for (int i = 0; i < 15; i++) {
            thresholdScheduler.recordTelemetry(workerId, 50.0, 65.0, 5, 75.0, targetRam, maxTasks);
        }
        assertThat(thresholdScheduler.isBreaching(workerId)).isFalse();
    }
}
