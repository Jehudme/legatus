package com.legatus.studio.viewmodel;

import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.service.WorkerService;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the Fleet Topology master-detail view, search filtering, and node controls.
 */
public class FleetViewModel {

    private final ObservableList<WorkerNode> allWorkers;
    private final FilteredList<WorkerNode> filteredWorkers;
    private final javafx.collections.transformation.SortedList<WorkerNode> sortedWorkers;
    private final ObjectProperty<WorkerNode> selectedWorker = new SimpleObjectProperty<>();

    private final StringProperty filterQuery = new SimpleStringProperty("");
    private final StringProperty stateFilter = new SimpleStringProperty("ALL");
    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private final WorkerService workerService;

    private static final java.util.Map<String, Integer> STATE_PRIORITY = java.util.Map.of(
            "ONLINE", 1,
            "DRAINING", 2,
            "PENDING_APPROVAL", 3,
            "QUARANTINED", 4,
            "OFFLINE", 5,
            "REVOKED", 6
    );

    public FleetViewModel(ObservableList<WorkerNode> allWorkers, WorkerService workerService) {
        this.allWorkers = Objects.requireNonNull(allWorkers, "allWorkers must not be null");
        this.workerService = Objects.requireNonNull(workerService, "workerService must not be null");

        this.filteredWorkers = new FilteredList<>(allWorkers, this::matchesFilter);
        this.sortedWorkers = new javafx.collections.transformation.SortedList<>(filteredWorkers, this::compareWorkers);

        this.filterQuery.addListener((obs, oldV, newV) -> filteredWorkers.setPredicate(this::matchesFilter));
        this.stateFilter.addListener((obs, oldV, newV) -> filteredWorkers.setPredicate(this::matchesFilter));
    }

    private int compareWorkers(WorkerNode w1, WorkerNode w2) {
        if (w1 == null && w2 == null) return 0;
        if (w1 == null) return 1;
        if (w2 == null) return -1;

        int p1 = STATE_PRIORITY.getOrDefault(w1.getState() != null ? w1.getState().toUpperCase() : "", 99);
        int p2 = STATE_PRIORITY.getOrDefault(w2.getState() != null ? w2.getState().toUpperCase() : "", 99);
        if (p1 != p2) {
            return Integer.compare(p1, p2);
        }
        return w1.getWorkerId().compareToIgnoreCase(w2.getWorkerId());
    }

    private boolean matchesFilter(WorkerNode worker) {
        if (worker == null) return false;

        String query = filterQuery.get().toLowerCase().trim();
        if (!query.isEmpty()) {
            boolean matchesId = worker.getWorkerId() != null && worker.getWorkerId().toLowerCase().contains(query);
            boolean matchesName = worker.getFriendlyName() != null && worker.getFriendlyName().toLowerCase().contains(query);
            boolean matchesHost = worker.getHostname() != null && worker.getHostname().toLowerCase().contains(query);
            boolean matchesIp = worker.getSourceIp() != null && worker.getSourceIp().toLowerCase().contains(query);
            if (!matchesId && !matchesName && !matchesHost && !matchesIp) {
                return false;
            }
        }

        String state = stateFilter.get();
        if (!"ALL".equalsIgnoreCase(state) && !state.equalsIgnoreCase(worker.getState())) {
            return false;
        }

        return true;
    }

    public CompletableFuture<Void> renameSelectedWorker(String newName) {
        WorkerNode worker = selectedWorker.get();
        if (worker == null || newName == null || newName.isBlank()) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.renameWorker(worker.getWorkerId(), newName.trim())
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        worker.setFriendlyName(newName.trim());
                        isLoading.set(false);
                        statusMessage.set("Worker renamed to: " + newName.trim());
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to rename worker: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> toggleActiveSelectedWorker() {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        boolean newActive = !worker.isActive();
        return workerService.toggleActive(worker.getWorkerId())
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        worker.setActive(newActive);
                        if (!newActive && "ONLINE".equalsIgnoreCase(worker.getState())) {
                            worker.setState("INACTIVE");
                        } else if (newActive && "INACTIVE".equalsIgnoreCase(worker.getState())) {
                            worker.setState("ONLINE");
                        }
                        isLoading.set(false);
                        statusMessage.set("Worker active state toggled to: " + newActive);
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to toggle active state: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> toggleAutoRenewSelectedWorker(boolean autoRenew) {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.toggleAutoRenew(worker.getWorkerId(), autoRenew)
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        worker.setAutoRenewLease(autoRenew);
                        isLoading.set(false);
                        statusMessage.set("Auto lease renewal set to: " + autoRenew);
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to set auto renew: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> updateThresholdsSelectedWorker(double targetCpuPct, double targetRamPct, int maxConcurrentTasks) {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.updateThresholds(worker.getWorkerId(), targetCpuPct, targetRamPct, maxConcurrentTasks)
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        worker.setTargetCpuPct(targetCpuPct);
                        worker.setTargetRamPct(targetRamPct);
                        worker.setMaxConcurrentTasks(maxConcurrentTasks);
                        isLoading.set(false);
                        statusMessage.set("Thresholds updated successfully.");
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to update thresholds: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> drainSelectedWorker() {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.drainWorker(worker.getWorkerId())
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        worker.setState("DRAINING");
                        isLoading.set(false);
                        statusMessage.set("Worker " + worker.getWorkerId() + " marked as DRAINING.");
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to drain worker: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> revokeSelectedWorker() {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.revokeWorker(worker.getWorkerId())
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        worker.setState("REVOKED");
                        isLoading.set(false);
                        statusMessage.set("Worker " + worker.getWorkerId() + " lease revoked.");
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to revoke worker: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> deleteSelectedWorker() {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.deleteWorker(worker.getWorkerId())
                .thenAccept(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        allWorkers.remove(worker);
                        selectedWorker.set(null);
                        isLoading.set(false);
                        statusMessage.set("Worker " + worker.getWorkerId() + " deleted from fleet.");
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        statusMessage.set("Failed to delete worker: " + err);
                        errorMessage.set(err != null ? err : "Unknown error");
                    });
                    return null;
                });
    }

    public ObservableList<WorkerNode> getFilteredWorkers() { return sortedWorkers; }
    public javafx.collections.transformation.SortedList<WorkerNode> getSortedWorkers() { return sortedWorkers; }
    public FilteredList<WorkerNode> getUnderlyingFilteredList() { return filteredWorkers; }
    public ObjectProperty<WorkerNode> selectedWorkerProperty() { return selectedWorker; }
    public WorkerNode getSelectedWorker() { return selectedWorker.get(); }
    public void setSelectedWorker(WorkerNode worker) { this.selectedWorker.set(worker); }

    public StringProperty filterQueryProperty() { return filterQuery; }
    public String getFilterQuery() { return filterQuery.get(); }
    public void setFilterQuery(String value) { this.filterQuery.set(value); }

    public StringProperty stateFilterProperty() { return stateFilter; }
    public String getStateFilter() { return stateFilter.get(); }
    public void setStateFilter(String value) { this.stateFilter.set(value); }

    public BooleanProperty isLoadingProperty() { return isLoading; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public String getErrorMessage() { return errorMessage.get(); }
    public void setErrorMessage(String value) { this.errorMessage.set(value); }
}
