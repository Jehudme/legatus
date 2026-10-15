package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast isolated unit tests for {@link WormAuditService}.
 */
@ExtendWith(MockitoExtension.class)
class WormAuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private WormAuditService wormAuditService;

    @BeforeEach
    void setUp() {
        wormAuditService = new WormAuditService(auditLogRepository);
    }

    @Test
    @DisplayName("Should initialize Genesis record with 64 zeros as prevRecordHash")
    void testGenesisRecordCreation() {
        when(auditLogRepository.findTopByOrderBySequenceNumDesc()).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog log = wormAuditService.recordEvent(
            "operator-1",
            "CLUSTER_ADMIN",
            "LOGIN",
            "ACCOUNT",
            "acc-1",
            AuditActionStatus.SUCCESS,
            "{\"ip\":\"127.0.0.1\"}",
            "127.0.0.1",
            "SOC2"
        );

        assertThat(log.getSequenceNum()).isEqualTo(1L);
        assertThat(log.getPrevRecordHash()).isEqualTo(WormAuditService.GENESIS_PREV_HASH);
        assertThat(log.getRecordHash()).hasSize(64);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should increment sequence and chain previous hash for subsequent records")
    void testSubsequentRecordChaining() {
        AuditLog previous = new AuditLog();
        previous.setSequenceNum(41L);
        previous.setRecordHash("a".repeat(64));

        when(auditLogRepository.findTopByOrderBySequenceNumDesc()).thenReturn(Optional.of(previous));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog log = wormAuditService.recordEvent(
            "worker-1",
            "WORKER_NODE",
            "HEARTBEAT",
            "WORKER",
            "wk-1",
            AuditActionStatus.SUCCESS,
            null,
            "10.0.0.1",
            null
        );

        assertThat(log.getSequenceNum()).isEqualTo(42L);
        assertThat(log.getPrevRecordHash()).isEqualTo("a".repeat(64));
        assertThat(log.getRecordHash()).hasSize(64);
    }

    @Test
    @DisplayName("Should compute deterministic SHA-256 hash")
    void testComputeRecordHashDeterminism() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String hash1 = wormAuditService.computeRecordHash(
            1L,
            WormAuditService.GENESIS_PREV_HASH,
            now,
            "admin",
            "CLUSTER_ADMIN",
            "CONFIG_CHANGE",
            "SERVER",
            "srv-1",
            AuditActionStatus.SUCCESS,
            "{}",
            "127.0.0.1"
        );

        String hash2 = wormAuditService.computeRecordHash(
            1L,
            WormAuditService.GENESIS_PREV_HASH,
            now,
            "admin",
            "CLUSTER_ADMIN",
            "CONFIG_CHANGE",
            "SERVER",
            "srv-1",
            AuditActionStatus.SUCCESS,
            "{}",
            "127.0.0.1"
        );

        assertThat(hash1)
            .matches("^[0-9a-f]{64}$")
            .isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should detect sequence number gap in verification")
    void testDetectSequenceGap() {
        AuditLog log1 = createValidEntry(1L, WormAuditService.GENESIS_PREV_HASH);
        AuditLog log3 = createValidEntry(3L, log1.getRecordHash()); // gap at 2!

        when(auditLogRepository.findAllByOrderBySequenceNumAsc()).thenReturn(List.of(log1, log3));

        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();
        assertThat(result.isValid()).isFalse();
        assertThat(result.corruptedSequenceNum()).isEqualTo(3L);
        assertThat(result.failureReason()).contains("Sequence gap or out-of-order sequence detected");
    }

    private AuditLog createValidEntry(long seq, String prevHash) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String hash = wormAuditService.computeRecordHash(
            seq, prevHash, now, "user", "ROLE", "EVENT", "TYPE", "ID", AuditActionStatus.SUCCESS, "", "127.0.0.1"
        );
        AuditLog entry = new AuditLog();
        entry.setSequenceNum(seq);
        entry.setTimestamp(now);
        entry.setActorId("user");
        entry.setActorRole("ROLE");
        entry.setEventType("EVENT");
        entry.setTargetType("TYPE");
        entry.setTargetId("ID");
        entry.setActionStatus(AuditActionStatus.SUCCESS);
        entry.setMetadataJson("");
        entry.setSourceIp("127.0.0.1");
        entry.setPrevRecordHash(prevHash);
        entry.setRecordHash(hash);
        entry.setComplianceFlag("TEST");
        return entry;
    }
}
