package dev.legatus.server.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SolixFunctionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static SolixFunction getSolixFunctionSample1() {
        return new SolixFunction()
            .functionId(UUID.fromString("23d8dc04-a48b-45d9-a01d-4b728f0ad4aa"))
            .name("name1")
            .semver("semver1")
            .entryPoint("entryPoint1")
            .bytecodeFormat("bytecodeFormat1")
            .bytecodeSha256("bytecodeSha2561")
            .defaultBudget(1L)
            .defaultMemoryBytes(1L)
            .defaultTimeoutMs(1);
    }

    public static SolixFunction getSolixFunctionSample2() {
        return new SolixFunction()
            .functionId(UUID.fromString("ad79f240-3727-46c3-b89f-2cf6ebd74367"))
            .name("name2")
            .semver("semver2")
            .entryPoint("entryPoint2")
            .bytecodeFormat("bytecodeFormat2")
            .bytecodeSha256("bytecodeSha2562")
            .defaultBudget(2L)
            .defaultMemoryBytes(2L)
            .defaultTimeoutMs(2);
    }

    public static SolixFunction getSolixFunctionRandomSampleGenerator() {
        return new SolixFunction()
            .functionId(UUID.randomUUID())
            .name(UUID.randomUUID().toString())
            .semver(UUID.randomUUID().toString())
            .entryPoint(UUID.randomUUID().toString())
            .bytecodeFormat(UUID.randomUUID().toString())
            .bytecodeSha256(UUID.randomUUID().toString())
            .defaultBudget(longCount.incrementAndGet())
            .defaultMemoryBytes(longCount.incrementAndGet())
            .defaultTimeoutMs(intCount.incrementAndGet());
    }
}
