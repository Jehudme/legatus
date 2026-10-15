package dev.legatus.server.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditLogDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(AuditLogDTO.class);
        AuditLogDTO auditLogDTO1 = new AuditLogDTO();
        auditLogDTO1.setEventId(UUID.randomUUID());
        AuditLogDTO auditLogDTO2 = new AuditLogDTO();
        assertThat(auditLogDTO1).isNotEqualTo(auditLogDTO2);
        auditLogDTO2.setEventId(auditLogDTO1.getEventId());
        assertThat(auditLogDTO1).isEqualTo(auditLogDTO2);
        auditLogDTO2.setEventId(UUID.randomUUID());
        assertThat(auditLogDTO1).isNotEqualTo(auditLogDTO2);
        auditLogDTO1.setEventId(null);
        assertThat(auditLogDTO1).isNotEqualTo(auditLogDTO2);
    }
}
