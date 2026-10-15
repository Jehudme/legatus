package dev.legatus.server.domain;

import java.util.UUID;

public class SecretEntryTestSamples {

    public static SecretEntry getSecretEntrySample1() {
        return new SecretEntry().secretKey("secretKey1");
    }

    public static SecretEntry getSecretEntrySample2() {
        return new SecretEntry().secretKey("secretKey2");
    }

    public static SecretEntry getSecretEntryRandomSampleGenerator() {
        return new SecretEntry().secretKey(UUID.randomUUID().toString());
    }
}
