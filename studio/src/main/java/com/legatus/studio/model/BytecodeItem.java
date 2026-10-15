package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable Solix bytecode item model for the Functions & Bytecode Workbench.
 */
public class BytecodeItem {

    private final StringProperty sha256 = new SimpleStringProperty("");
    private final StringProperty functionName = new SimpleStringProperty("");
    private final StringProperty entryPoint = new SimpleStringProperty("main");
    private final LongProperty sizeBytes = new SimpleLongProperty(0);
    private final StringProperty uploadedAt = new SimpleStringProperty("");

    public BytecodeItem() {}

    public BytecodeItem(String sha256, String functionName, String entryPoint, long sizeBytes, String uploadedAt) {
        this.sha256.set(sha256);
        this.functionName.set(functionName);
        this.entryPoint.set(entryPoint);
        this.sizeBytes.set(sizeBytes);
        this.uploadedAt.set(uploadedAt);
    }

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
}
