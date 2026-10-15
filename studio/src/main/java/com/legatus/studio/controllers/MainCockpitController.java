package com.legatus.studio.controllers;

import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainCockpitController {

    @FXML private StackPane contentContainer;
    @FXML private TitleBarController titleBarController;
    @FXML private TopRibbonController topRibbonController;
    @FXML private NavigationRailController navigationRailController;
    @FXML private DrawerController drawerController;

    private MainViewModel viewModel;

    public void setViewModel(MainViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        com.legatus.studio.service.StudioContext ctx = com.legatus.studio.service.StudioContext.getInstance();
        if (viewModel == null) {
            this.viewModel = ctx.getMainViewModel();
        }

        if (topRibbonController != null && ctx.getTelemetryService() != null) {
            topRibbonController.setViewModel(new com.legatus.studio.viewmodel.RibbonViewModel(
                    ctx.getTelemetryService().getClusterMetrics()));
        }

        if (navigationRailController != null) {
            if (ctx.getTelemetryService() != null) {
                navigationRailController.setViewModel(new com.legatus.studio.viewmodel.NavigationViewModel(
                        viewModel, ctx.getTelemetryService().getEnrollmentsList()));
            } else {
                navigationRailController.setViewModel(new com.legatus.studio.viewmodel.NavigationViewModel(viewModel));
            }
        }

        if (drawerController != null && ctx.getLogStreamBuffer() != null) {
            drawerController.setViewModel(new com.legatus.studio.viewmodel.DrawerViewModel(ctx.getLogStreamBuffer()));
        }

        bindViewModel();
    }

    private void bindViewModel() {
        if (viewModel == null) return;
        viewModel.currentModuleProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                loadModuleView(newV);
            }
        });
        loadModuleView(viewModel.getCurrentModule());
    }

    private void loadModuleView(MainViewModel.NavigationModule module) {
        if (contentContainer == null || module == null) return;
        com.legatus.studio.service.StudioContext ctx = com.legatus.studio.service.StudioContext.getInstance();
        try {
            switch (module) {
                case OVERVIEW -> {
                    ClusterOverviewController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getTelemetryService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.ClusterOverviewViewModel(
                                ctx.getTelemetryService().getClusterMetrics(),
                                ctx.getTelemetryService().getWorkersList(),
                                ctx.getTelemetryService().getTasksList()
                        ));
                    }
                }
                case FLEET -> {
                    FleetTopologyController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getTelemetryService() != null && ctx.getWorkerService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.FleetViewModel(
                                ctx.getTelemetryService().getWorkersList(), ctx.getWorkerService()));
                    }
                }
                case QUARANTINE -> {
                    QuarantineAttestationController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getTelemetryService() != null && ctx.getWorkerService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.QuarantineViewModel(
                                ctx.getTelemetryService().getEnrollmentsList(), ctx.getWorkerService()));
                    }
                }
                case FUNCTIONS -> {
                    FunctionsWorkbenchController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getBytecodeService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.FunctionsViewModel(ctx.getBytecodeService()));
                    }
                }
                case TASKS -> {
                    TaskExplorerController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getTelemetryService() != null && ctx.getTaskService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.TasksViewModel(
                                ctx.getTelemetryService().getTasksList(), ctx.getTaskService()));
                    }
                }
                case SECRETS -> {
                    SecretsVaultController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getSecretsService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.SecretsViewModel(ctx.getSecretsService()));
                    }
                }
                case USERS -> {
                    AdminUsersController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getUserManagementService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.UserManagementViewModel(ctx.getUserManagementService()));
                    }
                }
                case AUDIT -> {
                    AuditLogController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null && ctx.getAuditLogService() != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.AuditTrailViewModel(ctx.getAuditLogService()));
                    }
                }
                case SETTINGS -> {
                    SettingsDialogController c = ViewLoaderService.switchView(contentContainer, module.getFxmlName());
                    if (c != null) {
                        c.setViewModel(new com.legatus.studio.viewmodel.SettingsViewModel(new com.legatus.studio.config.ConfigStore()));
                    }
                }
                default -> ViewLoaderService.switchView(contentContainer, module.getFxmlName());
            }
        } catch (IOException e) {
            // Fallback
        }
    }

    public StackPane getContentContainer() {
        return contentContainer;
    }
}
