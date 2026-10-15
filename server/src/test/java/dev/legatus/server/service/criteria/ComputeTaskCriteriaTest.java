package dev.legatus.server.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class ComputeTaskCriteriaTest {

    @Test
    void newComputeTaskCriteriaHasAllFiltersNullTest() {
        var computeTaskCriteria = new ComputeTaskCriteria();
        assertThat(computeTaskCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void computeTaskCriteriaFluentMethodsCreatesFiltersTest() {
        var computeTaskCriteria = new ComputeTaskCriteria();

        setAllFilters(computeTaskCriteria);

        assertThat(computeTaskCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void computeTaskCriteriaCopyCreatesNullFilterTest() {
        var computeTaskCriteria = new ComputeTaskCriteria();
        var copy = computeTaskCriteria.copy();

        assertThat(computeTaskCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(computeTaskCriteria)
        );
    }

    @Test
    void computeTaskCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var computeTaskCriteria = new ComputeTaskCriteria();
        setAllFilters(computeTaskCriteria);

        var copy = computeTaskCriteria.copy();

        assertThat(computeTaskCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(computeTaskCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var computeTaskCriteria = new ComputeTaskCriteria();

        assertThat(computeTaskCriteria).hasToString("ComputeTaskCriteria{}");
    }

    private static void setAllFilters(ComputeTaskCriteria computeTaskCriteria) {
        computeTaskCriteria.taskId();
        computeTaskCriteria.name();
        computeTaskCriteria.status();
        computeTaskCriteria.priority();
        computeTaskCriteria.priorityWeight();
        computeTaskCriteria.idempotent();
        computeTaskCriteria.entryPoint();
        computeTaskCriteria.bytecodeSha256();
        computeTaskCriteria.instructionBudget();
        computeTaskCriteria.maxMemoryBytes();
        computeTaskCriteria.timeoutMs();
        computeTaskCriteria.exitCode();
        computeTaskCriteria.instructionsConsumed();
        computeTaskCriteria.peakMemoryBytes();
        computeTaskCriteria.wallTimeMs();
        computeTaskCriteria.attemptCount();
        computeTaskCriteria.maxRetries();
        computeTaskCriteria.createdAt();
        computeTaskCriteria.startedAt();
        computeTaskCriteria.completedAt();
        computeTaskCriteria.functionId();
        computeTaskCriteria.assignedWorkerId();
        computeTaskCriteria.distinct();
    }

    private static Condition<ComputeTaskCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getTaskId()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getPriority()) &&
                condition.apply(criteria.getPriorityWeight()) &&
                condition.apply(criteria.getIdempotent()) &&
                condition.apply(criteria.getEntryPoint()) &&
                condition.apply(criteria.getBytecodeSha256()) &&
                condition.apply(criteria.getInstructionBudget()) &&
                condition.apply(criteria.getMaxMemoryBytes()) &&
                condition.apply(criteria.getTimeoutMs()) &&
                condition.apply(criteria.getExitCode()) &&
                condition.apply(criteria.getInstructionsConsumed()) &&
                condition.apply(criteria.getPeakMemoryBytes()) &&
                condition.apply(criteria.getWallTimeMs()) &&
                condition.apply(criteria.getAttemptCount()) &&
                condition.apply(criteria.getMaxRetries()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getStartedAt()) &&
                condition.apply(criteria.getCompletedAt()) &&
                condition.apply(criteria.getFunctionId()) &&
                condition.apply(criteria.getAssignedWorkerId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<ComputeTaskCriteria> copyFiltersAre(ComputeTaskCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getTaskId(), copy.getTaskId()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getPriority(), copy.getPriority()) &&
                condition.apply(criteria.getPriorityWeight(), copy.getPriorityWeight()) &&
                condition.apply(criteria.getIdempotent(), copy.getIdempotent()) &&
                condition.apply(criteria.getEntryPoint(), copy.getEntryPoint()) &&
                condition.apply(criteria.getBytecodeSha256(), copy.getBytecodeSha256()) &&
                condition.apply(criteria.getInstructionBudget(), copy.getInstructionBudget()) &&
                condition.apply(criteria.getMaxMemoryBytes(), copy.getMaxMemoryBytes()) &&
                condition.apply(criteria.getTimeoutMs(), copy.getTimeoutMs()) &&
                condition.apply(criteria.getExitCode(), copy.getExitCode()) &&
                condition.apply(criteria.getInstructionsConsumed(), copy.getInstructionsConsumed()) &&
                condition.apply(criteria.getPeakMemoryBytes(), copy.getPeakMemoryBytes()) &&
                condition.apply(criteria.getWallTimeMs(), copy.getWallTimeMs()) &&
                condition.apply(criteria.getAttemptCount(), copy.getAttemptCount()) &&
                condition.apply(criteria.getMaxRetries(), copy.getMaxRetries()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getStartedAt(), copy.getStartedAt()) &&
                condition.apply(criteria.getCompletedAt(), copy.getCompletedAt()) &&
                condition.apply(criteria.getFunctionId(), copy.getFunctionId()) &&
                condition.apply(criteria.getAssignedWorkerId(), copy.getAssignedWorkerId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
