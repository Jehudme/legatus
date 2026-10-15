package com.legatus.studio.viewmodel;

import com.legatus.studio.model.ClusterMetrics;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.*;

import java.util.Objects;

/**
 * ViewModel driving the persistent Top Telemetry Ribbon.
 */
public class RibbonViewModel {

    private final ClusterMetrics clusterMetrics;

    private final StringBinding cpuFormatted;
    private final StringBinding memoryFormatted;
    private final StringBinding workersFormatted;
    private final StringBinding tasksThroughputFormatted;
    private final StringBinding rttFormatted;

    public RibbonViewModel(ClusterMetrics clusterMetrics) {
        this.clusterMetrics = Objects.requireNonNull(clusterMetrics, "clusterMetrics must not be null");

        this.cpuFormatted = Bindings.createStringBinding(
                () -> String.format("%.1f%%", clusterMetrics.getClusterCpuPercent()),
                clusterMetrics.clusterCpuPercentProperty()
        );

        this.memoryFormatted = Bindings.createStringBinding(() -> {
            double usedGb = clusterMetrics.getClusterUsedMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
            double totalGb = clusterMetrics.getClusterTotalMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
            return String.format("%.1f / %.1f GB", usedGb, totalGb);
        }, clusterMetrics.clusterUsedMemoryBytesProperty(), clusterMetrics.clusterTotalMemoryBytesProperty());

        this.workersFormatted = Bindings.createStringBinding(
                () -> String.valueOf(clusterMetrics.getOnlineWorkersCount()),
                clusterMetrics.onlineWorkersCountProperty()
        );

        this.tasksThroughputFormatted = Bindings.createStringBinding(
                () -> String.format("%d active | %d queued", clusterMetrics.getExecutingTasksCount(), clusterMetrics.getQueuedTasksCount()),
                clusterMetrics.executingTasksCountProperty(), clusterMetrics.queuedTasksCountProperty()
        );

        this.rttFormatted = Bindings.createStringBinding(
                () -> String.format("%.1f ms", clusterMetrics.getRttLatencyMs()),
                clusterMetrics.rttLatencyMsProperty()
        );
    }

    public ClusterMetrics getClusterMetrics() { return clusterMetrics; }
    public StringBinding cpuFormattedProperty() { return cpuFormatted; }
    public StringBinding memoryFormattedProperty() { return memoryFormatted; }
    public StringBinding workersFormattedProperty() { return workersFormatted; }
    public StringBinding tasksThroughputFormattedProperty() { return tasksThroughputFormatted; }
    public StringBinding rttFormattedProperty() { return rttFormatted; }
}
