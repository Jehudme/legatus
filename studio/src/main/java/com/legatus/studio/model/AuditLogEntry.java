package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable WORM audit log entry model driving the Audit Log Inspector and hash-chain verification.
 */
public class AuditLogEntry {

    private final LongProperty sequenceNumber = new SimpleLongProperty(0);
    private final StringProperty eventId = new SimpleStringProperty("");
    private final StringProperty timestamp = new SimpleStringProperty("");
    private final StringProperty actorId = new SimpleStringProperty("");
    private final StringProperty action = new SimpleStringProperty("");
    private final StringProperty resourceType = new SimpleStringProperty("");
    private final StringProperty resourceId = new SimpleStringProperty("");
    private final StringProperty status = new SimpleStringProperty("");
    private final StringProperty sha256Hash = new SimpleStringProperty("");
    private final StringProperty previousHash = new SimpleStringProperty("");

    public AuditLogEntry() {}

    public AuditLogEntry(long sequenceNumber, String eventId, String timestamp, String actorId,
                         String action, String resourceType, String resourceId, String status,
                         String sha256Hash, String previousHash) {
        this.sequenceNumber.set(sequenceNumber);
        this.eventId.set(eventId);
        this.timestamp.set(timestamp);
        this.actorId.set(actorId);
        this.action.set(action);
        this.resourceType.set(resourceType);
        this.resourceId.set(resourceId);
        this.status.set(status);
        this.sha256Hash.set(sha256Hash);
        this.previousHash.set(previousHash);
    }

    public LongProperty sequenceNumberProperty() { return sequenceNumber; }
    public long getSequenceNumber() { return sequenceNumber.get(); }
    public void setSequenceNumber(long value) { this.sequenceNumber.set(value); }

    public StringProperty eventIdProperty() { return eventId; }
    public String getEventId() { return eventId.get(); }
    public void setEventId(String value) { this.eventId.set(value); }

    public StringProperty timestampProperty() { return timestamp; }
    public String getTimestamp() { return timestamp.get(); }
    public void setTimestamp(String value) { this.timestamp.set(value); }

    public StringProperty actorIdProperty() { return actorId; }
    public String getActorId() { return actorId.get(); }
    public void setActorId(String value) { this.actorId.set(value); }

    public StringProperty actionProperty() { return action; }
    public String getAction() { return action.get(); }
    public void setAction(String value) { this.action.set(value); }

    public StringProperty resourceTypeProperty() { return resourceType; }
    public String getResourceType() { return resourceType.get(); }
    public void setResourceType(String value) { this.resourceType.set(value); }

    public StringProperty resourceIdProperty() { return resourceId; }
    public String getResourceId() { return resourceId.get(); }
    public void setResourceId(String value) { this.resourceId.set(value); }

    public StringProperty statusProperty() { return status; }
    public String getStatus() { return status.get(); }
    public void setStatus(String value) { this.status.set(value); }

    public StringProperty sha256HashProperty() { return sha256Hash; }
    public String getSha256Hash() { return sha256Hash.get(); }
    public void setSha256Hash(String value) { this.sha256Hash.set(value); }

    public StringProperty previousHashProperty() { return previousHash; }
    public String getPreviousHash() { return previousHash.get(); }
    public void setPreviousHash(String value) { this.previousHash.set(value); }
}
