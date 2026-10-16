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
    @FXML private javafx.scene.layout.HBox breachWarningBadge;
    @FXML private Label breachWarningLabel;
    @FXML private javafx.scene.layout.HBox queueErrorBadge;

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

        if (breachWarningBadge != null) {
            breachWarningBadge.visibleProperty().bind(viewModel.thresholdBreachWarningProperty());
            breachWarningBadge.managedProperty().bind(breachWarningBadge.visibleProperty());
        }

        if (queueErrorBadge != null) {
            queueErrorBadge.visibleProperty().bind(viewModel.queueSaturatedErrorProperty());
            queueErrorBadge.managedProperty().bind(queueErrorBadge.visibleProperty());
        }

        com.legatus.studio.service.StudioContext ctx = com.legatus.studio.service.StudioContext.getInstance();
        if (ctx.getMainViewModel() != null && statusPill != null) {
            ctx.getMainViewModel().connectionStatusTextProperty().addListener((obs, oldV, stateText) -> {
                if (stateText != null) {
                    if ("STREAMING".equalsIgnoreCase(stateText)) {
                        statusPill.setText("CONNECTED");
                        statusPill.getStyleClass().setAll("badge-online");
                    } else if ("CONNECTING".equalsIgnoreCase(stateText) || "NEGOTIATING".equalsIgnoreCase(stateText)) {
                        statusPill.setText("CONNECTING");
                        statusPill.getStyleClass().setAll("badge-draining");
                    } else {
                        statusPill.setText("DISCONNECTED");
                        statusPill.getStyleClass().setAll("badge-offline");
                    }
                }
            });
        }
    }
}
