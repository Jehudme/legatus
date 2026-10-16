package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.service.StudioContext;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
    @FXML private TableColumn<BytecodeItem, Number> invocationsCol;
    @FXML private TableColumn<BytecodeItem, String> sha256Col;
    @FXML private TableColumn<BytecodeItem, String> createdAtCol;

    // Inspector
    @FXML private VBox inspectorContainer;
    @FXML private Label inspectorFunctionName;
    @FXML private Label inspectorSha256;
    @FXML private Label inspectorUsageBadge;
    @FXML private Label inspectorCreatedAt;
    @FXML private TextArea descriptionArea;
    @FXML private FlowPane tagsChipPane;
    @FXML private TextField newTagField;
    @FXML private Button saveMetadataButton;
    @FXML private Button openInWorkbenchButton;
    @FXML private Label metadataStatusLabel;

    // Bytecode Uploader
    @FXML private Label filePathLabel;
    @FXML private Label sha256Label;
    @FXML private TextField functionNameField;
    @FXML private TextField entrypointField;
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
        bytecodeTable.setItems(filteredBytecodeItems);

        functionNameCol.setCellValueFactory(c -> c.getValue().functionNameProperty());
        tagsCol.setCellValueFactory(c -> c.getValue().tagsProperty());
        invocationsCol.setCellValueFactory(c -> c.getValue().invocationsLastHourProperty());
        sha256Col.setCellValueFactory(c -> c.getValue().sha256Property());
        createdAtCol.setCellValueFactory(c -> c.getValue().uploadedAtProperty());

        // Tag filter listener
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
        bytecodeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            viewModel.setSelectedBytecode(selected);
            displaySelectedMetadata(selected);
        });

        // Initialize with currently selected item if any
        if (viewModel.getSelectedBytecode() != null) {
            bytecodeTable.getSelectionModel().select(viewModel.getSelectedBytecode());
            displaySelectedMetadata(viewModel.getSelectedBytecode());
        }

        filePathLabel.textProperty().bind(viewModel.selectedFilePathProperty());
        sha256Label.textProperty().bind(viewModel.calculatedSha256Property());
        functionNameField.textProperty().bindBidirectional(viewModel.functionNameInputProperty());
        entrypointField.textProperty().bindBidirectional(viewModel.entrypointInputProperty());
        uploadButton.disableProperty().bind(viewModel.isUploadingProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    }

    private void displaySelectedMetadata(BytecodeItem item) {
        if (item == null) {
            inspectorFunctionName.setText("Select a function from catalog");
            inspectorSha256.setText("--");
            inspectorUsageBadge.setText("0 invocations/hr");
            inspectorCreatedAt.setText("--");
            descriptionArea.setText("");
            tagsChipPane.getChildren().clear();
            openInWorkbenchButton.setDisable(true);
            saveMetadataButton.setDisable(true);
            return;
        }

        openInWorkbenchButton.setDisable(false);
        saveMetadataButton.setDisable(false);

        inspectorFunctionName.setText(item.getFunctionName());
        inspectorSha256.setText(item.getSha256());
        inspectorUsageBadge.setText(item.getInvocationsLastHour() + " invocations in last 60m");
        inspectorCreatedAt.setText(item.getUploadedAt().isBlank() ? "N/A" : item.getUploadedAt());
        descriptionArea.setText(item.getDescription());

        renderTagChips(item.getTags());
    }

    private void renderTagChips(String tagsStr) {
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
        BytecodeItem current = bytecodeTable.getSelectionModel().getSelectedItem();
        if (current == null || viewModel == null) return;

        String desc = descriptionArea.getText();
        String tags = current.getTags();
        current.setDescription(desc);

        if (current.getFunctionId() != null) {
            metadataStatusLabel.setText("Saving metadata...");
            viewModel.updateFunctionMetadata(current.getFunctionId(), desc, tags)
                    .thenRun(() -> metadataStatusLabel.setText("Metadata saved successfully!"))
                    .exceptionally(ex -> {
                        metadataStatusLabel.setText("Failed to save: " + ex.getMessage());
                        return null;
                    });
        } else {
            metadataStatusLabel.setText("Local metadata updated.");
        }
    }

    @FXML
    private void onOpenInWorkbenchClicked() {
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

    @FXML
    private void onRefreshCatalogClicked() {
        if (viewModel != null) viewModel.loadCatalogedFunctions();
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
