package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link WormAuditService} with real H2 relational persistence.
 */
@IntegrationTest
class WormAuditServiceIT {

    @Autowired
    private WormAuditService wormAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create Genesis record with 64 zeroes and sequentially chain subsequent records")
    void testGenesisAndSequentialChaining() {
        // 1. Genesis Event
        AuditLog genesis = wormAuditService.recordEvent(
            "operator-admin",
            "CLUSTER_ADMIN",
            "CLUSTER_BOOTSTRAP",
            "CLUSTER",
            "legatus-primary",
            AuditActionStatus.SUCCESS,
            "{\"version\":\"1.0.0\"}",
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        assertThat(genesis.getSequenceNum()).isEqualTo(1L);
        assertThat(genesis.getPrevRecordHash()).isEqualTo(WormAuditService.GENESIS_PREV_HASH);
        assertThat(genesis.getRecordHash()).hasSize(64);

        // 2. Second Event
        AuditLog second = wormAuditService.recordEvent(
            "worker-node-1",
            "WORKER_NODE",
            "WORKER_REGISTER",
            "WORKER",
            "wk-001",
            AuditActionStatus.SUCCESS,
            "{\"cores\":8}",
            "10.0.0.42",
            "SOC2_CC6.8"
        );

        assertThat(second.getSequenceNum()).isEqualTo(2L);
        assertThat(second.getPrevRecordHash()).isEqualTo(genesis.getRecordHash());
        assertThat(second.getRecordHash()).hasSize(64);

        // 3. Third Event
        AuditLog third = wormAuditService.recordEvent(
            "operator-admin",
            "CLUSTER_ADMIN",
            "WORKER_APPROVE",
            "WORKER",
            "wk-001",
            AuditActionStatus.SUCCESS,
            "{\"approved\":true}",
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        assertThat(third.getSequenceNum()).isEqualTo(3L);
        assertThat(third.getPrevRecordHash()).isEqualTo(second.getRecordHash());

        // Verify ledger integrity
        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();
        assertThat(result.isValid()).isTrue();
        assertThat(result.recordsVerified()).isEqualTo(3L);
        assertThat(result.corruptedSequenceNum()).isNull();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("Should detect corrupted record hash in the chain")
    void testTamperDetectionCorruptedHash() {
        wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "EVT_1", "SYS", "1", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );
        AuditLog second = wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "EVT_2", "SYS", "2", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );
        wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "EVT_3", "SYS", "3", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );

        // Tamper with second record hash
        second.setRecordHash("f".repeat(64));
        auditLogRepository.saveAndFlush(second);

        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();
        assertThat(result.isValid()).isFalse();
        assertThat(result.corruptedSequenceNum()).isEqualTo(2L);
        assertThat(result.failureReason()).contains("Cryptographic hash mismatch");
    }

    @Test
    @DisplayName("Should detect broken link when previous record hash is altered")
    void testTamperDetectionBrokenPrevLink() {
        wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "EVT_1", "SYS", "1", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );
        AuditLog second = wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "EVT_2", "SYS", "2", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );

        // Break the prev link on record 2
        second.setPrevRecordHash("e".repeat(64));
        auditLogRepository.saveAndFlush(second);

        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();
        assertThat(result.isValid()).isFalse();
        assertThat(result.corruptedSequenceNum()).isEqualTo(2L);
        assertThat(result.failureReason()).contains("Broken cryptographic chain");
    }

    @Test
    @DisplayName("Should verify successfully on empty ledger")
    void testEmptyLedger() {
        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();
        assertThat(result.isValid()).isTrue();
        assertThat(result.recordsVerified()).isZero();
    }
}
