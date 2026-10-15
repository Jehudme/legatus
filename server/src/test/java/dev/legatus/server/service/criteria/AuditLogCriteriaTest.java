package dev.legatus.server.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class AuditLogCriteriaTest {

    @Test
    void newAuditLogCriteriaHasAllFiltersNullTest() {
        var auditLogCriteria = new AuditLogCriteria();
        assertThat(auditLogCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void auditLogCriteriaFluentMethodsCreatesFiltersTest() {
        var auditLogCriteria = new AuditLogCriteria();

        setAllFilters(auditLogCriteria);

        assertThat(auditLogCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void auditLogCriteriaCopyCreatesNullFilterTest() {
        var auditLogCriteria = new AuditLogCriteria();
        var copy = auditLogCriteria.copy();

        assertThat(auditLogCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(auditLogCriteria)
        );
    }

    @Test
    void auditLogCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var auditLogCriteria = new AuditLogCriteria();
        setAllFilters(auditLogCriteria);

        var copy = auditLogCriteria.copy();

        assertThat(auditLogCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(auditLogCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var auditLogCriteria = new AuditLogCriteria();

        assertThat(auditLogCriteria).hasToString("AuditLogCriteria{}");
    }

    private static void setAllFilters(AuditLogCriteria auditLogCriteria) {
        auditLogCriteria.eventId();
        auditLogCriteria.sequenceNum();
        auditLogCriteria.timestamp();
        auditLogCriteria.actorId();
        auditLogCriteria.actorRole();
        auditLogCriteria.eventType();
        auditLogCriteria.targetType();
        auditLogCriteria.targetId();
        auditLogCriteria.actionStatus();
        auditLogCriteria.sourceIp();
        auditLogCriteria.prevRecordHash();
        auditLogCriteria.recordHash();
        auditLogCriteria.complianceFlag();
        auditLogCriteria.distinct();
    }

    private static Condition<AuditLogCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getEventId()) &&
                condition.apply(criteria.getSequenceNum()) &&
                condition.apply(criteria.getTimestamp()) &&
                condition.apply(criteria.getActorId()) &&
                condition.apply(criteria.getActorRole()) &&
                condition.apply(criteria.getEventType()) &&
                condition.apply(criteria.getTargetType()) &&
                condition.apply(criteria.getTargetId()) &&
                condition.apply(criteria.getActionStatus()) &&
                condition.apply(criteria.getSourceIp()) &&
                condition.apply(criteria.getPrevRecordHash()) &&
                condition.apply(criteria.getRecordHash()) &&
                condition.apply(criteria.getComplianceFlag()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<AuditLogCriteria> copyFiltersAre(AuditLogCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getEventId(), copy.getEventId()) &&
                condition.apply(criteria.getSequenceNum(), copy.getSequenceNum()) &&
                condition.apply(criteria.getTimestamp(), copy.getTimestamp()) &&
                condition.apply(criteria.getActorId(), copy.getActorId()) &&
                condition.apply(criteria.getActorRole(), copy.getActorRole()) &&
                condition.apply(criteria.getEventType(), copy.getEventType()) &&
                condition.apply(criteria.getTargetType(), copy.getTargetType()) &&
                condition.apply(criteria.getTargetId(), copy.getTargetId()) &&
                condition.apply(criteria.getActionStatus(), copy.getActionStatus()) &&
                condition.apply(criteria.getSourceIp(), copy.getSourceIp()) &&
                condition.apply(criteria.getPrevRecordHash(), copy.getPrevRecordHash()) &&
                condition.apply(criteria.getRecordHash(), copy.getRecordHash()) &&
                condition.apply(criteria.getComplianceFlag(), copy.getComplianceFlag()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
