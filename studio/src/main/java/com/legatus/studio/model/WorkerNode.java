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

    private final StringProperty friendlyName = new SimpleStringProperty("");
    private final BooleanProperty active = new SimpleBooleanProperty(true);
    private final DoubleProperty targetCpuPct = new SimpleDoubleProperty(75.0);
    private final DoubleProperty targetRamPct = new SimpleDoubleProperty(80.0);
    private final IntegerProperty maxConcurrentTasks = new SimpleIntegerProperty(16);
    private final BooleanProperty autoRenewLease = new SimpleBooleanProperty(true);
    private final LongProperty remainingLeaseSec = new SimpleLongProperty(86400L);
    private final LongProperty uptimeSeconds = new SimpleLongProperty(0L);
    private final StringProperty createdAt = new SimpleStringProperty("");

    // Rolling 60-point history for live CPU sparkline chart
    private final ObservableList<Double> cpuHistory = FXCollections.observableArrayList();

    public WorkerNode() {}

    public WorkerNode(String workerId, String hostname, String sourceIp, String os, String architecture,
                      String state, int allocatableCores, long maxMemoryBytes, double cpuLoadPct,
                      long memoryUsedBytes, int activeTasksCount, String lastHeartbeat) {
        this(workerId, "", hostname, sourceIp, os, architecture, state, true,
                allocatableCores, maxMemoryBytes, 75.0, 80.0, 16, true, 86400L, 0L,
                cpuLoadPct, memoryUsedBytes, activeTasksCount, lastHeartbeat, "");
    }

    public WorkerNode(String workerId, String friendlyName, String hostname, String sourceIp, String os,
                      String architecture, String state, boolean active, int allocatableCores,
                      long maxMemoryBytes, double targetCpuPct, double targetRamPct,
                      int maxConcurrentTasks, boolean autoRenewLease, long remainingLeaseSec,
                      long uptimeSeconds, double cpuLoadPct, long memoryUsedBytes,
                      int activeTasksCount, String lastHeartbeat, String createdAt) {
        this.workerId.set(workerId != null ? workerId : "");
        this.friendlyName.set(friendlyName != null ? friendlyName : "");
        this.hostname.set(hostname != null ? hostname : "");
        this.sourceIp.set(sourceIp != null ? sourceIp : "");
        this.os.set(os != null ? os : "");
        this.architecture.set(architecture != null ? architecture : "");
        this.state.set(state != null ? state : "ONLINE");
        this.active.set(active);
        this.allocatableCores.set(allocatableCores);
        this.maxMemoryBytes.set(maxMemoryBytes);
        this.targetCpuPct.set(targetCpuPct);
        this.targetRamPct.set(targetRamPct);
        this.maxConcurrentTasks.set(maxConcurrentTasks);
        this.autoRenewLease.set(autoRenewLease);
        this.remainingLeaseSec.set(remainingLeaseSec);
        this.uptimeSeconds.set(uptimeSeconds);
        this.cpuLoadPct.set(cpuLoadPct);
        this.memoryUsedBytes.set(memoryUsedBytes);
        this.activeTasksCount.set(activeTasksCount);
        this.lastHeartbeat.set(lastHeartbeat != null ? lastHeartbeat : "");
        this.createdAt.set(createdAt != null ? createdAt : "");
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

    public StringProperty friendlyNameProperty() { return friendlyName; }
    public String getFriendlyName() { return friendlyName.get(); }
    public void setFriendlyName(String value) { this.friendlyName.set(value); }

    public BooleanProperty activeProperty() { return active; }
    public boolean isActive() { return active.get(); }
    public void setActive(boolean value) { this.active.set(value); }

    public DoubleProperty targetCpuPctProperty() { return targetCpuPct; }
    public double getTargetCpuPct() { return targetCpuPct.get(); }
    public void setTargetCpuPct(double value) { this.targetCpuPct.set(value); }

    public DoubleProperty targetRamPctProperty() { return targetRamPct; }
    public double getTargetRamPct() { return targetRamPct.get(); }
    public void setTargetRamPct(double value) { this.targetRamPct.set(value); }

    public IntegerProperty maxConcurrentTasksProperty() { return maxConcurrentTasks; }
    public int getMaxConcurrentTasks() { return maxConcurrentTasks.get(); }
    public void setMaxConcurrentTasks(int value) { this.maxConcurrentTasks.set(value); }

    public BooleanProperty autoRenewLeaseProperty() { return autoRenewLease; }
    public boolean isAutoRenewLease() { return autoRenewLease.get(); }
    public void setAutoRenewLease(boolean value) { this.autoRenewLease.set(value); }

    public LongProperty remainingLeaseSecProperty() { return remainingLeaseSec; }
    public long getRemainingLeaseSec() { return remainingLeaseSec.get(); }
    public void setRemainingLeaseSec(long value) { this.remainingLeaseSec.set(value); }

    public LongProperty uptimeSecondsProperty() { return uptimeSeconds; }
    public long getUptimeSeconds() { return uptimeSeconds.get(); }
    public void setUptimeSeconds(long value) { this.uptimeSeconds.set(value); }

    public StringProperty createdAtProperty() { return createdAt; }
    public String getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(String value) { this.createdAt.set(value); }

    public String getDisplayName() {
        String fn = friendlyName.get();
        if (fn != null && !fn.isBlank()) return fn;
        String hn = hostname.get();
        if (hn != null && !hn.isBlank()) return hn;
        return workerId.get();
    }

    public String getFormattedUptime() {
        long sec = uptimeSeconds.get();
        if (sec <= 0) return "< 1m";
        long days = sec / 86400;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, mins);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, mins);
        } else {
            return String.format("%dm %ds", mins, sec % 60);
        }
    }

    public String getFormattedLeaseRemaining() {
        long sec = remainingLeaseSec.get();
        if (sec <= 0) return "Expired";
        long hours = sec / 3600;
        long mins = (sec % 3600) / 60;
        long s = sec % 60;
        if (hours > 0) {
            return String.format("%dh %dm", hours, mins);
        } else {
            return String.format("%dm %ds", mins, s);
        }
    }

    public ObservableList<Double> getCpuHistory() { return cpuHistory; }
}
