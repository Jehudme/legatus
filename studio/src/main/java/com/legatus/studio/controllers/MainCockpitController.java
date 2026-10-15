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
        if (viewModel != null) {
            bindViewModel();
        } else {
            // Load default overview
            loadModuleView(MainViewModel.NavigationModule.OVERVIEW);
        }
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
        if (contentContainer == null) return;
        try {
            ViewLoaderService.switchView(contentContainer, module.getFxmlName());
        } catch (IOException e) {
            // Fallback
        }
    }

    public StackPane getContentContainer() {
        return contentContainer;
    }
}
