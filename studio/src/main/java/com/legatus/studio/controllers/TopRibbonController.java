package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.RibbonViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class TopRibbonController {

    @FXML private Label statusPill;
    @FXML private Label cpuLabel;
    @FXML private Label memoryLabel;
    @FXML private Label workersLabel;
    @FXML private Label tasksLabel;
    @FXML private Label rttLabel;

    private RibbonViewModel viewModel;

    public void setViewModel(RibbonViewModel viewModel) {
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
        cpuLabel.textProperty().bind(viewModel.cpuFormattedProperty());
        memoryLabel.textProperty().bind(viewModel.memoryFormattedProperty());
        workersLabel.textProperty().bind(viewModel.workersFormattedProperty());
        tasksLabel.textProperty().bind(viewModel.tasksThroughputFormattedProperty());
        rttLabel.textProperty().bind(viewModel.rttFormattedProperty());
    }
}
