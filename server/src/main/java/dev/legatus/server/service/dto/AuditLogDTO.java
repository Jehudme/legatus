package dev.legatus.server.service.dto;

import dev.legatus.server.domain.enumeration.AuditActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A DTO for the {@link dev.legatus.server.domain.AuditLog} entity.
 */
@Schema(
    description = "Statutory Write-Once-Read-Many (WORM) immutable audit ledger with\nsequential SHA-256 cryptographic hash chaining (Table: audit_logs)"
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AuditLogDTO implements Serializable {

    @NotNull
    private UUID eventId;

    @NotNull
    @Min(value = 1L)
    private Long sequenceNum;

    @NotNull
    private Instant timestamp;

    @NotNull
    @Size(max = 255)
    private String actorId;

    @NotNull
    @Size(max = 32)
    private String actorRole;

    @NotNull
    @Size(max = 64)
    private String eventType;

    @NotNull
    @Size(max = 64)
    private String targetType;

    @Size(max = 255)
    private String targetId;

    @NotNull
    private AuditActionStatus actionStatus;

    @Lob
    private String metadataJson;

    @NotNull
    @Size(max = 45)
    private String sourceIp;

    @NotNull
    @Size(min = 64, max = 64)
    @Schema(
        description = "64-char hex SHA-256 hash of sequence_num - 1 (64 zeroes for Genesis)",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String prevRecordHash;

    @NotNull
    @Size(min = 64, max = 64)
    @Schema(description = "64-char hex SHA-256 hash of current record: H_i = SHA256(...)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recordHash;

    @NotNull
    @Size(max = 64)
    private String complianceFlag;

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Long getSequenceNum() {
        return sequenceNum;
    }

    public void setSequenceNum(Long sequenceNum) {
        this.sequenceNum = sequenceNum;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public AuditActionStatus getActionStatus() {
        return actionStatus;
    }

    public void setActionStatus(AuditActionStatus actionStatus) {
        this.actionStatus = actionStatus;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getPrevRecordHash() {
        return prevRecordHash;
    }

    public void setPrevRecordHash(String prevRecordHash) {
        this.prevRecordHash = prevRecordHash;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public void setRecordHash(String recordHash) {
        this.recordHash = recordHash;
    }

    public String getComplianceFlag() {
        return complianceFlag;
    }

    public void setComplianceFlag(String complianceFlag) {
        this.complianceFlag = complianceFlag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditLogDTO)) {
            return false;
        }

        AuditLogDTO auditLogDTO = (AuditLogDTO) o;
        if (this.eventId == null) {
            return false;
        }
        return Objects.equals(this.eventId, auditLogDTO.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.eventId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AuditLogDTO{" +
            "eventId='" + getEventId() + "'" +
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
