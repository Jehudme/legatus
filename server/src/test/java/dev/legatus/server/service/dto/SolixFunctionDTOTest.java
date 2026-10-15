package dev.legatus.server.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolixFunctionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(SolixFunctionDTO.class);
        SolixFunctionDTO solixFunctionDTO1 = new SolixFunctionDTO();
        solixFunctionDTO1.setFunctionId(UUID.randomUUID());
        SolixFunctionDTO solixFunctionDTO2 = new SolixFunctionDTO();
        assertThat(solixFunctionDTO1).isNotEqualTo(solixFunctionDTO2);
        solixFunctionDTO2.setFunctionId(solixFunctionDTO1.getFunctionId());
        assertThat(solixFunctionDTO1).isEqualTo(solixFunctionDTO2);
        solixFunctionDTO2.setFunctionId(UUID.randomUUID());
        assertThat(solixFunctionDTO1).isNotEqualTo(solixFunctionDTO2);
        solixFunctionDTO1.setFunctionId(null);
        assertThat(solixFunctionDTO1).isNotEqualTo(solixFunctionDTO2);
    }
}
