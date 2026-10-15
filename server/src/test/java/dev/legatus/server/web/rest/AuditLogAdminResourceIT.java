package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.service.WormAuditService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link AuditLogAdminResource} statutory WORM endpoints:
 * hash chain verification, meta-auditing, and HTTP 405 deletion/mutation rejection.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "admin_auditor", roles = { "ADMIN", "CLUSTER_ADMIN" })
@IntegrationTest
@Transactional
class AuditLogAdminResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WormAuditService wormAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Should verify intact cryptographic hash chain and append AUDIT_LOG_VERIFIED meta-audit event")
    void testVerifyChainSuccess() throws Exception {
        wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "CONFIG_INIT", "CONFIG", "1", AuditActionStatus.SUCCESS, "{}", "127.0.0.1", null
        );
        wormAuditService.recordEvent(
            "admin", "CLUSTER_ADMIN", "NODE_INIT", "NODE", "2", AuditActionStatus.SUCCESS, "{}", "127.0.0.1", null
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs/verify"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("VALID")))
            .andExpect(jsonPath("$.chain_integrity", is(true)))
            .andExpect(jsonPath("$.tamper_detected", is(false)))
            .andExpect(jsonPath("$.verified_records_count", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.discrepancy", nullValue()));

        // Check meta-auditing: AUDIT_LOG_VERIFIED must be recorded
        AuditLog latest = auditLogRepository.findTopByOrderBySequenceNumDesc().orElseThrow();
        assertThat(latest.getEventType()).isEqualTo("AUDIT_LOG_VERIFIED");
        assertThat(latest.getActorId()).isEqualTo("admin_auditor");
    }

    @Test
    @DisplayName("Should query audit logs and append AUDIT_LOG_ACCESSED meta-audit event")
    void testQueryAuditLogsWithMetaAuditing() throws Exception {
        wormAuditService.recordEvent(
            "system", "SYSTEM", "BOOT", "SYS", "0", AuditActionStatus.SUCCESS, null, "127.0.0.1", null
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs?page=0&size=10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", isA(List.class)))
            .andExpect(jsonPath("$.page", is(0)))
            .andExpect(jsonPath("$.size", is(10)));

        AuditLog latest = auditLogRepository.findTopByOrderBySequenceNumDesc().orElseThrow();
        assertThat(latest.getEventType()).isEqualTo("AUDIT_LOG_ACCESSED");
    }

    @Test
    @DisplayName("Should reject DELETE with HTTP 405 Method Not Allowed and log AUDIT_LOG_TAMPER_ATTEMPT")
    void testRejectDeleteWithMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/audit-logs/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isMethodNotAllowed());

        AuditLog latest = auditLogRepository.findTopByOrderBySequenceNumDesc().orElseThrow();
        assertThat(latest.getEventType()).isEqualTo("AUDIT_LOG_TAMPER_ATTEMPT");
        assertThat(latest.getActionStatus()).isEqualTo(AuditActionStatus.DENIED);
    }

    @Test
    @DisplayName("Should reject PUT with HTTP 405 Method Not Allowed and log AUDIT_LOG_TAMPER_ATTEMPT")
    void testRejectPutWithMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/v1/admin/audit-logs/00000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed());

        AuditLog latest = auditLogRepository.findTopByOrderBySequenceNumDesc().orElseThrow();
        assertThat(latest.getEventType()).isEqualTo("AUDIT_LOG_TAMPER_ATTEMPT");
        assertThat(latest.getActionStatus()).isEqualTo(AuditActionStatus.DENIED);
    }

    @Test
    @DisplayName("Should reject PATCH with HTTP 405 Method Not Allowed and log AUDIT_LOG_TAMPER_ATTEMPT")
    void testRejectPatchWithMethodNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/audit-logs/00000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed());

        AuditLog latest = auditLogRepository.findTopByOrderBySequenceNumDesc().orElseThrow();
        assertThat(latest.getEventType()).isEqualTo("AUDIT_LOG_TAMPER_ATTEMPT");
        assertThat(latest.getActionStatus()).isEqualTo(AuditActionStatus.DENIED);
    }
}
