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

    @FXML private Label selectedTargetLabel;
    @FXML private TextArea argumentsArea;
    @FXML private TextField budgetField;
    @FXML private TextField memoryField;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private Button executeButton;
    @FXML private Label executionStatusLabel;

    private FunctionsViewModel viewModel;

    public void setViewModel(FunctionsViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        if (priorityCombo != null) {
            priorityCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                    "NORMAL", "HIGH", "LOW", "CRITICAL", "BATCH"
            ));
            priorityCombo.setValue("NORMAL");
        }
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

        // Update selected bytecode on row selection
        bytecodeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.setSelectedBytecode(newV);
            }
        });

        viewModel.selectedBytecodeProperty().addListener((obs, oldV, newV) -> {
            if (selectedTargetLabel != null) {
                if (newV != null) {
                    String sha = newV.getSha256();
                    String shortSha = sha.length() > 16 ? sha.substring(0, 16) + "..." : sha;
                    selectedTargetLabel.setText(newV.getFunctionName() + " [" + newV.getEntryPoint() + "] (" + shortSha + ")");
                } else {
                    selectedTargetLabel.setText("Select a function from the table or upload bytecode.");
                }
            }
        });

        // Initialize target label if already selected
        if (selectedTargetLabel != null && viewModel.getSelectedBytecode() != null) {
            BytecodeItem curr = viewModel.getSelectedBytecode();
            String sha = curr.getSha256();
            String shortSha = sha.length() > 16 ? sha.substring(0, 16) + "..." : sha;
            selectedTargetLabel.setText(curr.getFunctionName() + " [" + curr.getEntryPoint() + "] (" + shortSha + ")");
        }

        filePathLabel.textProperty().bind(viewModel.selectedFilePathProperty());
        sha256Label.textProperty().bind(viewModel.calculatedSha256Property());
        functionNameField.textProperty().bindBidirectional(viewModel.functionNameInputProperty());
        entrypointField.textProperty().bindBidirectional(viewModel.entrypointInputProperty());
        uploadButton.disableProperty().bind(viewModel.isUploadingProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());

        if (argumentsArea != null) {
            argumentsArea.textProperty().bindBidirectional(viewModel.argumentsJsonInputProperty());
        }
        if (budgetField != null) {
            budgetField.textProperty().bindBidirectional(viewModel.instructionBudgetInputProperty());
        }
        if (memoryField != null) {
            memoryField.textProperty().bindBidirectional(viewModel.memoryLimitMbInputProperty());
        }
        if (priorityCombo != null) {
            priorityCombo.valueProperty().bindBidirectional(viewModel.taskPriorityInputProperty());
        }
        if (executeButton != null) {
            executeButton.disableProperty().bind(viewModel.isExecutingProperty());
        }
        if (executionStatusLabel != null) {
            executionStatusLabel.textProperty().bind(viewModel.executionStatusMessageProperty());
        }
    }

    @FXML
    private void onRefreshCatalogClicked() {
        if (viewModel != null) viewModel.loadCatalogedFunctions();
    }

    @FXML
    private void onExecuteClicked() {
        if (viewModel != null) viewModel.executeSelectedFunction();
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
