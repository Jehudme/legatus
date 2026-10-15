package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.MainViewModel;
import com.legatus.studio.viewmodel.NavigationViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class NavigationRailController {

    @FXML private Button overviewBtn;
    @FXML private Button fleetBtn;
    @FXML private Button quarantineBtn;
    @FXML private Label quarantineBadge;
    @FXML private Button functionsBtn;
    @FXML private Button tasksBtn;
    @FXML private Button secretsBtn;
    @FXML private Button usersBtn;
    @FXML private Button auditBtn;
    @FXML private Button settingsBtn;

    private NavigationViewModel viewModel;

    public void setViewModel(NavigationViewModel viewModel) {
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
        quarantineBadge.textProperty().bind(viewModel.quarantineBadgeCountProperty().asString());
        quarantineBadge.visibleProperty().bind(viewModel.quarantineBadgeCountProperty().greaterThan(0));
    }

    @FXML private void onOverviewClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.OVERVIEW); }
    @FXML private void onFleetClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.FLEET); }
    @FXML private void onQuarantineClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.QUARANTINE); }
    @FXML private void onFunctionsClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.FUNCTIONS); }
    @FXML private void onTasksClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.TASKS); }
    @FXML private void onSecretsClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.SECRETS); }
    @FXML private void onUsersClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.USERS); }
    @FXML private void onAuditClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.AUDIT); }
    @FXML private void onSettingsClicked() { if (viewModel != null) viewModel.selectModule(MainViewModel.NavigationModule.SETTINGS); }
}
