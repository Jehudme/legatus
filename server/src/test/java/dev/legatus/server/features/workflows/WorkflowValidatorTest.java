package dev.legatus.server.features.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowValidatorTest {

    @Test
    @DisplayName("Should successfully topologically sort a linear DAG")
    void testLinearDag() {
        Map<String, WorkflowStep> steps = new LinkedHashMap<>();
        steps.put("step-a", new WorkflowStep("step-a", "fnA", List.of(), Map.of(), Map.of()));
        steps.put("step-b", new WorkflowStep("step-b", "fnB", List.of("step-a"), Map.of(), Map.of()));
        steps.put("step-c", new WorkflowStep("step-c", "fnC", List.of("step-b"), Map.of(), Map.of()));

        List<String> sorted = WorkflowValidator.validateAndSort(steps);
        assertThat(sorted).containsExactly("step-a", "step-b", "step-c");
    }

    @Test
    @DisplayName("Should successfully topologically sort a diamond DAG")
    void testDiamondDag() {
        Map<String, WorkflowStep> steps = new LinkedHashMap<>();
        steps.put("root", new WorkflowStep("root", "fnRoot", List.of(), Map.of(), Map.of()));
        steps.put("branch-1", new WorkflowStep("branch-1", "fn1", List.of("root"), Map.of(), Map.of()));
        steps.put("branch-2", new WorkflowStep("branch-2", "fn2", List.of("root"), Map.of(), Map.of()));
        steps.put("join", new WorkflowStep("join", "fnJoin", List.of("branch-1", "branch-2"), Map.of(), Map.of()));

        List<String> sorted = WorkflowValidator.validateAndSort(steps);
        assertThat(sorted).hasSize(4);
        assertThat(sorted.indexOf("root")).isLessThan(sorted.indexOf("branch-1"));
        assertThat(sorted.indexOf("root")).isLessThan(sorted.indexOf("branch-2"));
        assertThat(sorted.indexOf("branch-1")).isLessThan(sorted.indexOf("join"));
        assertThat(sorted.indexOf("branch-2")).isLessThan(sorted.indexOf("join"));
    }

    @Test
    @DisplayName("Should throw WorkflowCycleException when cyclic dependency is detected")
    void testCycleDetection() {
        Map<String, WorkflowStep> steps = new LinkedHashMap<>();
        steps.put("node-1", new WorkflowStep("node-1", "fn1", List.of("node-2"), Map.of(), Map.of()));
        steps.put("node-2", new WorkflowStep("node-2", "fn2", List.of("node-1"), Map.of(), Map.of()));

        assertThatThrownBy(() -> WorkflowValidator.validateAndSort(steps))
            .isInstanceOf(WorkflowValidator.WorkflowCycleException.class)
            .hasMessageContaining("Cyclic dependency detected");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when unknown dependency is referenced")
    void testUnknownDependency() {
        Map<String, WorkflowStep> steps = new LinkedHashMap<>();
        steps.put("node-1", new WorkflowStep("node-1", "fn1", List.of("ghost-node"), Map.of(), Map.of()));

        assertThatThrownBy(() -> WorkflowValidator.validateAndSort(steps))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("depends on unknown step 'ghost-node'");
    }
}
