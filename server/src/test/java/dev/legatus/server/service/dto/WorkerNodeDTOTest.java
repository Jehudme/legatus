package dev.legatus.server.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class WorkerNodeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(WorkerNodeDTO.class);
        WorkerNodeDTO workerNodeDTO1 = new WorkerNodeDTO();
        workerNodeDTO1.setWorkerId("id1");
        WorkerNodeDTO workerNodeDTO2 = new WorkerNodeDTO();
        assertThat(workerNodeDTO1).isNotEqualTo(workerNodeDTO2);
        workerNodeDTO2.setWorkerId(workerNodeDTO1.getWorkerId());
        assertThat(workerNodeDTO1).isEqualTo(workerNodeDTO2);
        workerNodeDTO2.setWorkerId("id2");
        assertThat(workerNodeDTO1).isNotEqualTo(workerNodeDTO2);
        workerNodeDTO1.setWorkerId(null);
        assertThat(workerNodeDTO1).isNotEqualTo(workerNodeDTO2);
    }
}
