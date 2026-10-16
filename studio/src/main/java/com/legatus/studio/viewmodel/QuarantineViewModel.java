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
    private final BooleanProperty autoAccept = new SimpleBooleanProperty(false);
    private final BooleanProperty isAdmin = new SimpleBooleanProperty(true); // default true for admin/cluster_admin

    private final WorkerService workerService;

    public QuarantineViewModel(ObservableList<QuarantineEnrollment> enrollments, WorkerService workerService) {
        this.enrollments = Objects.requireNonNull(enrollments, "enrollments must not be null");
        this.workerService = Objects.requireNonNull(workerService, "workerService must not be null");

        if (!enrollments.isEmpty()) {
            selectedEnrollment.set(enrollments.get(0));
        }

        // Check operator session role if available
        com.legatus.studio.security.OperatorSession session = com.legatus.studio.security.OperatorSession.getInstance();
        if (session != null && session.getRole() != null) {
            String role = session.getRole();
            isAdmin.set(role.equalsIgnoreCase("ROLE_ADMIN") || role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("CLUSTER_ADMIN"));
        }

        loadAutoAcceptState();
    }

    public void loadAutoAcceptState() {
        workerService.getAutoAccept()
                .thenAccept(enabled -> com.legatus.studio.service.FxAsync.runOnFxThread(() -> autoAccept.set(enabled)))
                .exceptionally(ex -> null);
    }

    public CompletableFuture<Void> setAutoAccept(boolean enabled) {
        return workerService.setAutoAccept(enabled)
                .thenAccept(v -> com.legatus.studio.service.FxAsync.runOnFxThread(() -> autoAccept.set(enabled)))
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        statusMessage.set("Failed to set auto-accept: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return null;
                });
    }

    public CompletableFuture<Boolean> approveSelected() {
        return approveSelected(null);
    }

    public CompletableFuture<Boolean> approveSelected(String friendlyName) {
        QuarantineEnrollment current = selectedEnrollment.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isProcessing.set(true);
        return workerService.approveEnrollment(current.getEnrollmentId(), leaseDuration.get(), friendlyName)
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        enrollments.remove(current);
                        selectedEnrollment.set(enrollments.isEmpty() ? null : enrollments.get(0));
                        isProcessing.set(false);
                        statusMessage.set("Lease issued for worker: " + (friendlyName != null ? friendlyName : current.getWorkerId()));
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isProcessing.set(false);
                        statusMessage.set("Approval failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public CompletableFuture<Boolean> rejectSelected() {
        QuarantineEnrollment current = selectedEnrollment.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isProcessing.set(true);
        return workerService.rejectEnrollment(current.getEnrollmentId(), rejectionReason.get(), blacklistIp.get())
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        enrollments.remove(current);
                        selectedEnrollment.set(enrollments.isEmpty() ? null : enrollments.get(0));
                        isProcessing.set(false);
                        statusMessage.set("Node " + current.getWorkerId() + " rejected.");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isProcessing.set(false);
                        statusMessage.set("Rejection failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
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

    public BooleanProperty autoAcceptProperty() { return autoAccept; }
    public boolean isAutoAccept() { return autoAccept.get(); }

    public BooleanProperty isAdminProperty() { return isAdmin; }
    public boolean isAdmin() { return isAdmin.get(); }
}
