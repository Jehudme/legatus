package com.legatus.studio.model;

import com.legatus.studio.protocol.RestDto;
import javafx.beans.property.*;

import java.util.UUID;

/**
 * Observable Solix bytecode & function item model for the Function Catalog & Inspector (MS-009).
 */
public class BytecodeItem {

    private final ObjectProperty<UUID> functionId = new SimpleObjectProperty<>();
    private final StringProperty sha256 = new SimpleStringProperty("");
    private final StringProperty functionName = new SimpleStringProperty("");
    private final StringProperty entryPoint = new SimpleStringProperty("main");
    private final LongProperty sizeBytes = new SimpleLongProperty(0);
    private final StringProperty uploadedAt = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final LongProperty invocationsLastHour = new SimpleLongProperty(0L);
    private RestDto.SolixFunctionDto rawDto;

    public BytecodeItem() {}

    public BytecodeItem(String sha256, String functionName, String entryPoint, long sizeBytes, String uploadedAt) {
        this.sha256.set(sha256);
        this.functionName.set(functionName);
        this.entryPoint.set(entryPoint);
        this.sizeBytes.set(sizeBytes);
        this.uploadedAt.set(uploadedAt);
    }

    public BytecodeItem(UUID functionId, String sha256, String functionName, String entryPoint, long sizeBytes, String uploadedAt) {
        this(sha256, functionName, entryPoint, sizeBytes, uploadedAt);
        this.functionId.set(functionId);
    }

    public BytecodeItem(UUID functionId, String sha256, String functionName, String entryPoint, long sizeBytes,
                        String uploadedAt, String description, String tags, long invocationsLastHour,
                        RestDto.SolixFunctionDto rawDto) {
        this.functionId.set(functionId);
        this.sha256.set(sha256);
        this.functionName.set(functionName);
        this.entryPoint.set(entryPoint);
        this.sizeBytes.set(sizeBytes);
        this.uploadedAt.set(uploadedAt);
        this.description.set(description != null ? description : "");
        this.tags.set(tags != null ? tags : "");
        this.invocationsLastHour.set(invocationsLastHour);
        this.rawDto = rawDto;
    }

    public ObjectProperty<UUID> functionIdProperty() { return functionId; }
    public UUID getFunctionId() { return functionId.get(); }
    public void setFunctionId(UUID value) { this.functionId.set(value); }

    public StringProperty sha256Property() { return sha256; }
    public String getSha256() { return sha256.get(); }
    public void setSha256(String value) { this.sha256.set(value); }

    public StringProperty functionNameProperty() { return functionName; }
    public String getFunctionName() { return functionName.get(); }
    public void setFunctionName(String value) { this.functionName.set(value); }

    public StringProperty entryPointProperty() { return entryPoint; }
    public String getEntryPoint() { return entryPoint.get(); }
    public void setEntryPoint(String value) { this.entryPoint.set(value); }

    public LongProperty sizeBytesProperty() { return sizeBytes; }
    public long getSizeBytes() { return sizeBytes.get(); }
    public void setSizeBytes(long value) { this.sizeBytes.set(value); }

    public StringProperty uploadedAtProperty() { return uploadedAt; }
    public String getUploadedAt() { return uploadedAt.get(); }
    public void setUploadedAt(String value) { this.uploadedAt.set(value); }

    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }
    public void setDescription(String value) { this.description.set(value != null ? value : ""); }

    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }
    public void setTags(String value) { this.tags.set(value != null ? value : ""); }

    public LongProperty invocationsLastHourProperty() { return invocationsLastHour; }
    public long getInvocationsLastHour() { return invocationsLastHour.get(); }
    public void setInvocationsLastHour(long value) { this.invocationsLastHour.set(value); }

    public RestDto.SolixFunctionDto getRawDto() { return rawDto; }
    public void setRawDto(RestDto.SolixFunctionDto rawDto) { this.rawDto = rawDto; }
}
