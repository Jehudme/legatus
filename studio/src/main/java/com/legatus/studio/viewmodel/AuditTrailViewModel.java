package com.legatus.studio.viewmodel;

import com.legatus.studio.model.AuditLogEntry;
import com.legatus.studio.service.AuditLogService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the WORM Audit Trail view and hash-chain cryptographic verification.
 */
public class AuditTrailViewModel {

    private final ObservableList<AuditLogEntry> allAuditLogs = FXCollections.observableArrayList();
    private final FilteredList<AuditLogEntry> filteredAuditLogs;

    private final StringProperty filterActor = new SimpleStringProperty("");
    private final StringProperty filterAction = new SimpleStringProperty("");
    private final BooleanProperty isBusy = new SimpleBooleanProperty(false);
    private final StringProperty verificationStatus = new SimpleStringProperty("UNVERIFIED");
    private final StringProperty verificationDetails = new SimpleStringProperty("");

    private final AuditLogService auditLogService;

    public AuditTrailViewModel(AuditLogService auditLogService) {
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService must not be null");

        this.filteredAuditLogs = new FilteredList<>(allAuditLogs, this::matchesFilter);
        this.filterActor.addListener((obs, oldV, newV) -> filteredAuditLogs.setPredicate(this::matchesFilter));
        this.filterAction.addListener((obs, oldV, newV) -> filteredAuditLogs.setPredicate(this::matchesFilter));
    }

    private boolean matchesFilter(AuditLogEntry entry) {
        if (entry == null) return false;

        String actor = filterActor.get().toLowerCase().trim();
        if (!actor.isEmpty() && !entry.getActorId().toLowerCase().contains(actor)) {
            return false;
        }

        String action = filterAction.get().toLowerCase().trim();
        if (!action.isEmpty() && !entry.getAction().toLowerCase().contains(action)) {
            return false;
        }

        return true;
    }

    public CompletableFuture<Void> refreshAuditLogs() {
        isBusy.set(true);
        return auditLogService.getAuditLogs()
                .thenAccept(list -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        allAuditLogs.clear();
                        list.forEach(dto -> allAuditLogs.add(new AuditLogEntry(
                                dto.sequence_number(), dto.event_id(), dto.timestamp(), dto.actor_id(),
                                dto.action(), dto.resource_type(), dto.resource_id(), dto.status(),
                                dto.sha256_hash(), dto.previous_hash()
                        )));
                        isBusy.set(false);
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        verificationDetails.set("Failed to load audit logs: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return null;
                });
    }

    public CompletableFuture<Boolean> verifyChainIntegrity() {
        isBusy.set(true);
        verificationStatus.set("VERIFYING");
        return auditLogService.verifyHashChain()
                .thenApply(res -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        if (res.valid()) {
                            verificationStatus.set("VALID_CHAIN");
                            verificationDetails.set(String.format("Verified %d records from genesis to head without break.", res.records_verified()));
                        } else {
                            verificationStatus.set("CORRUPTED_CHAIN");
                            verificationDetails.set("Hash chain corruption detected: " + res.error());
                        }
                    });
                    return res.valid();
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        verificationStatus.set("ERROR");
                        verificationDetails.set("Verification check failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public FilteredList<AuditLogEntry> getFilteredAuditLogs() { return filteredAuditLogs; }
    public StringProperty filterActorProperty() { return filterActor; }
    public StringProperty filterActionProperty() { return filterAction; }
    public BooleanProperty isBusyProperty() { return isBusy; }
    public StringProperty verificationStatusProperty() { return verificationStatus; }
    public StringProperty verificationDetailsProperty() { return verificationDetails; }
}
