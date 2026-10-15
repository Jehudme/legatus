package dev.legatus.server.service.criteria;

import dev.legatus.server.domain.enumeration.AuditActionStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link dev.legatus.server.domain.AuditLog} entity. This class is used
 * in {@link dev.legatus.server.web.rest.AuditLogResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /audit-logs?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AuditLogCriteria implements Serializable, Criteria {

    /**
     * Class for filtering AuditActionStatus
     */
    public static class AuditActionStatusFilter extends Filter<AuditActionStatus> {

        public AuditActionStatusFilter() {}

        public AuditActionStatusFilter(AuditActionStatusFilter filter) {
            super(filter);
        }

        @Override
        public AuditActionStatusFilter copy() {
            return new AuditActionStatusFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private UUIDFilter eventId;

    private LongFilter sequenceNum;

    private InstantFilter timestamp;

    private StringFilter actorId;

    private StringFilter actorRole;

    private StringFilter eventType;

    private StringFilter targetType;

    private StringFilter targetId;

    private AuditActionStatusFilter actionStatus;

    private StringFilter sourceIp;

    private StringFilter prevRecordHash;

    private StringFilter recordHash;

    private StringFilter complianceFlag;

    private Boolean distinct;

    public AuditLogCriteria() {}

    public AuditLogCriteria(AuditLogCriteria other) {
        this.eventId = other.optionalEventId().map(UUIDFilter::copy).orElse(null);
        this.sequenceNum = other.optionalSequenceNum().map(LongFilter::copy).orElse(null);
        this.timestamp = other.optionalTimestamp().map(InstantFilter::copy).orElse(null);
        this.actorId = other.optionalActorId().map(StringFilter::copy).orElse(null);
        this.actorRole = other.optionalActorRole().map(StringFilter::copy).orElse(null);
        this.eventType = other.optionalEventType().map(StringFilter::copy).orElse(null);
        this.targetType = other.optionalTargetType().map(StringFilter::copy).orElse(null);
        this.targetId = other.optionalTargetId().map(StringFilter::copy).orElse(null);
        this.actionStatus = other.optionalActionStatus().map(AuditActionStatusFilter::copy).orElse(null);
        this.sourceIp = other.optionalSourceIp().map(StringFilter::copy).orElse(null);
        this.prevRecordHash = other.optionalPrevRecordHash().map(StringFilter::copy).orElse(null);
        this.recordHash = other.optionalRecordHash().map(StringFilter::copy).orElse(null);
        this.complianceFlag = other.optionalComplianceFlag().map(StringFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public AuditLogCriteria copy() {
        return new AuditLogCriteria(this);
    }

    public UUIDFilter getEventId() {
        return eventId;
    }

    public Optional<UUIDFilter> optionalEventId() {
        return Optional.ofNullable(eventId);
    }

    public UUIDFilter eventId() {
        if (eventId == null) {
            setEventId(new UUIDFilter());
        }
        return eventId;
    }

    public void setEventId(UUIDFilter eventId) {
        this.eventId = eventId;
    }

    public LongFilter getSequenceNum() {
        return sequenceNum;
    }

    public Optional<LongFilter> optionalSequenceNum() {
        return Optional.ofNullable(sequenceNum);
    }

    public LongFilter sequenceNum() {
        if (sequenceNum == null) {
            setSequenceNum(new LongFilter());
        }
        return sequenceNum;
    }

    public void setSequenceNum(LongFilter sequenceNum) {
        this.sequenceNum = sequenceNum;
    }

    public InstantFilter getTimestamp() {
        return timestamp;
    }

    public Optional<InstantFilter> optionalTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    public InstantFilter timestamp() {
        if (timestamp == null) {
            setTimestamp(new InstantFilter());
        }
        return timestamp;
    }

    public void setTimestamp(InstantFilter timestamp) {
        this.timestamp = timestamp;
    }

    public StringFilter getActorId() {
        return actorId;
    }

    public Optional<StringFilter> optionalActorId() {
        return Optional.ofNullable(actorId);
    }

    public StringFilter actorId() {
        if (actorId == null) {
            setActorId(new StringFilter());
        }
        return actorId;
    }

    public void setActorId(StringFilter actorId) {
        this.actorId = actorId;
    }

    public StringFilter getActorRole() {
        return actorRole;
    }

    public Optional<StringFilter> optionalActorRole() {
        return Optional.ofNullable(actorRole);
    }

    public StringFilter actorRole() {
        if (actorRole == null) {
            setActorRole(new StringFilter());
        }
        return actorRole;
    }

    public void setActorRole(StringFilter actorRole) {
        this.actorRole = actorRole;
    }

    public StringFilter getEventType() {
        return eventType;
    }

    public Optional<StringFilter> optionalEventType() {
        return Optional.ofNullable(eventType);
    }

    public StringFilter eventType() {
        if (eventType == null) {
            setEventType(new StringFilter());
        }
        return eventType;
    }

    public void setEventType(StringFilter eventType) {
        this.eventType = eventType;
    }

    public StringFilter getTargetType() {
        return targetType;
    }

    public Optional<StringFilter> optionalTargetType() {
        return Optional.ofNullable(targetType);
    }

    public StringFilter targetType() {
        if (targetType == null) {
            setTargetType(new StringFilter());
        }
        return targetType;
    }

    public void setTargetType(StringFilter targetType) {
        this.targetType = targetType;
    }

    public StringFilter getTargetId() {
        return targetId;
    }

    public Optional<StringFilter> optionalTargetId() {
        return Optional.ofNullable(targetId);
    }

    public StringFilter targetId() {
        if (targetId == null) {
            setTargetId(new StringFilter());
        }
        return targetId;
    }

    public void setTargetId(StringFilter targetId) {
        this.targetId = targetId;
    }

    public AuditActionStatusFilter getActionStatus() {
        return actionStatus;
    }

    public Optional<AuditActionStatusFilter> optionalActionStatus() {
        return Optional.ofNullable(actionStatus);
    }

    public AuditActionStatusFilter actionStatus() {
        if (actionStatus == null) {
            setActionStatus(new AuditActionStatusFilter());
        }
        return actionStatus;
    }

    public void setActionStatus(AuditActionStatusFilter actionStatus) {
        this.actionStatus = actionStatus;
    }

    public StringFilter getSourceIp() {
        return sourceIp;
    }

    public Optional<StringFilter> optionalSourceIp() {
        return Optional.ofNullable(sourceIp);
    }

    public StringFilter sourceIp() {
        if (sourceIp == null) {
            setSourceIp(new StringFilter());
        }
        return sourceIp;
    }

    public void setSourceIp(StringFilter sourceIp) {
        this.sourceIp = sourceIp;
    }

    public StringFilter getPrevRecordHash() {
        return prevRecordHash;
    }

    public Optional<StringFilter> optionalPrevRecordHash() {
        return Optional.ofNullable(prevRecordHash);
    }

    public StringFilter prevRecordHash() {
        if (prevRecordHash == null) {
            setPrevRecordHash(new StringFilter());
        }
        return prevRecordHash;
    }

    public void setPrevRecordHash(StringFilter prevRecordHash) {
        this.prevRecordHash = prevRecordHash;
    }

    public StringFilter getRecordHash() {
        return recordHash;
    }

    public Optional<StringFilter> optionalRecordHash() {
        return Optional.ofNullable(recordHash);
    }

    public StringFilter recordHash() {
        if (recordHash == null) {
            setRecordHash(new StringFilter());
        }
        return recordHash;
    }

    public void setRecordHash(StringFilter recordHash) {
        this.recordHash = recordHash;
    }

    public StringFilter getComplianceFlag() {
        return complianceFlag;
    }

    public Optional<StringFilter> optionalComplianceFlag() {
        return Optional.ofNullable(complianceFlag);
    }

    public StringFilter complianceFlag() {
        if (complianceFlag == null) {
            setComplianceFlag(new StringFilter());
        }
        return complianceFlag;
    }

    public void setComplianceFlag(StringFilter complianceFlag) {
        this.complianceFlag = complianceFlag;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AuditLogCriteria that = (AuditLogCriteria) o;
        return (
            Objects.equals(eventId, that.eventId) &&
            Objects.equals(sequenceNum, that.sequenceNum) &&
            Objects.equals(timestamp, that.timestamp) &&
            Objects.equals(actorId, that.actorId) &&
            Objects.equals(actorRole, that.actorRole) &&
            Objects.equals(eventType, that.eventType) &&
            Objects.equals(targetType, that.targetType) &&
            Objects.equals(targetId, that.targetId) &&
            Objects.equals(actionStatus, that.actionStatus) &&
            Objects.equals(sourceIp, that.sourceIp) &&
            Objects.equals(prevRecordHash, that.prevRecordHash) &&
            Objects.equals(recordHash, that.recordHash) &&
            Objects.equals(complianceFlag, that.complianceFlag) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            eventId,
            sequenceNum,
            timestamp,
            actorId,
            actorRole,
            eventType,
            targetType,
            targetId,
            actionStatus,
            sourceIp,
            prevRecordHash,
            recordHash,
            complianceFlag,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AuditLogCriteria{" +
            optionalEventId().map(f -> "eventId=" + f + ", ").orElse("") +
            optionalSequenceNum().map(f -> "sequenceNum=" + f + ", ").orElse("") +
            optionalTimestamp().map(f -> "timestamp=" + f + ", ").orElse("") +
            optionalActorId().map(f -> "actorId=" + f + ", ").orElse("") +
            optionalActorRole().map(f -> "actorRole=" + f + ", ").orElse("") +
            optionalEventType().map(f -> "eventType=" + f + ", ").orElse("") +
            optionalTargetType().map(f -> "targetType=" + f + ", ").orElse("") +
            optionalTargetId().map(f -> "targetId=" + f + ", ").orElse("") +
            optionalActionStatus().map(f -> "actionStatus=" + f + ", ").orElse("") +
            optionalSourceIp().map(f -> "sourceIp=" + f + ", ").orElse("") +
            optionalPrevRecordHash().map(f -> "prevRecordHash=" + f + ", ").orElse("") +
            optionalRecordHash().map(f -> "recordHash=" + f + ", ").orElse("") +
            optionalComplianceFlag().map(f -> "complianceFlag=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
