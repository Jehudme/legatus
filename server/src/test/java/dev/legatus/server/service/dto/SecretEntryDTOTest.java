package dev.legatus.server.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SecretEntryDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(SecretEntryDTO.class);
        SecretEntryDTO secretEntryDTO1 = new SecretEntryDTO();
        secretEntryDTO1.setSecretKey("id1");
        SecretEntryDTO secretEntryDTO2 = new SecretEntryDTO();
        assertThat(secretEntryDTO1).isNotEqualTo(secretEntryDTO2);
        secretEntryDTO2.setSecretKey(secretEntryDTO1.getSecretKey());
        assertThat(secretEntryDTO1).isEqualTo(secretEntryDTO2);
        secretEntryDTO2.setSecretKey("id2");
        assertThat(secretEntryDTO1).isNotEqualTo(secretEntryDTO2);
        secretEntryDTO1.setSecretKey(null);
        assertThat(secretEntryDTO1).isNotEqualTo(secretEntryDTO2);
    }
}
