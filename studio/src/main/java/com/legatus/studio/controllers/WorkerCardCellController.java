package com.legatus.studio.controllers;

import com.legatus.studio.model.WorkerNode;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.Polyline;

public class WorkerCardCellController {

    @FXML private Label workerIdLabel;
    @FXML private Label hostnameLabel;
    @FXML private Label ipLabel;
    @FXML private Label stateBadge;
    @FXML private Label cpuLabel;
    @FXML private ProgressBar cpuProgressBar;
    @FXML private Label memoryLabel;
    @FXML private ProgressBar memoryProgressBar;
    @FXML private Label tasksBadge;
    @FXML private Polyline sparklinePolyline;

    private WorkerNode worker;

    public void updateWorker(WorkerNode worker) {
        this.worker = worker;
        if (worker == null) return;

        // Display friendly name if available, otherwise fallback to workerId
        var nameBinding = javafx.beans.binding.Bindings.createStringBinding(() -> {
            String fn = worker.getFriendlyName();
            if (fn != null && !fn.isBlank()) return fn;
            return worker.getWorkerId();
        }, worker.friendlyNameProperty(), worker.workerIdProperty());
        workerIdLabel.textProperty().bind(nameBinding);

        hostnameLabel.textProperty().bind(worker.hostnameProperty());
        if (ipLabel != null) {
            ipLabel.textProperty().bind(worker.sourceIpProperty());
        }

        stateBadge.textProperty().bind(worker.stateProperty());
        worker.stateProperty().addListener((obs, oldV, state) -> updateStateBadgeStyle(state));
        updateStateBadgeStyle(worker.getState());

        cpuLabel.textProperty().bind(worker.cpuLoadPctProperty().asString("%.1f%%"));
        if (cpuProgressBar != null) {
            var cpuProgressBinding = javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.max(0.0, Math.min(1.0, worker.getCpuLoadPct() / 100.0)),
                    worker.cpuLoadPctProperty()
            );
            cpuProgressBar.progressProperty().bind(cpuProgressBinding);
        }

        if (memoryLabel != null) {
            var memBinding = javafx.beans.binding.Bindings.createStringBinding(() -> {
                double usedGb = worker.getMemoryUsedBytes() / (1024.0 * 1024.0 * 1024.0);
                double maxGb = worker.getMaxMemoryBytes() / (1024.0 * 1024.0 * 1024.0);
                return String.format("%.1f / %.1f GB", usedGb, maxGb);
            }, worker.memoryUsedBytesProperty(), worker.maxMemoryBytesProperty());
            memoryLabel.textProperty().bind(memBinding);
        }

        if (memoryProgressBar != null) {
            var memProgressBinding = javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                long max = worker.getMaxMemoryBytes();
                if (max <= 0) return 0.0;
                return Math.max(0.0, Math.min(1.0, (double) worker.getMemoryUsedBytes() / (double) max));
            }, worker.memoryUsedBytesProperty(), worker.maxMemoryBytesProperty());
            memoryProgressBar.progressProperty().bind(memProgressBinding);
        }

        if (tasksBadge != null) {
            var taskBinding = javafx.beans.binding.Bindings.createStringBinding(
                    () -> worker.getActiveTasksCount() + " running",
                    worker.activeTasksCountProperty()
            );
            tasksBadge.textProperty().bind(taskBinding);
        }

        updateSparkline();
        worker.getCpuHistory().addListener((ListChangeListener<Double>) c -> updateSparkline());
    }

    private void updateStateBadgeStyle(String state) {
        if (stateBadge == null || state == null) return;
        if ("ONLINE".equalsIgnoreCase(state)) {
            stateBadge.getStyleClass().setAll("badge-online");
        } else if ("INACTIVE".equalsIgnoreCase(state)) {
            stateBadge.getStyleClass().setAll("badge-inactive");
        } else if ("DRAINING".equalsIgnoreCase(state)) {
            stateBadge.getStyleClass().setAll("badge-draining");
        } else {
            stateBadge.getStyleClass().setAll("badge-offline");
        }
    }

    private void updateSparkline() {
        if (sparklinePolyline == null || worker == null) return;
        sparklinePolyline.getPoints().clear();

        var history = worker.getCpuHistory();
        if (history.isEmpty()) return;

        double width = 80.0;
        double height = 20.0;
        double step = history.size() > 1 ? width / (history.size() - 1) : width;

        for (int i = 0; i < history.size(); i++) {
            double x = i * step;
            double pct = Math.max(0.0, Math.min(100.0, history.get(i)));
            double y = height - (pct / 100.0 * height);
            sparklinePolyline.getPoints().addAll(x, y);
        }
    }

    public Label getWorkerIdLabel() { return workerIdLabel; }
    public Label getHostnameLabel() { return hostnameLabel; }
    public Label getIpLabel() { return ipLabel; }
    public Label getStateBadge() { return stateBadge; }
    public Label getCpuLabel() { return cpuLabel; }
    public ProgressBar getCpuProgressBar() { return cpuProgressBar; }
    public Label getMemoryLabel() { return memoryLabel; }
    public ProgressBar getMemoryProgressBar() { return memoryProgressBar; }
    public Label getTasksBadge() { return tasksBadge; }
    public Polyline getSparklinePolyline() { return sparklinePolyline; }
}
