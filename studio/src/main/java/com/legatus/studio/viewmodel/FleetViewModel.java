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
    private final ObjectProperty<WorkerNode> selectedWorker = new SimpleObjectProperty<>();

    private final StringProperty filterQuery = new SimpleStringProperty("");
    private final StringProperty stateFilter = new SimpleStringProperty("ALL");
    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final WorkerService workerService;

    public FleetViewModel(ObservableList<WorkerNode> allWorkers, WorkerService workerService) {
        this.allWorkers = Objects.requireNonNull(allWorkers, "allWorkers must not be null");
        this.workerService = Objects.requireNonNull(workerService, "workerService must not be null");

        this.filteredWorkers = new FilteredList<>(allWorkers, this::matchesFilter);

        this.filterQuery.addListener((obs, oldV, newV) -> filteredWorkers.setPredicate(this::matchesFilter));
        this.stateFilter.addListener((obs, oldV, newV) -> filteredWorkers.setPredicate(this::matchesFilter));
    }

    private boolean matchesFilter(WorkerNode worker) {
        if (worker == null) return false;

        String query = filterQuery.get().toLowerCase().trim();
        if (!query.isEmpty()) {
            boolean matchesId = worker.getWorkerId().toLowerCase().contains(query);
            boolean matchesHost = worker.getHostname().toLowerCase().contains(query);
            boolean matchesIp = worker.getSourceIp().toLowerCase().contains(query);
            if (!matchesId && !matchesHost && !matchesIp) {
                return false;
            }
        }

        String state = stateFilter.get();
        if (!"ALL".equalsIgnoreCase(state) && !state.equalsIgnoreCase(worker.getState())) {
            return false;
        }

        return true;
    }

    public CompletableFuture<Void> drainSelectedWorker() {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.drainWorker(worker.getWorkerId())
                .thenAccept(v -> {
                    worker.setState("DRAINING");
                    isLoading.set(false);
                    statusMessage.set("Worker " + worker.getWorkerId() + " marked as DRAINING.");
                })
                .exceptionally(ex -> {
                    isLoading.set(false);
                    statusMessage.set("Failed to drain worker: " + ex.getCause().getMessage());
                    return null;
                });
    }

    public CompletableFuture<Void> revokeSelectedWorker() {
        WorkerNode worker = selectedWorker.get();
        if (worker == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        return workerService.revokeWorker(worker.getWorkerId())
                .thenAccept(v -> {
                    worker.setState("REVOKED");
                    isLoading.set(false);
                    statusMessage.set("Worker " + worker.getWorkerId() + " lease revoked.");
                })
                .exceptionally(ex -> {
                    isLoading.set(false);
                    statusMessage.set("Failed to revoke worker: " + ex.getCause().getMessage());
                    return null;
                });
    }

    public FilteredList<WorkerNode> getFilteredWorkers() { return filteredWorkers; }
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
}
