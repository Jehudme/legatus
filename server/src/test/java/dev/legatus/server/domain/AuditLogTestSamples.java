package dev.legatus.server.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class AuditLogTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static AuditLog getAuditLogSample1() {
        return new AuditLog()
            .eventId(UUID.fromString("23d8dc04-a48b-45d9-a01d-4b728f0ad4aa"))
            .sequenceNum(1L)
            .actorId("actorId1")
            .actorRole("actorRole1")
            .eventType("eventType1")
            .targetType("targetType1")
            .targetId("targetId1")
            .sourceIp("sourceIp1")
            .prevRecordHash("prevRecordHash1")
            .recordHash("recordHash1")
            .complianceFlag("complianceFlag1");
    }

    public static AuditLog getAuditLogSample2() {
        return new AuditLog()
            .eventId(UUID.fromString("ad79f240-3727-46c3-b89f-2cf6ebd74367"))
            .sequenceNum(2L)
            .actorId("actorId2")
            .actorRole("actorRole2")
            .eventType("eventType2")
            .targetType("targetType2")
            .targetId("targetId2")
            .sourceIp("sourceIp2")
            .prevRecordHash("prevRecordHash2")
            .recordHash("recordHash2")
            .complianceFlag("complianceFlag2");
    }

    public static AuditLog getAuditLogRandomSampleGenerator() {
        return new AuditLog()
            .eventId(UUID.randomUUID())
            .sequenceNum(longCount.incrementAndGet())
            .actorId(UUID.randomUUID().toString())
            .actorRole(UUID.randomUUID().toString())
            .eventType(UUID.randomUUID().toString())
            .targetType(UUID.randomUUID().toString())
            .targetId(UUID.randomUUID().toString())
            .sourceIp(UUID.randomUUID().toString())
            .prevRecordHash(UUID.randomUUID().toString())
            .recordHash(UUID.randomUUID().toString())
            .complianceFlag(UUID.randomUUID().toString());
    }
}
