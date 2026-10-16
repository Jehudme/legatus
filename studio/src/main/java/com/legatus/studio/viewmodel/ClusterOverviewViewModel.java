package com.legatus.studio.viewmodel;

import com.legatus.studio.model.ClusterMetrics;
import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.model.WorkerNode;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * ViewModel driving the Cluster Overview primary workspace.
 */
public class ClusterOverviewViewModel {

    private final ClusterMetrics clusterMetrics;
    private final ObservableList<WorkerNode> workers;
    private final ObservableList<TaskRecord> tasks;

    public ClusterOverviewViewModel(ClusterMetrics clusterMetrics, ObservableList<WorkerNode> workers, ObservableList<TaskRecord> tasks) {
        this.clusterMetrics = Objects.requireNonNull(clusterMetrics, "clusterMetrics must not be null");
        this.workers = Objects.requireNonNull(workers, "workers must not be null");
        this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
    }

    public ClusterMetrics getClusterMetrics() { return clusterMetrics; }
    public ObservableList<WorkerNode> getWorkers() { return workers; }
    public ObservableList<TaskRecord> getTasks() { return tasks; }
    public javafx.beans.property.BooleanProperty thresholdBreachWarningProperty() { return clusterMetrics.thresholdBreachWarningProperty(); }
    public javafx.beans.property.BooleanProperty queueSaturatedErrorProperty() { return clusterMetrics.queueSaturatedErrorProperty(); }
    public ObservableList<String> getBreachedWorkerIds() { return clusterMetrics.getBreachedWorkerIds(); }
}
