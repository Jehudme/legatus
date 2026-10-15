package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class TitleBarController {

    @FXML private Label titleLabel;
    @FXML private Label profileLabel;

    private MainViewModel viewModel;

    public void setViewModel(MainViewModel viewModel) {
        this.viewModel = viewModel;
        if (profileLabel != null && viewModel != null) {
            profileLabel.textProperty().bind(viewModel.activeProfileNameProperty());
        }
    }

    @FXML
    private void onMinimizeClicked() {
        Stage stage = getStage();
        if (stage != null) stage.setIconified(true);
    }

    @FXML
    private void onMaximizeClicked() {
        Stage stage = getStage();
        if (stage != null) stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void onCloseClicked() {
        Platform.exit();
    }

    private Stage getStage() {
        if (titleLabel != null && titleLabel.getScene() != null) {
            return (Stage) titleLabel.getScene().getWindow();
        }
        return null;
    }
}
