package dev.legatus.server.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class AccountTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Account getAccountSample1() {
        return new Account()
            .accountId(UUID.fromString("23d8dc04-a48b-45d9-a01d-4b728f0ad4aa"))
            .username("username1")
            .email("email1")
            .passwordHash("passwordHash1")
            .failedLoginAttempts(1);
    }

    public static Account getAccountSample2() {
        return new Account()
            .accountId(UUID.fromString("ad79f240-3727-46c3-b89f-2cf6ebd74367"))
            .username("username2")
            .email("email2")
            .passwordHash("passwordHash2")
            .failedLoginAttempts(2);
    }

    public static Account getAccountRandomSampleGenerator() {
        return new Account()
            .accountId(UUID.randomUUID())
            .username(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .passwordHash(UUID.randomUUID().toString())
            .failedLoginAttempts(intCount.incrementAndGet());
    }
}
