package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.ClusterOverviewViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ClusterOverviewController {

    @FXML private Label onlineWorkersCountLabel;
    @FXML private Label cpuLoadLabel;
    @FXML private Label memoryUsedLabel;
    @FXML private Label activeTasksLabel;

    private ClusterOverviewViewModel viewModel;

    public void setViewModel(ClusterOverviewViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        if (viewModel != null) {
            bindViewModel();
        }
    }

    private void bindViewModel() {
        if (viewModel == null) return;
        var metrics = viewModel.getClusterMetrics();
        onlineWorkersCountLabel.textProperty().bind(metrics.onlineWorkersCountProperty().asString());
        cpuLoadLabel.textProperty().bind(metrics.clusterCpuPercentProperty().asString("%.1f%%"));
        activeTasksLabel.textProperty().bind(metrics.executingTasksCountProperty().asString());
    }
}
