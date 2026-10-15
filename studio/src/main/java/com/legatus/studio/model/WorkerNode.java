package com.legatus.studio.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Observable worker compute node model driving the Fleet Topology master-detail view.
 */
public class WorkerNode {

    private final StringProperty workerId = new SimpleStringProperty("");
    private final StringProperty hostname = new SimpleStringProperty("");
    private final StringProperty sourceIp = new SimpleStringProperty("");
    private final StringProperty os = new SimpleStringProperty("");
    private final StringProperty architecture = new SimpleStringProperty("");
    private final StringProperty state = new SimpleStringProperty("ONLINE");
    private final IntegerProperty allocatableCores = new SimpleIntegerProperty(0);
    private final LongProperty maxMemoryBytes = new SimpleLongProperty(0);
    private final DoubleProperty cpuLoadPct = new SimpleDoubleProperty(0.0);
    private final LongProperty memoryUsedBytes = new SimpleLongProperty(0);
    private final IntegerProperty activeTasksCount = new SimpleIntegerProperty(0);
    private final StringProperty lastHeartbeat = new SimpleStringProperty("");

    // Rolling 60-point history for live CPU sparkline chart
    private final ObservableList<Double> cpuHistory = FXCollections.observableArrayList();

    public WorkerNode() {}

    public WorkerNode(String workerId, String hostname, String sourceIp, String os, String architecture,
                      String state, int allocatableCores, long maxMemoryBytes, double cpuLoadPct,
                      long memoryUsedBytes, int activeTasksCount, String lastHeartbeat) {
        this.workerId.set(workerId);
        this.hostname.set(hostname);
        this.sourceIp.set(sourceIp);
        this.os.set(os);
        this.architecture.set(architecture);
        this.state.set(state);
        this.allocatableCores.set(allocatableCores);
        this.maxMemoryBytes.set(maxMemoryBytes);
        this.cpuLoadPct.set(cpuLoadPct);
        this.memoryUsedBytes.set(memoryUsedBytes);
        this.activeTasksCount.set(activeTasksCount);
        this.lastHeartbeat.set(lastHeartbeat);
        recordCpuPoint(cpuLoadPct);
    }

    public void recordCpuPoint(double pct) {
        if (cpuHistory.size() >= 60) {
            cpuHistory.remove(0);
        }
        cpuHistory.add(pct);
    }

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

    public StringProperty stateProperty() { return state; }
    public String getState() { return state.get(); }
    public void setState(String value) { this.state.set(value); }

    public IntegerProperty allocatableCoresProperty() { return allocatableCores; }
    public int getAllocatableCores() { return allocatableCores.get(); }
    public void setAllocatableCores(int value) { this.allocatableCores.set(value); }

    public LongProperty maxMemoryBytesProperty() { return maxMemoryBytes; }
    public long getMaxMemoryBytes() { return maxMemoryBytes.get(); }
    public void setMaxMemoryBytes(long value) { this.maxMemoryBytes.set(value); }

    public DoubleProperty cpuLoadPctProperty() { return cpuLoadPct; }
    public double getCpuLoadPct() { return cpuLoadPct.get(); }
    public void setCpuLoadPct(double value) {
        this.cpuLoadPct.set(value);
        recordCpuPoint(value);
    }

    public LongProperty memoryUsedBytesProperty() { return memoryUsedBytes; }
    public long getMemoryUsedBytes() { return memoryUsedBytes.get(); }
    public void setMemoryUsedBytes(long value) { this.memoryUsedBytes.set(value); }

    public IntegerProperty activeTasksCountProperty() { return activeTasksCount; }
    public int getActiveTasksCount() { return activeTasksCount.get(); }
    public void setActiveTasksCount(int value) { this.activeTasksCount.set(value); }

    public StringProperty lastHeartbeatProperty() { return lastHeartbeat; }
    public String getLastHeartbeat() { return lastHeartbeat.get(); }
    public void setLastHeartbeat(String value) { this.lastHeartbeat.set(value); }

    public ObservableList<Double> getCpuHistory() { return cpuHistory; }
}
