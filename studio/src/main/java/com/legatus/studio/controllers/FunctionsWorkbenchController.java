package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;

public class FunctionsWorkbenchController {

    @FXML private TableView<BytecodeItem> bytecodeTable;
    @FXML private TableColumn<BytecodeItem, String> functionNameCol;
    @FXML private TableColumn<BytecodeItem, String> sha256Col;
    @FXML private TableColumn<BytecodeItem, Number> sizeCol;

    @FXML private Label filePathLabel;
    @FXML private Label sha256Label;
    @FXML private TextField functionNameField;
    @FXML private TextField entrypointField;
    @FXML private Button uploadButton;
    @FXML private Label statusLabel;

    private FunctionsViewModel viewModel;

    public void setViewModel(FunctionsViewModel viewModel) {
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

        bytecodeTable.setItems(viewModel.getBytecodeItems());
        functionNameCol.setCellValueFactory(c -> c.getValue().functionNameProperty());
        sha256Col.setCellValueFactory(c -> c.getValue().sha256Property());
        sizeCol.setCellValueFactory(c -> c.getValue().sizeBytesProperty());

        filePathLabel.textProperty().bind(viewModel.selectedFilePathProperty());
        sha256Label.textProperty().bind(viewModel.calculatedSha256Property());
        functionNameField.textProperty().bindBidirectional(viewModel.functionNameInputProperty());
        entrypointField.textProperty().bindBidirectional(viewModel.entrypointInputProperty());
        uploadButton.disableProperty().bind(viewModel.isUploadingProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    }

    @FXML
    private void onChooseFileClicked() {
        if (viewModel == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Solix Bytecode (.slxb)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Solix Bytecode (*.slxb)", "*.slxb", "*.*"));
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            try {
                viewModel.inspectLocalFile(file.toPath());
            } catch (Exception e) {
                statusLabel.setText("Failed to inspect file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onUploadClicked() {
        if (viewModel != null) viewModel.executeUpload();
    }
}
