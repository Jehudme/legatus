package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable cluster-wide aggregate metrics property store driving the Top Telemetry Ribbon.
 */
public class ClusterMetrics {

    private final DoubleProperty clusterCpuPercent = new SimpleDoubleProperty(0.0);
    private final LongProperty clusterUsedMemoryBytes = new SimpleLongProperty(0);
    private final LongProperty clusterTotalMemoryBytes = new SimpleLongProperty(0);
    private final IntegerProperty onlineWorkersCount = new SimpleIntegerProperty(0);
    private final IntegerProperty drainingWorkersCount = new SimpleIntegerProperty(0);
    private final IntegerProperty offlineWorkersCount = new SimpleIntegerProperty(0);
    private final IntegerProperty queuedTasksCount = new SimpleIntegerProperty(0);
    private final IntegerProperty executingTasksCount = new SimpleIntegerProperty(0);
    private final IntegerProperty tasksCompletedLast60s = new SimpleIntegerProperty(0);
    private final IntegerProperty tasksFailedLast60s = new SimpleIntegerProperty(0);
    private final DoubleProperty rttLatencyMs = new SimpleDoubleProperty(0.0);

    public DoubleProperty clusterCpuPercentProperty() { return clusterCpuPercent; }
    public double getClusterCpuPercent() { return clusterCpuPercent.get(); }
    public void setClusterCpuPercent(double value) { this.clusterCpuPercent.set(value); }

    public LongProperty clusterUsedMemoryBytesProperty() { return clusterUsedMemoryBytes; }
    public long getClusterUsedMemoryBytes() { return clusterUsedMemoryBytes.get(); }
    public void setClusterUsedMemoryBytes(long value) { this.clusterUsedMemoryBytes.set(value); }

    public LongProperty clusterTotalMemoryBytesProperty() { return clusterTotalMemoryBytes; }
    public long getClusterTotalMemoryBytes() { return clusterTotalMemoryBytes.get(); }
    public void setClusterTotalMemoryBytes(long value) { this.clusterTotalMemoryBytes.set(value); }

    public IntegerProperty onlineWorkersCountProperty() { return onlineWorkersCount; }
    public int getOnlineWorkersCount() { return onlineWorkersCount.get(); }
    public void setOnlineWorkersCount(int value) { this.onlineWorkersCount.set(value); }

    public IntegerProperty drainingWorkersCountProperty() { return drainingWorkersCount; }
    public int getDrainingWorkersCount() { return drainingWorkersCount.get(); }
    public void setDrainingWorkersCount(int value) { this.drainingWorkersCount.set(value); }

    public IntegerProperty offlineWorkersCountProperty() { return offlineWorkersCount; }
    public int getOfflineWorkersCount() { return offlineWorkersCount.get(); }
    public void setOfflineWorkersCount(int value) { this.offlineWorkersCount.set(value); }

    public IntegerProperty queuedTasksCountProperty() { return queuedTasksCount; }
    public int getQueuedTasksCount() { return queuedTasksCount.get(); }
    public void setQueuedTasksCount(int value) { this.queuedTasksCount.set(value); }

    public IntegerProperty executingTasksCountProperty() { return executingTasksCount; }
    public int getExecutingTasksCount() { return executingTasksCount.get(); }
    public void setExecutingTasksCount(int value) { this.executingTasksCount.set(value); }

    public IntegerProperty tasksCompletedLast60sProperty() { return tasksCompletedLast60s; }
    public int getTasksCompletedLast60s() { return tasksCompletedLast60s.get(); }
    public void setTasksCompletedLast60s(int value) { this.tasksCompletedLast60s.set(value); }

    public IntegerProperty tasksFailedLast60sProperty() { return tasksFailedLast60s; }
    public int getTasksFailedLast60s() { return tasksFailedLast60s.get(); }
    public void setTasksFailedLast60s(int value) { this.tasksFailedLast60s.set(value); }

    public DoubleProperty rttLatencyMsProperty() { return rttLatencyMs; }
    public double getRttLatencyMs() { return rttLatencyMs.get(); }
    public void setRttLatencyMs(double value) { this.rttLatencyMs.set(value); }
}
