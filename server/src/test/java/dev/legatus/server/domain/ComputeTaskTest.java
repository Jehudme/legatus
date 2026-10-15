package dev.legatus.server.domain;

import static dev.legatus.server.domain.ComputeTaskTestSamples.*;
import static dev.legatus.server.domain.SolixFunctionTestSamples.*;
import static dev.legatus.server.domain.WorkerNodeTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ComputeTaskTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ComputeTask.class);
        ComputeTask computeTask1 = getComputeTaskSample1();
        ComputeTask computeTask2 = new ComputeTask();
        assertThat(computeTask1).isNotEqualTo(computeTask2);

        computeTask2.setTaskId(computeTask1.getTaskId());
        assertThat(computeTask1).isEqualTo(computeTask2);

        computeTask2 = getComputeTaskSample2();
        assertThat(computeTask1).isNotEqualTo(computeTask2);
    }

    @Test
    void functionTest() {
        ComputeTask computeTask = getComputeTaskRandomSampleGenerator();
        SolixFunction solixFunctionBack = getSolixFunctionRandomSampleGenerator();

        computeTask.setFunction(solixFunctionBack);
        assertThat(computeTask.getFunction()).isEqualTo(solixFunctionBack);

        computeTask.function(null);
        assertThat(computeTask.getFunction()).isNull();
    }

    @Test
    void assignedWorkerTest() {
        ComputeTask computeTask = getComputeTaskRandomSampleGenerator();
        WorkerNode workerNodeBack = getWorkerNodeRandomSampleGenerator();

        computeTask.setAssignedWorker(workerNodeBack);
        assertThat(computeTask.getAssignedWorker()).isEqualTo(workerNodeBack);

        computeTask.assignedWorker(null);
        assertThat(computeTask.getAssignedWorker()).isNull();
    }
}
