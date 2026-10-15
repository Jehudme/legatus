package com.legatus.studio.controllers;

import com.legatus.studio.viewmodel.DrawerViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class DrawerController {

    @FXML private ListView<String> consoleListView;
    @FXML private TextField filterField;
    @FXML private CheckBox autoScrollCheckBox;

    private DrawerViewModel viewModel;

    public void setViewModel(DrawerViewModel viewModel) {
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
        consoleListView.setItems(viewModel.getFilteredConsoleLines());
        autoScrollCheckBox.selectedProperty().bindBidirectional(viewModel.isAutoScrollProperty());
        filterField.textProperty().bindBidirectional(viewModel.filterTextProperty());

        // Auto-scroll when new console log lines arrive
        viewModel.getFilteredConsoleLines().addListener((javafx.collections.ListChangeListener<String>) c -> {
            if (viewModel.isAutoScrollProperty().get() && !viewModel.getFilteredConsoleLines().isEmpty()) {
                consoleListView.scrollTo(viewModel.getFilteredConsoleLines().size() - 1);
            }
        });
    }

    @FXML
    private void onClearClicked() {
        if (viewModel != null) viewModel.clearConsole();
    }
}
