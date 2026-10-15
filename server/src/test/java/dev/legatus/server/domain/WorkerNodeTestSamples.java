package dev.legatus.server.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WorkerNodeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static WorkerNode getWorkerNodeSample1() {
        return new WorkerNode()
            .workerId("workerId1")
            .enrollmentId("enrollmentId1")
            .hostname("hostname1")
            .agentVersion("agentVersion1")
            .ed25519Pubkey("ed25519Pubkey1")
            .fingerprint("fingerprint1")
            .visualCode("visualCode1")
            .secretHash("secretHash1")
            .assignedCluster("assignedCluster1")
            .sourceIp("sourceIp1")
            .physicalCores(1)
            .logicalCores(1)
            .totalMemoryBytes(1L)
            .gpuModel("gpuModel1")
            .allocatableCores(1)
            .maxMemoryBytes(1L)
            .maxInstructionBudget(1L)
            .activeSessionId("activeSessionId1");
    }

    public static WorkerNode getWorkerNodeSample2() {
        return new WorkerNode()
            .workerId("workerId2")
            .enrollmentId("enrollmentId2")
            .hostname("hostname2")
            .agentVersion("agentVersion2")
            .ed25519Pubkey("ed25519Pubkey2")
            .fingerprint("fingerprint2")
            .visualCode("visualCode2")
            .secretHash("secretHash2")
            .assignedCluster("assignedCluster2")
            .sourceIp("sourceIp2")
            .physicalCores(2)
            .logicalCores(2)
            .totalMemoryBytes(2L)
            .gpuModel("gpuModel2")
            .allocatableCores(2)
            .maxMemoryBytes(2L)
            .maxInstructionBudget(2L)
            .activeSessionId("activeSessionId2");
    }

    public static WorkerNode getWorkerNodeRandomSampleGenerator() {
        return new WorkerNode()
            .workerId(UUID.randomUUID().toString())
            .enrollmentId(UUID.randomUUID().toString())
            .hostname(UUID.randomUUID().toString())
            .agentVersion(UUID.randomUUID().toString())
            .ed25519Pubkey(UUID.randomUUID().toString())
            .fingerprint(UUID.randomUUID().toString())
            .visualCode(UUID.randomUUID().toString())
            .secretHash(UUID.randomUUID().toString())
            .assignedCluster(UUID.randomUUID().toString())
            .sourceIp(UUID.randomUUID().toString())
            .physicalCores(intCount.incrementAndGet())
            .logicalCores(intCount.incrementAndGet())
            .totalMemoryBytes(longCount.incrementAndGet())
            .gpuModel(UUID.randomUUID().toString())
            .allocatableCores(intCount.incrementAndGet())
            .maxMemoryBytes(longCount.incrementAndGet())
            .maxInstructionBudget(longCount.incrementAndGet())
            .activeSessionId(UUID.randomUUID().toString());
    }
}
