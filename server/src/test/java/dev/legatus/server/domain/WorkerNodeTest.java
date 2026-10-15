package dev.legatus.server.domain;

import static dev.legatus.server.domain.WorkerNodeTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class WorkerNodeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(WorkerNode.class);
        WorkerNode workerNode1 = getWorkerNodeSample1();
        WorkerNode workerNode2 = new WorkerNode();
        assertThat(workerNode1).isNotEqualTo(workerNode2);

        workerNode2.setWorkerId(workerNode1.getWorkerId());
        assertThat(workerNode1).isEqualTo(workerNode2);

        workerNode2 = getWorkerNodeSample2();
        assertThat(workerNode1).isNotEqualTo(workerNode2);
    }
}
