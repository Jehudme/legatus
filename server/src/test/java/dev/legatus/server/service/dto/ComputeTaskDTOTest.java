package dev.legatus.server.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ComputeTaskDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ComputeTaskDTO.class);
        ComputeTaskDTO computeTaskDTO1 = new ComputeTaskDTO();
        computeTaskDTO1.setTaskId("id1");
        ComputeTaskDTO computeTaskDTO2 = new ComputeTaskDTO();
        assertThat(computeTaskDTO1).isNotEqualTo(computeTaskDTO2);
        computeTaskDTO2.setTaskId(computeTaskDTO1.getTaskId());
        assertThat(computeTaskDTO1).isEqualTo(computeTaskDTO2);
        computeTaskDTO2.setTaskId("id2");
        assertThat(computeTaskDTO1).isNotEqualTo(computeTaskDTO2);
        computeTaskDTO1.setTaskId(null);
        assertThat(computeTaskDTO1).isNotEqualTo(computeTaskDTO2);
    }
}
