package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.SettingsViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SettingsDialogController {

    @FXML private Label currentThemeLabel;
    @FXML private Button toggleThemeButton;
    @FXML private TextField timeoutField;
    @FXML private TextField heartbeatField;
    @FXML private TextField logLimitField;
    @FXML private Label configPathLabel;
    @FXML private Label logPathLabel;
    @FXML private Label statusLabel;

    private SettingsViewModel viewModel;

    public void setViewModel(SettingsViewModel viewModel) {
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

        currentThemeLabel.textProperty().bind(viewModel.currentThemeProperty().asString());
        configPathLabel.textProperty().bind(viewModel.configPathProperty());
        logPathLabel.textProperty().bind(viewModel.logPathProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    }

    @FXML
    private void onToggleThemeClicked() {
        if (viewModel != null) viewModel.toggleTheme();
    }

    @FXML
    private void onSaveClicked() {
        if (viewModel != null) viewModel.saveSettings();
    }
}
