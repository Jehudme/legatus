package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.security.WormAuditLogTrigger;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link WormAuditService} and {@link WormAuditLogTrigger}
 * with real embedded H2 relational persistence.
 */
@IntegrationTest
@Transactional
class WormAuditServiceIT {

    @Autowired
    private WormAuditService wormAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

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

        assertThat(genesis.getSequenceNum()).isNotNull();
        assertThat(genesis.getPrevRecordHash()).isNotNull();
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

        assertThat(second.getSequenceNum()).isEqualTo(genesis.getSequenceNum() + 1L);
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

        assertThat(third.getSequenceNum()).isEqualTo(second.getSequenceNum() + 1L);
        assertThat(third.getPrevRecordHash()).isEqualTo(second.getRecordHash());

        // Verify ledger integrity
        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();
        assertThat(result.isValid()).isTrue();
        assertThat(result.recordsVerified()).isGreaterThanOrEqualTo(3L);
        assertThat(result.corruptedSequenceNum()).isNull();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("Should prevent SQL DELETE statements via statutory H2 database trigger")
    void testTriggerPreventsDelete() {
        AuditLog log = wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "TEST_IMMUTABLE_DEL", "SYS", "1", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );

        assertThatThrownBy(() -> {
            auditLogRepository.delete(log);
            auditLogRepository.flush();
        }).hasRootCauseInstanceOf(SQLException.class)
          .hasRootCauseMessage(WormAuditLogTrigger.VIOLATION_MESSAGE);
    }

    @Test
    @DisplayName("Should prevent SQL UPDATE statements via statutory H2 database trigger")
    void testTriggerPreventsUpdate() {
        AuditLog log = wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "TEST_IMMUTABLE_UPD", "SYS", "2", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );

        log.setEventType("TAMPERED_EVENT_NAME");
        assertThatThrownBy(() -> {
            auditLogRepository.saveAndFlush(log);
        }).hasRootCauseInstanceOf(SQLException.class)
          .hasRootCauseMessage(WormAuditLogTrigger.VIOLATION_MESSAGE);
    }
}
