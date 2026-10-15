package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable quarantine enrollment model driving the side-by-side visual attestation modal.
 */
public class QuarantineEnrollment {

    private final StringProperty enrollmentId = new SimpleStringProperty("");
    private final StringProperty workerId = new SimpleStringProperty("");
    private final StringProperty hostname = new SimpleStringProperty("");
    private final StringProperty sourceIp = new SimpleStringProperty("");
    private final StringProperty os = new SimpleStringProperty("");
    private final StringProperty architecture = new SimpleStringProperty("");
    private final StringProperty fingerprintHex = new SimpleStringProperty("");
    private final StringProperty visualCode = new SimpleStringProperty("");
    private final IntegerProperty allocatableCores = new SimpleIntegerProperty(0);
    private final LongProperty maxMemoryBytes = new SimpleLongProperty(0);
    private final StringProperty submittedAt = new SimpleStringProperty("");

    public QuarantineEnrollment() {}

    public QuarantineEnrollment(String enrollmentId, String workerId, String hostname, String sourceIp,
                                String os, String architecture, String fingerprintHex, String visualCode,
                                int allocatableCores, long maxMemoryBytes, String submittedAt) {
        this.enrollmentId.set(enrollmentId);
        this.workerId.set(workerId);
        this.hostname.set(hostname);
        this.sourceIp.set(sourceIp);
        this.os.set(os);
        this.architecture.set(architecture);
        this.fingerprintHex.set(fingerprintHex);
        this.visualCode.set(visualCode);
        this.allocatableCores.set(allocatableCores);
        this.maxMemoryBytes.set(maxMemoryBytes);
        this.submittedAt.set(submittedAt);
    }

    public StringProperty enrollmentIdProperty() { return enrollmentId; }
    public String getEnrollmentId() { return enrollmentId.get(); }
    public void setEnrollmentId(String value) { this.enrollmentId.set(value); }

    public StringProperty workerIdProperty() { return workerId; }
    public String getWorkerId() { return workerId.get(); }
    public void setWorkerId(String value) { this.workerId.set(value); }

    public StringProperty hostnameProperty() { return hostname; }
    public String getHostname() { return hostname.get(); }
    public void setHostname(String value) { this.hostname.set(value); }

    public StringProperty sourceIpProperty() { return sourceIp; }
    public String getSourceIp() { return sourceIp.get(); }
    public void setSourceIp(String value) { this.sourceIp.set(value); }

    public StringProperty osProperty() { return os; }
    public String getOs() { return os.get(); }
    public void setOs(String value) { this.os.set(value); }

    public StringProperty architectureProperty() { return architecture; }
    public String getArchitecture() { return architecture.get(); }
    public void setArchitecture(String value) { this.architecture.set(value); }

    public StringProperty fingerprintHexProperty() { return fingerprintHex; }
    public String getFingerprintHex() { return fingerprintHex.get(); }
    public void setFingerprintHex(String value) { this.fingerprintHex.set(value); }

    public StringProperty visualCodeProperty() { return visualCode; }
    public String getVisualCode() { return visualCode.get(); }
    public void setVisualCode(String value) { this.visualCode.set(value); }

    public IntegerProperty allocatableCoresProperty() { return allocatableCores; }
    public int getAllocatableCores() { return allocatableCores.get(); }
    public void setAllocatableCores(int value) { this.allocatableCores.set(value); }

    public LongProperty maxMemoryBytesProperty() { return maxMemoryBytes; }
    public long getMaxMemoryBytes() { return maxMemoryBytes.get(); }
    public void setMaxMemoryBytes(long value) { this.maxMemoryBytes.set(value); }

    public StringProperty submittedAtProperty() { return submittedAt; }
    public String getSubmittedAt() { return submittedAt.get(); }
    public void setSubmittedAt(String value) { this.submittedAt.set(value); }
}
