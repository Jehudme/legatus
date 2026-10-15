package com.legatus.studio.controllers;

import com.legatus.studio.model.WorkerNode;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.shape.Polyline;

public class WorkerCardCellController {

    @FXML private Label workerIdLabel;
    @FXML private Label hostnameLabel;
    @FXML private Label stateBadge;
    @FXML private Label cpuLabel;
    @FXML private Label memoryLabel;
    @FXML private Polyline sparklinePolyline;

    private WorkerNode worker;

    public void updateWorker(WorkerNode worker) {
        this.worker = worker;
        if (worker == null) return;

        workerIdLabel.textProperty().bind(worker.workerIdProperty());
        hostnameLabel.textProperty().bind(worker.hostnameProperty());
        stateBadge.textProperty().bind(worker.stateProperty());
        cpuLabel.textProperty().bind(worker.cpuLoadPctProperty().asString("%.1f%%"));

        updateSparkline();
        worker.getCpuHistory().addListener((ListChangeListener<Double>) c -> updateSparkline());
    }

    private void updateSparkline() {
        if (sparklinePolyline == null || worker == null) return;
        sparklinePolyline.getPoints().clear();

        var history = worker.getCpuHistory();
        if (history.isEmpty()) return;

        double width = 120.0;
        double height = 30.0;
        double step = history.size() > 1 ? width / (history.size() - 1) : width;

        for (int i = 0; i < history.size(); i++) {
            double x = i * step;
            double pct = Math.max(0.0, Math.min(100.0, history.get(i)));
            double y = height - (pct / 100.0 * height);
            sparklinePolyline.getPoints().addAll(x, y);
        }
    }
}
