package dev.legatus.server.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class WorkerNodeCriteriaTest {

    @Test
    void newWorkerNodeCriteriaHasAllFiltersNullTest() {
        var workerNodeCriteria = new WorkerNodeCriteria();
        assertThat(workerNodeCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void workerNodeCriteriaFluentMethodsCreatesFiltersTest() {
        var workerNodeCriteria = new WorkerNodeCriteria();

        setAllFilters(workerNodeCriteria);

        assertThat(workerNodeCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void workerNodeCriteriaCopyCreatesNullFilterTest() {
        var workerNodeCriteria = new WorkerNodeCriteria();
        var copy = workerNodeCriteria.copy();

        assertThat(workerNodeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(workerNodeCriteria)
        );
    }

    @Test
    void workerNodeCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var workerNodeCriteria = new WorkerNodeCriteria();
        setAllFilters(workerNodeCriteria);

        var copy = workerNodeCriteria.copy();

        assertThat(workerNodeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(workerNodeCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var workerNodeCriteria = new WorkerNodeCriteria();

        assertThat(workerNodeCriteria).hasToString("WorkerNodeCriteria{}");
    }

    private static void setAllFilters(WorkerNodeCriteria workerNodeCriteria) {
        workerNodeCriteria.workerId();
        workerNodeCriteria.enrollmentId();
        workerNodeCriteria.hostname();
        workerNodeCriteria.os();
        workerNodeCriteria.arch();
        workerNodeCriteria.agentVersion();
        workerNodeCriteria.ed25519Pubkey();
        workerNodeCriteria.fingerprint();
        workerNodeCriteria.visualCode();
        workerNodeCriteria.secretHash();
        workerNodeCriteria.state();
        workerNodeCriteria.assignedCluster();
        workerNodeCriteria.sourceIp();
        workerNodeCriteria.physicalCores();
        workerNodeCriteria.logicalCores();
        workerNodeCriteria.totalMemoryBytes();
        workerNodeCriteria.gpuPresent();
        workerNodeCriteria.gpuModel();
        workerNodeCriteria.allocatableCores();
        workerNodeCriteria.maxMemoryBytes();
        workerNodeCriteria.maxInstructionBudget();
        workerNodeCriteria.draining();
        workerNodeCriteria.activeSessionId();
        workerNodeCriteria.enrollmentExpiresAt();
        workerNodeCriteria.lastSeenAt();
        workerNodeCriteria.createdAt();
        workerNodeCriteria.distinct();
    }

    private static Condition<WorkerNodeCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getWorkerId()) &&
                condition.apply(criteria.getEnrollmentId()) &&
                condition.apply(criteria.getHostname()) &&
                condition.apply(criteria.getOs()) &&
                condition.apply(criteria.getArch()) &&
                condition.apply(criteria.getAgentVersion()) &&
                condition.apply(criteria.getEd25519Pubkey()) &&
                condition.apply(criteria.getFingerprint()) &&
                condition.apply(criteria.getVisualCode()) &&
                condition.apply(criteria.getSecretHash()) &&
                condition.apply(criteria.getState()) &&
                condition.apply(criteria.getAssignedCluster()) &&
                condition.apply(criteria.getSourceIp()) &&
                condition.apply(criteria.getPhysicalCores()) &&
                condition.apply(criteria.getLogicalCores()) &&
                condition.apply(criteria.getTotalMemoryBytes()) &&
                condition.apply(criteria.getGpuPresent()) &&
                condition.apply(criteria.getGpuModel()) &&
                condition.apply(criteria.getAllocatableCores()) &&
                condition.apply(criteria.getMaxMemoryBytes()) &&
                condition.apply(criteria.getMaxInstructionBudget()) &&
                condition.apply(criteria.getDraining()) &&
                condition.apply(criteria.getActiveSessionId()) &&
                condition.apply(criteria.getEnrollmentExpiresAt()) &&
                condition.apply(criteria.getLastSeenAt()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<WorkerNodeCriteria> copyFiltersAre(WorkerNodeCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getWorkerId(), copy.getWorkerId()) &&
                condition.apply(criteria.getEnrollmentId(), copy.getEnrollmentId()) &&
                condition.apply(criteria.getHostname(), copy.getHostname()) &&
                condition.apply(criteria.getOs(), copy.getOs()) &&
                condition.apply(criteria.getArch(), copy.getArch()) &&
                condition.apply(criteria.getAgentVersion(), copy.getAgentVersion()) &&
                condition.apply(criteria.getEd25519Pubkey(), copy.getEd25519Pubkey()) &&
                condition.apply(criteria.getFingerprint(), copy.getFingerprint()) &&
                condition.apply(criteria.getVisualCode(), copy.getVisualCode()) &&
                condition.apply(criteria.getSecretHash(), copy.getSecretHash()) &&
                condition.apply(criteria.getState(), copy.getState()) &&
                condition.apply(criteria.getAssignedCluster(), copy.getAssignedCluster()) &&
                condition.apply(criteria.getSourceIp(), copy.getSourceIp()) &&
                condition.apply(criteria.getPhysicalCores(), copy.getPhysicalCores()) &&
                condition.apply(criteria.getLogicalCores(), copy.getLogicalCores()) &&
                condition.apply(criteria.getTotalMemoryBytes(), copy.getTotalMemoryBytes()) &&
                condition.apply(criteria.getGpuPresent(), copy.getGpuPresent()) &&
                condition.apply(criteria.getGpuModel(), copy.getGpuModel()) &&
                condition.apply(criteria.getAllocatableCores(), copy.getAllocatableCores()) &&
                condition.apply(criteria.getMaxMemoryBytes(), copy.getMaxMemoryBytes()) &&
                condition.apply(criteria.getMaxInstructionBudget(), copy.getMaxInstructionBudget()) &&
                condition.apply(criteria.getDraining(), copy.getDraining()) &&
                condition.apply(criteria.getActiveSessionId(), copy.getActiveSessionId()) &&
                condition.apply(criteria.getEnrollmentExpiresAt(), copy.getEnrollmentExpiresAt()) &&
                condition.apply(criteria.getLastSeenAt(), copy.getLastSeenAt()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
