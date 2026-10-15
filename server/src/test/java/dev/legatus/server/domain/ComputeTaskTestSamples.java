package dev.legatus.server.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ComputeTaskTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static ComputeTask getComputeTaskSample1() {
        return new ComputeTask()
            .taskId("taskId1")
            .name("name1")
            .priorityWeight(1)
            .entryPoint("entryPoint1")
            .bytecodeSha256("bytecodeSha2561")
            .instructionBudget(1L)
            .maxMemoryBytes(1L)
            .timeoutMs(1)
            .exitCode(1)
            .instructionsConsumed(1L)
            .peakMemoryBytes(1L)
            .wallTimeMs(1L)
            .attemptCount(1)
            .maxRetries(1);
    }

    public static ComputeTask getComputeTaskSample2() {
        return new ComputeTask()
            .taskId("taskId2")
            .name("name2")
            .priorityWeight(2)
            .entryPoint("entryPoint2")
            .bytecodeSha256("bytecodeSha2562")
            .instructionBudget(2L)
            .maxMemoryBytes(2L)
            .timeoutMs(2)
            .exitCode(2)
            .instructionsConsumed(2L)
            .peakMemoryBytes(2L)
            .wallTimeMs(2L)
            .attemptCount(2)
            .maxRetries(2);
    }

    public static ComputeTask getComputeTaskRandomSampleGenerator() {
        return new ComputeTask()
            .taskId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .priorityWeight(intCount.incrementAndGet())
            .entryPoint(UUID.randomUUID().toString())
            .bytecodeSha256(UUID.randomUUID().toString())
            .instructionBudget(longCount.incrementAndGet())
            .maxMemoryBytes(longCount.incrementAndGet())
            .timeoutMs(intCount.incrementAndGet())
            .exitCode(intCount.incrementAndGet())
            .instructionsConsumed(longCount.incrementAndGet())
            .peakMemoryBytes(longCount.incrementAndGet())
            .wallTimeMs(longCount.incrementAndGet())
            .attemptCount(intCount.incrementAndGet())
            .maxRetries(intCount.incrementAndGet());
    }
}
