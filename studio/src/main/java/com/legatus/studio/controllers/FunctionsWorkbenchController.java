package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.service.FxAsync;
import com.legatus.studio.service.StudioContext;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FunctionsWorkbenchController {

    @FXML private TextField tagFilterField;
    @FXML private TableView<BytecodeItem> bytecodeTable;
    @FXML private TableColumn<BytecodeItem, String> functionNameCol;
    @FXML private TableColumn<BytecodeItem, String> tagsCol;
    @FXML private TableColumn<BytecodeItem, String> sha256Col;
    @FXML private TableColumn<BytecodeItem, String> createdAtCol;
    @FXML private Button newFunctionButton;

    // Dedicated Inspector
    @FXML private VBox inspectorContainer;
    @FXML private Label inspectorFunctionName;
    @FXML private Label inspectorSha256;
    @FXML private Label inspectorCreatedAt;
    @FXML private TextArea descriptionArea;
    @FXML private FlowPane tagsChipPane;
    @FXML private TextField newTagField;
    @FXML private Button saveMetadataButton;
    @FXML private Button openInWorkbenchButton;
    @FXML private Label metadataStatusLabel;

    // Modal Dialog: Register & Deploy New Function
    @FXML private VBox newFunctionModal;
    @FXML private VBox dropZoneBox;
    @FXML private Button chooseFileButton;
    @FXML private Label filePathLabel;
    @FXML private Label sha256Label;
    @FXML private Label magicValidationLabel;
    @FXML private TextField functionNameField;
    @FXML private TextField entrypointField;
    @FXML private Button cancelModalButton;
    @FXML private Button uploadButton;
    @FXML private Label statusLabel;

    private FunctionsViewModel viewModel;
    private FilteredList<BytecodeItem> filteredBytecodeItems;

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

        filteredBytecodeItems = new FilteredList<>(viewModel.getBytecodeItems(), p -> true);
        SortedList<BytecodeItem> sorted = new SortedList<>(filteredBytecodeItems);
        if (bytecodeTable != null) {
            sorted.comparatorProperty().bind(bytecodeTable.comparatorProperty());
            bytecodeTable.setItems(sorted);
        }

        if (functionNameCol != null) functionNameCol.setCellValueFactory(c -> c.getValue().functionNameProperty());
        if (tagsCol != null) tagsCol.setCellValueFactory(c -> c.getValue().tagsProperty());
        if (sha256Col != null) sha256Col.setCellValueFactory(c -> c.getValue().sha256Property());
        if (createdAtCol != null) createdAtCol.setCellValueFactory(c -> c.getValue().uploadedAtProperty());

        // Tag / name / digest filter listener
        if (tagFilterField != null) {
            tagFilterField.textProperty().addListener((obs, oldV, filterText) -> {
                if (filterText == null || filterText.isBlank()) {
                    filteredBytecodeItems.setPredicate(p -> true);
                } else {
                    String lower = filterText.trim().toLowerCase();
                    filteredBytecodeItems.setPredicate(item -> {
                        boolean nameMatch = item.getFunctionName() != null && item.getFunctionName().toLowerCase().contains(lower);
                        boolean tagMatch = item.getTags() != null && item.getTags().toLowerCase().contains(lower);
                        boolean shaMatch = item.getSha256() != null && item.getSha256().toLowerCase().contains(lower);
                        return nameMatch || tagMatch || shaMatch;
                    });
                }
            });
        }

        // Selection binding
        if (bytecodeTable != null) {
            bytecodeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
                viewModel.setSelectedBytecode(selected);
                displaySelectedMetadata(selected);
            });

            // Initialize with currently selected item if any
            if (viewModel.getSelectedBytecode() != null) {
                bytecodeTable.getSelectionModel().select(viewModel.getSelectedBytecode());
                displaySelectedMetadata(viewModel.getSelectedBytecode());
            } else if (!viewModel.getBytecodeItems().isEmpty()) {
                bytecodeTable.getSelectionModel().select(0);
                displaySelectedMetadata(viewModel.getBytecodeItems().get(0));
            }
        }

        if (filePathLabel != null) filePathLabel.textProperty().bind(viewModel.selectedFilePathProperty());
        if (sha256Label != null) sha256Label.textProperty().bind(viewModel.calculatedSha256Property());
        if (functionNameField != null) functionNameField.textProperty().bindBidirectional(viewModel.functionNameInputProperty());
        if (entrypointField != null) entrypointField.textProperty().bindBidirectional(viewModel.entrypointInputProperty());
        if (uploadButton != null) {
            uploadButton.disableProperty().bind(
                    viewModel.isUploadingProperty()
                            .or(viewModel.isValidMagicHeaderProperty().not())
                            .or(viewModel.selectedFilePathProperty().isEmpty())
            );
        }
        if (statusLabel != null) statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    }

    private void displaySelectedMetadata(BytecodeItem item) {
        if (item == null) {
            if (inspectorFunctionName != null) inspectorFunctionName.setText("Select a function from catalog");
            if (inspectorSha256 != null) inspectorSha256.setText("--");
            if (inspectorCreatedAt != null) inspectorCreatedAt.setText("--");
            if (descriptionArea != null) descriptionArea.setText("");
            if (tagsChipPane != null) tagsChipPane.getChildren().clear();
            if (openInWorkbenchButton != null) openInWorkbenchButton.setDisable(true);
            if (saveMetadataButton != null) saveMetadataButton.setDisable(true);
            return;
        }

        if (openInWorkbenchButton != null) openInWorkbenchButton.setDisable(false);
        if (saveMetadataButton != null) saveMetadataButton.setDisable(false);

        if (inspectorFunctionName != null) inspectorFunctionName.setText(item.getFunctionName());
        if (inspectorSha256 != null) inspectorSha256.setText(item.getSha256());
        if (inspectorCreatedAt != null) inspectorCreatedAt.setText(item.getUploadedAt().isBlank() ? "N/A" : item.getUploadedAt());
        if (descriptionArea != null) descriptionArea.setText(item.getDescription());

        renderTagChips(item.getTags());
    }

    private void renderTagChips(String tagsStr) {
        if (tagsChipPane == null) return;
        tagsChipPane.getChildren().clear();
        if (tagsStr == null || tagsStr.isBlank()) {
            return;
        }

        String[] tags = tagsStr.split("[,; ]+");
        for (String tag : tags) {
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) continue;

            HBox chip = new HBox(4);
            chip.setAlignment(javafx.geometry.Pos.CENTER);
            chip.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2); -fx-background-radius: 12; -fx-padding: 3 8 3 8;");

            Label tagLabel = new Label(trimmed);
            tagLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 11px; -fx-font-weight: bold;");

            Button delBtn = new Button("×");
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-padding: 0 0 0 2; -fx-font-size: 11px; -fx-cursor: hand;");
            delBtn.setOnAction(e -> removeTag(trimmed));

            chip.getChildren().addAll(tagLabel, delBtn);
            tagsChipPane.getChildren().add(chip);
        }
    }

    private void removeTag(String tagToRemove) {
        if (bytecodeTable == null) return;
        BytecodeItem current = bytecodeTable.getSelectionModel().getSelectedItem();
        if (current == null) return;

        List<String> remaining = new ArrayList<>();
        if (current.getTags() != null) {
            for (String t : current.getTags().split("[,; ]+")) {
                if (!t.trim().isEmpty() && !t.trim().equalsIgnoreCase(tagToRemove)) {
                    remaining.add(t.trim());
                }
            }
        }
        String updatedTags = String.join(",", remaining);
        current.setTags(updatedTags);
        renderTagChips(updatedTags);
    }

    @FXML
    private void onAddTagClicked() {
        if (bytecodeTable == null) return;
        BytecodeItem current = bytecodeTable.getSelectionModel().getSelectedItem();
        if (current == null || newTagField == null || newTagField.getText().isBlank()) return;

        String newTag = newTagField.getText().trim();
        List<String> list = new ArrayList<>();
        if (current.getTags() != null && !current.getTags().isBlank()) {
            list.addAll(Arrays.asList(current.getTags().split("[,; ]+")));
        }
        if (!list.contains(newTag)) {
            list.add(newTag);
        }
        String updatedTags = String.join(",", list);
        current.setTags(updatedTags);
        renderTagChips(updatedTags);
        newTagField.clear();
    }

    @FXML
    private void onSaveMetadataClicked() {
        if (bytecodeTable == null) return;
        BytecodeItem current = bytecodeTable.getSelectionModel().getSelectedItem();
        if (current == null || viewModel == null) return;

        String desc = descriptionArea != null ? descriptionArea.getText() : "";
        String tags = current.getTags();
        current.setDescription(desc);

        if (current.getFunctionId() != null) {
            if (metadataStatusLabel != null) metadataStatusLabel.setText("Saving metadata...");
            viewModel.updateFunctionMetadata(current.getFunctionId(), desc, tags)
                    .thenRun(() -> {
                        if (metadataStatusLabel != null) metadataStatusLabel.setText("Metadata saved successfully!");
                    })
                    .exceptionally(ex -> {
                        if (metadataStatusLabel != null) metadataStatusLabel.setText("Failed to save: " + ex.getMessage());
                        return null;
                    });
        } else {
            if (metadataStatusLabel != null) metadataStatusLabel.setText("Local metadata updated.");
        }
    }

    @FXML
    private void onOpenInWorkbenchClicked() {
        if (bytecodeTable == null) return;
        BytecodeItem current = bytecodeTable.getSelectionModel().getSelectedItem();
        if (current == null) return;

        StudioContext ctx = StudioContext.getInstance();
        ctx.getMainViewModel().setTargetFunctionForExecution(current);
        ctx.getMainViewModel().navigateTo(MainViewModel.NavigationModule.EXECUTE);
    }

    @FXML
    private void onClearFilterClicked() {
        if (tagFilterField != null) {
            tagFilterField.clear();
        }
    }

    // Modal Lifecycle
    @FXML
    public void onOpenNewFunctionModal() {
        if (viewModel != null) {
            viewModel.selectedFilePathProperty().set("");
            viewModel.calculatedSha256Property().set("");
            viewModel.functionNameInputProperty().set("");
            viewModel.entrypointInputProperty().set("main");
            viewModel.statusMessageProperty().set("");
        }
        if (magicValidationLabel != null) {
            magicValidationLabel.setText("");
        }
        if (newFunctionModal != null) {
            newFunctionModal.setVisible(true);
            newFunctionModal.setManaged(true);
        }
    }

    @FXML
    public void onCloseNewFunctionModal() {
        if (newFunctionModal != null) {
            newFunctionModal.setVisible(false);
            newFunctionModal.setManaged(false);
        }
    }

    @FXML
    public void onFileDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    public void onFileDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            inspectAndValidateFile(file);
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    @FXML
    public void onChooseFileClicked() {
        if (viewModel == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Solix Bytecode (.slxb)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Solix Bytecode (*.slxb, *.solx)", "*.slxb", "*.solx", "*.*"));
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            inspectAndValidateFile(file);
        }
    }

    public void inspectAndValidateFile(File file) {
        if (viewModel == null || file == null) return;
        try {
            viewModel.inspectLocalFile(file.toPath());
            updateMagicValidationLabel();
        } catch (Exception e) {
            viewModel.statusMessageProperty().set("Failed to inspect file: " + e.getMessage());
            if (magicValidationLabel != null) {
                magicValidationLabel.setText("Selected file is not valid compiled Solix bytecode.");
                magicValidationLabel.setStyle("-fx-text-fill: #ef4444;");
            }
        }
    }

    private void updateMagicValidationLabel() {
        if (magicValidationLabel == null || viewModel == null) return;
        if (viewModel.isValidMagicHeader()) {
            magicValidationLabel.setText("✓ Valid Solix Bytecode Header (SOLX)");
            magicValidationLabel.setStyle("-fx-text-fill: #34d399;");
        } else {
            magicValidationLabel.setText("Selected file is not valid compiled Solix bytecode.");
            magicValidationLabel.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    @FXML
    public void onUploadClicked() {
        if (viewModel == null) return;
        if (!viewModel.isValidMagicHeader()) {
            if (magicValidationLabel != null) {
                magicValidationLabel.setText("Selected file is not valid compiled Solix bytecode.");
                magicValidationLabel.setStyle("-fx-text-fill: #ef4444;");
            }
            return;
        }
        viewModel.executeUpload().thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                FxAsync.runOnFxThread(() -> {
                    onCloseNewFunctionModal();
                    if (bytecodeTable != null && !viewModel.getBytecodeItems().isEmpty()) {
                        bytecodeTable.getSelectionModel().select(0);
                    }
                });
            }
        });
    }

    // Getters for UI assertions and test automation
    public TableView<BytecodeItem> getBytecodeTable() { return bytecodeTable; }
    public VBox getNewFunctionModal() { return newFunctionModal; }
    public Button getNewFunctionButton() { return newFunctionButton; }
    public TextField getFunctionNameField() { return functionNameField; }
    public TextField getEntrypointField() { return entrypointField; }
    public Button getUploadButton() { return uploadButton; }
    public Button getCancelModalButton() { return cancelModalButton; }
    public Label getMagicValidationLabel() { return magicValidationLabel; }
    public VBox getInspectorContainer() { return inspectorContainer; }
    public FunctionsViewModel getViewModel() { return viewModel; }
}
