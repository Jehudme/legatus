package com.legatus.studio.viewmodel;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.service.WorkerService;
import javafx.beans.property.*;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the Quarantine Queue and Side-by-Side Visual Attestation Modal (Issue #402, ADR-0005).
 */
public class QuarantineViewModel {

    private final ObservableList<QuarantineEnrollment> enrollments;
    private final ObjectProperty<QuarantineEnrollment> selectedEnrollment = new SimpleObjectProperty<>();

    private final StringProperty leaseDuration = new SimpleStringProperty("24h");
    private final StringProperty rejectionReason = new SimpleStringProperty("Operator rejected enrollment");
    private final BooleanProperty blacklistIp = new BooleanPropertyBase(false) {
        @Override public Object getBean() { return QuarantineViewModel.this; }
        @Override public String getName() { return "blacklistIp"; }
    };

    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final WorkerService workerService;

    public QuarantineViewModel(ObservableList<QuarantineEnrollment> enrollments, WorkerService workerService) {
        this.enrollments = Objects.requireNonNull(enrollments, "enrollments must not be null");
        this.workerService = Objects.requireNonNull(workerService, "workerService must not be null");

        if (!enrollments.isEmpty()) {
            selectedEnrollment.set(enrollments.get(0));
        }
    }

    public CompletableFuture<Boolean> approveSelected() {
        QuarantineEnrollment current = selectedEnrollment.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isProcessing.set(true);
        return workerService.approveEnrollment(current.getEnrollmentId(), leaseDuration.get())
                .thenApply(v -> {
                    enrollments.remove(current);
                    selectedEnrollment.set(enrollments.isEmpty() ? null : enrollments.get(0));
                    isProcessing.set(false);
                    statusMessage.set("Lease issued for worker: " + current.getWorkerId());
                    return true;
                })
                .exceptionally(ex -> {
                    isProcessing.set(false);
                    statusMessage.set("Approval failed: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> rejectSelected() {
        QuarantineEnrollment current = selectedEnrollment.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isProcessing.set(true);
        return workerService.rejectEnrollment(current.getEnrollmentId(), rejectionReason.get(), blacklistIp.get())
                .thenApply(v -> {
                    enrollments.remove(current);
                    selectedEnrollment.set(enrollments.isEmpty() ? null : enrollments.get(0));
                    isProcessing.set(false);
                    statusMessage.set("Node " + current.getWorkerId() + " rejected.");
                    return true;
                })
                .exceptionally(ex -> {
                    isProcessing.set(false);
                    statusMessage.set("Rejection failed: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public ObservableList<QuarantineEnrollment> getEnrollments() { return enrollments; }
    public ObjectProperty<QuarantineEnrollment> selectedEnrollmentProperty() { return selectedEnrollment; }
    public QuarantineEnrollment getSelectedEnrollment() { return selectedEnrollment.get(); }
    public void setSelectedEnrollment(QuarantineEnrollment enr) { this.selectedEnrollment.set(enr); }

    public StringProperty leaseDurationProperty() { return leaseDuration; }
    public StringProperty rejectionReasonProperty() { return rejectionReason; }
    public BooleanProperty blacklistIpProperty() { return blacklistIp; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public StringProperty statusMessageProperty() { return statusMessage; }
}
