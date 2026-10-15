package dev.legatus.server.domain;

import dev.legatus.server.domain.enumeration.AuditActionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Statutory Write-Once-Read-Many (WORM) immutable audit ledger with
 * sequential SHA-256 cryptographic hash chaining (Table: audit_logs)
 */
@Entity
@Table(name = "audit_logs")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Id
    @GeneratedValue
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @NotNull
    @Min(value = 1L)
    @Column(name = "sequence_num", nullable = false, unique = true)
    private Long sequenceNum;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @NotNull
    @Size(max = 255)
    @Column(name = "actor_id", length = 255, nullable = false)
    private String actorId;

    @NotNull
    @Size(max = 32)
    @Column(name = "actor_role", length = 32, nullable = false)
    private String actorRole;

    @NotNull
    @Size(max = 64)
    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @NotNull
    @Size(max = 64)
    @Column(name = "target_type", length = 64, nullable = false)
    private String targetType;

    @Size(max = 255)
    @Column(name = "target_id", length = 255)
    private String targetId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action_status", nullable = false)
    private AuditActionStatus actionStatus;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @NotNull
    @Size(max = 45)
    @Column(name = "source_ip", length = 45, nullable = false)
    private String sourceIp;

    /**
     * 64-char hex SHA-256 hash of sequence_num - 1 (64 zeroes for Genesis)
     */
    @NotNull
    @Size(min = 64, max = 64)
    @Column(name = "prev_record_hash", length = 64, nullable = false)
    private String prevRecordHash;

    /**
     * 64-char hex SHA-256 hash of current record: H_i = SHA256(...)
     */
    @NotNull
    @Size(min = 64, max = 64)
    @Column(name = "record_hash", length = 64, nullable = false, unique = true)
    private String recordHash;

    @NotNull
    @Size(max = 64)
    @Column(name = "compliance_flag", length = 64, nullable = false)
    private String complianceFlag;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public UUID getEventId() {
        return this.eventId;
    }

    public AuditLog eventId(UUID eventId) {
        this.setEventId(eventId);
        return this;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Long getSequenceNum() {
        return this.sequenceNum;
    }

    public AuditLog sequenceNum(Long sequenceNum) {
        this.setSequenceNum(sequenceNum);
        return this;
    }

    public void setSequenceNum(Long sequenceNum) {
        this.sequenceNum = sequenceNum;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public AuditLog timestamp(Instant timestamp) {
        this.setTimestamp(timestamp);
        return this;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getActorId() {
        return this.actorId;
    }

    public AuditLog actorId(String actorId) {
        this.setActorId(actorId);
        return this;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return this.actorRole;
    }

    public AuditLog actorRole(String actorRole) {
        this.setActorRole(actorRole);
        return this;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getEventType() {
        return this.eventType;
    }

    public AuditLog eventType(String eventType) {
        this.setEventType(eventType);
        return this;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTargetType() {
        return this.targetType;
    }

    public AuditLog targetType(String targetType) {
        this.setTargetType(targetType);
        return this;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return this.targetId;
    }

    public AuditLog targetId(String targetId) {
        this.setTargetId(targetId);
        return this;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public AuditActionStatus getActionStatus() {
        return this.actionStatus;
    }

    public AuditLog actionStatus(AuditActionStatus actionStatus) {
        this.setActionStatus(actionStatus);
        return this;
    }

    public void setActionStatus(AuditActionStatus actionStatus) {
        this.actionStatus = actionStatus;
    }

    public String getMetadataJson() {
        return this.metadataJson;
    }

    public AuditLog metadataJson(String metadataJson) {
        this.setMetadataJson(metadataJson);
        return this;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getSourceIp() {
        return this.sourceIp;
    }

    public AuditLog sourceIp(String sourceIp) {
        this.setSourceIp(sourceIp);
        return this;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getPrevRecordHash() {
        return this.prevRecordHash;
    }

    public AuditLog prevRecordHash(String prevRecordHash) {
        this.setPrevRecordHash(prevRecordHash);
        return this;
    }

    public void setPrevRecordHash(String prevRecordHash) {
        this.prevRecordHash = prevRecordHash;
    }

    public String getRecordHash() {
        return this.recordHash;
    }

    public AuditLog recordHash(String recordHash) {
        this.setRecordHash(recordHash);
        return this;
    }

    public void setRecordHash(String recordHash) {
        this.recordHash = recordHash;
    }

    public String getComplianceFlag() {
        return this.complianceFlag;
    }

    public AuditLog complianceFlag(String complianceFlag) {
        this.setComplianceFlag(complianceFlag);
        return this;
    }

    public void setComplianceFlag(String complianceFlag) {
        this.complianceFlag = complianceFlag;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditLog)) {
            return false;
        }
        return getEventId() != null && getEventId().equals(((AuditLog) o).getEventId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AuditLog{" +
            "eventId=" + getEventId() +
            ", sequenceNum=" + getSequenceNum() +
            ", timestamp='" + getTimestamp() + "'" +
            ", actorId='" + getActorId() + "'" +
            ", actorRole='" + getActorRole() + "'" +
            ", eventType='" + getEventType() + "'" +
            ", targetType='" + getTargetType() + "'" +
            ", targetId='" + getTargetId() + "'" +
            ", actionStatus='" + getActionStatus() + "'" +
            ", metadataJson='" + getMetadataJson() + "'" +
            ", sourceIp='" + getSourceIp() + "'" +
            ", prevRecordHash='" + getPrevRecordHash() + "'" +
            ", recordHash='" + getRecordHash() + "'" +
            ", complianceFlag='" + getComplianceFlag() + "'" +
            "}";
    }
}
