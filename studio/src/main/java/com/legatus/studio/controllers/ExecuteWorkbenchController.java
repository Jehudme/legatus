package com.legatus.studio.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.protocol.Frame;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.ExecutionProfileService;
import com.legatus.studio.service.FxAsync;
import com.legatus.studio.service.StudioContext;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.*;

/**
 * Controller for the Dedicated Function Execution Workbench (MS-009).
 */
public class ExecuteWorkbenchController {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @FXML private ComboBox<BytecodeItem> functionSelector;

    // Profile bar
    @FXML private ComboBox<ExecutionProfile> profileSelector;
    @FXML private Button saveProfileBtn;
    @FXML private Button saveAsProfileBtn;
    @FXML private Button deleteProfileBtn;
    @FXML private Button exportProfileBtn;
    @FXML private Button importProfileBtn;

    // Argument Editor
    @FXML private TextArea argumentsArea;
    @FXML private Label jsonValidationLabel;

    // Quotas & Constraints
    @FXML private TextField instructionBudgetField;
    @FXML private CheckBox unlimitedInstructionsCheck;
    @FXML private TextField timeLimitField;
    @FXML private CheckBox unlimitedTimeCheck;
    @FXML private TextField memoryField;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private CheckBox notifyOnFailureCheck;

    // Actions
    @FXML private Button executeButton;
    @FXML private Label dispatchStatusLabel;

    // Monitor & Console
    @FXML private Label activeTaskIdLabel;
    @FXML private Label activeWorkerIdLabel;
    @FXML private Label taskStatusBadge;
    @FXML private Label progressMetricsLabel;
    @FXML private ProgressBar executionProgressBar;
    @FXML private TextArea terminalArea;

    private StudioContext context;
    private MainViewModel mainViewModel;
    private final ObservableList<ExecutionProfile> profiles = FXCollections.observableArrayList();
    private String currentMonitoredTaskId = null;

    public void setContext(StudioContext context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
        initializeBindings();
    }

    @FXML
    public void initialize() {
        if (priorityCombo != null) {
            priorityCombo.setItems(FXCollections.observableArrayList(
                    "NORMAL", "HIGH", "LOW", "CRITICAL", "BATCH"
            ));
            priorityCombo.setValue("NORMAL");
        }

        if (profileSelector != null) {
            profileSelector.setItems(profiles);
            profileSelector.setConverter(new StringConverter<>() {
                @Override
                public String toString(ExecutionProfile profile) {
                    return profile != null ? profile.getProfileName() : "";
                }
                @Override
                public ExecutionProfile fromString(String string) {
                    return null;
                }
            });
        }

        if (functionSelector != null) {
            functionSelector.setConverter(new StringConverter<>() {
                @Override
                public String toString(BytecodeItem item) {
                    if (item == null) return "";
                    String sha = item.getSha256();
                    String shortSha = sha.length() > 10 ? sha.substring(0, 10) : sha;
                    return item.getFunctionName() + " [" + item.getEntryPoint() + "] (" + shortSha + ")";
                }
                @Override
                public BytecodeItem fromString(String string) {
                    return null;
                }
            });
        }

        // Live JSON validation listener
        if (argumentsArea != null) {
            argumentsArea.textProperty().addListener((obs, oldV, newV) -> validateJsonSyntax(newV));
        }

        // Unlimited toggle bindings
        if (unlimitedInstructionsCheck != null && instructionBudgetField != null) {
            unlimitedInstructionsCheck.selectedProperty().addListener((obs, oldV, selected) ->
                    instructionBudgetField.setDisable(selected));
        }
        if (unlimitedTimeCheck != null && timeLimitField != null) {
            unlimitedTimeCheck.selectedProperty().addListener((obs, oldV, selected) ->
                    timeLimitField.setDisable(selected));
        }

        if (context != null) {
            initializeBindings();
        }
    }

    private void initializeBindings() {
        if (context == null) return;

        // Populate functions
        if (context.getFunctionsViewModel() != null) {
            functionSelector.setItems(context.getFunctionsViewModel().getBytecodeItems());
        }

        // Select pre-targeted function if navigated from catalog
        if (mainViewModel != null && mainViewModel.getTargetFunctionForExecution() != null) {
            functionSelector.getSelectionModel().select(mainViewModel.getTargetFunctionForExecution());
        } else if (!functionSelector.getItems().isEmpty() && functionSelector.getSelectionModel().isEmpty()) {
            functionSelector.getSelectionModel().select(0);
        }

        // Function selection listener -> reload profiles
        functionSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selectedFn) -> {
            loadProfilesForFunction(selectedFn);
        });

        // Profile selection listener -> populate inputs
        profileSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldV, profile) -> {
            applyProfile(profile);
        });

        // Load profiles for initial selection
        if (functionSelector.getSelectionModel().getSelectedItem() != null) {
            loadProfilesForFunction(functionSelector.getSelectionModel().getSelectedItem());
        }

        // Attach WebSocket listeners for live logs and progress
        attachTelemetryListeners();
    }

    private void attachTelemetryListeners() {
        if (context.getLstcpClient() == null) return;

        context.getLstcpClient().setTaskLogChunkListener(chunk -> {
            if (chunk == null || chunk.task_id() == null) return;
            if (chunk.task_id().equals(currentMonitoredTaskId)) {
                Platform.runLater(() -> {
                    if (chunk.log_chunk() != null) {
                        terminalArea.appendText(chunk.log_chunk() + "\n");
                    }
                    if (chunk.progress_pct() != null) {
                        executionProgressBar.setProgress(chunk.progress_pct() / 100.0);
                    }
                    if (chunk.instructions_consumed() != null) {
                        progressMetricsLabel.setText(chunk.instructions_consumed() + " instructions executed");
                    }
                });
            }
        });

        context.getLstcpClient().setTaskLifecycleListener(lifecycle -> {
            if (lifecycle == null || lifecycle.task_id() == null) return;
            if (lifecycle.task_id().equals(currentMonitoredTaskId)) {
                Platform.runLater(() -> {
                    taskStatusBadge.setText(lifecycle.status());
                    if ("COMPLETED".equalsIgnoreCase(lifecycle.status())) {
                        taskStatusBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #34d399;");
                        executionProgressBar.setProgress(1.0);
                        executeButton.setDisable(false);
                    } else if ("FAILED".equalsIgnoreCase(lifecycle.status()) || "ABORTED".equalsIgnoreCase(lifecycle.status())) {
                        taskStatusBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #f87171;");
                        executeButton.setDisable(false);
                    } else if ("EXECUTING".equalsIgnoreCase(lifecycle.status())) {
                        taskStatusBadge.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2); -fx-text-fill: #38bdf8;");
                        if (lifecycle.assigned_worker_id() != null) {
                            activeWorkerIdLabel.setText(lifecycle.assigned_worker_id());
                        }
                    }
                    if (lifecycle.instructions_consumed() != null) {
                        progressMetricsLabel.setText(lifecycle.instructions_consumed() + " instructions executed");
                    }
                });
            }
        });
    }

    private void loadProfilesForFunction(BytecodeItem function) {
        profiles.clear();
        if (function == null) return;

        UUID funcId = function.getFunctionId();
        ExecutionProfileService service = context.getExecutionProfileService();

        if (service != null && funcId != null) {
            service.getProfiles(funcId).thenAccept(list -> {
                FxAsync.runOnFxThread(() -> {
                    for (RestDto.ExecutionProfileDto dto : list) {
                        profiles.add(ExecutionProfile.fromDto(dto));
                    }
                    if (profiles.isEmpty()) {
                        seedDefaultProfiles(funcId);
                    }
                    if (!profiles.isEmpty()) {
                        profileSelector.getSelectionModel().select(0);
                    }
                });
            }).exceptionally(ex -> {
                FxAsync.runOnFxThread(() -> seedDefaultProfiles(funcId));
                return null;
            });
        } else {
            seedDefaultProfiles(funcId);
            if (!profiles.isEmpty()) {
                profileSelector.getSelectionModel().select(0);
            }
        }
    }

    private void seedDefaultProfiles(UUID functionId) {
        profiles.clear();

        ExecutionProfile pDefault = new ExecutionProfile("", "Default", functionId);
        pDefault.setArgumentsJson("{\n  \"action\": \"run\"\n}");
        pDefault.setInstructionBudget(10_000_000L);
        pDefault.setTimeLimitMs(60_000L);
        pDefault.setMemoryMb(64L);
        pDefault.setPriority("NORMAL");
        pDefault.setNotifyOnFailure(false);
        profiles.add(pDefault);

        ExecutionProfile pFast = new ExecutionProfile("", "Fast Test", functionId);
        pFast.setArgumentsJson("{\n  \"quick\": true\n}");
        pFast.setInstructionBudget(1_000_000L);
        pFast.setTimeLimitMs(5_000L);
        pFast.setMemoryMb(32L);
        pFast.setPriority("HIGH");
        pFast.setNotifyOnFailure(true);
        profiles.add(pFast);

        ExecutionProfile pUnbounded = new ExecutionProfile("", "Unbounded Batch", functionId);
        pUnbounded.setArgumentsJson("{}");
        pUnbounded.setInstructionBudget(0L);
        pUnbounded.setUnlimitedInstructions(true);
        pUnbounded.setTimeLimitMs(0L);
        pUnbounded.setUnlimitedTime(true);
        pUnbounded.setMemoryMb(256L);
        pUnbounded.setPriority("BATCH");
        pUnbounded.setNotifyOnFailure(true);
        profiles.add(pUnbounded);
    }

    private void applyProfile(ExecutionProfile profile) {
        if (profile == null) return;

        argumentsArea.setText(profile.getArgumentsJson() != null ? profile.getArgumentsJson() : "{}");
        instructionBudgetField.setText(String.valueOf(profile.getInstructionBudget()));
        unlimitedInstructionsCheck.setSelected(profile.isUnlimitedInstructions());
        timeLimitField.setText(String.valueOf(profile.getTimeLimitMs()));
        unlimitedTimeCheck.setSelected(profile.isUnlimitedTime());
        memoryField.setText(String.valueOf(profile.getMemoryMb()));
        if (profile.getPriority() != null) {
            priorityCombo.setValue(profile.getPriority());
        }
        notifyOnFailureCheck.setSelected(profile.isNotifyOnFailure());
        validateJsonSyntax(argumentsArea.getText());
    }

    private void validateJsonSyntax(String text) {
        if (text == null || text.isBlank()) {
            jsonValidationLabel.setText("✓ Empty Arguments");
            jsonValidationLabel.setStyle("-fx-text-fill: #94a3b8;");
            return;
        }
        try {
            MAPPER.readTree(text);
            jsonValidationLabel.setText("✓ Valid JSON");
            jsonValidationLabel.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
        } catch (Exception e) {
            jsonValidationLabel.setText("✗ Invalid JSON");
            jsonValidationLabel.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void onFormatJsonClicked() {
        String text = argumentsArea.getText();
        if (text == null || text.isBlank()) return;
        try {
            Object obj = MAPPER.readValue(text, Object.class);
            String formatted = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            argumentsArea.setText(formatted);
            validateJsonSyntax(formatted);
        } catch (Exception e) {
            jsonValidationLabel.setText("Format Failed: " + e.getMessage());
            jsonValidationLabel.setStyle("-fx-text-fill: #f87171;");
        }
    }

    @FXML
    private void onSaveProfileClicked() {
        ExecutionProfile current = profileSelector.getSelectionModel().getSelectedItem();
        BytecodeItem fn = functionSelector.getSelectionModel().getSelectedItem();
        if (current == null || fn == null) return;

        updateProfileFromInputs(current);
        if (fn.getFunctionId() != null) {
            current.setFunctionId(fn.getFunctionId());
            dispatchStatusLabel.setText("Saving profile '" + current.getProfileName() + "'...");
            context.getExecutionProfileService().saveProfile(current.toDto())
                    .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                        current.setProfileId(saved.profile_id());
                        current.setVersion(saved.version() != null ? saved.version() : 0L);
                        dispatchStatusLabel.setText("Profile '" + current.getProfileName() + "' saved.");
                    }))
                    .exceptionally(ex -> {
                        FxAsync.runOnFxThread(() -> dispatchStatusLabel.setText("Failed to save profile: " + ex.getMessage()));
                        return null;
                    });
        } else {
            dispatchStatusLabel.setText("Local profile '" + current.getProfileName() + "' updated.");
        }
    }

    @FXML
    private void onSaveAsProfileClicked() {
        BytecodeItem fn = functionSelector.getSelectionModel().getSelectedItem();
        if (fn == null) return;

        TextInputDialog dialog = new TextInputDialog("New-Profile");
        dialog.setTitle("Save Execution Profile As");
        dialog.setHeaderText("Create a new execution profile for function: " + fn.getFunctionName());
        dialog.setContentText("Profile Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) return;

            ExecutionProfile newProfile = new ExecutionProfile("", trimmed, fn.getFunctionId());
            updateProfileFromInputs(newProfile);

            if (fn.getFunctionId() != null) {
                dispatchStatusLabel.setText("Saving new profile '" + trimmed + "'...");
                context.getExecutionProfileService().saveProfile(newProfile.toDto())
                        .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                            newProfile.setProfileId(saved.profile_id());
                            newProfile.setVersion(saved.version() != null ? saved.version() : 0L);
                            profiles.add(newProfile);
                            profileSelector.getSelectionModel().select(newProfile);
                            dispatchStatusLabel.setText("Profile '" + trimmed + "' created.");
                        }))
                        .exceptionally(ex -> {
                            FxAsync.runOnFxThread(() -> dispatchStatusLabel.setText("Save As Failed: " + ex.getMessage()));
                            return null;
                        });
            } else {
                profiles.add(newProfile);
                profileSelector.getSelectionModel().select(newProfile);
                dispatchStatusLabel.setText("Local profile '" + trimmed + "' created.");
            }
        });
    }

    @FXML
    private void onDeleteProfileClicked() {
        ExecutionProfile current = profileSelector.getSelectionModel().getSelectedItem();
        if (current == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Profile");
        confirm.setHeaderText("Delete profile '" + current.getProfileName() + "'?");
        confirm.setContentText("Are you sure you want to remove this execution profile?");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            if (current.getProfileId() != null && !current.getProfileId().isBlank()) {
                context.getExecutionProfileService().deleteProfile(current.getProfileId())
                        .thenRun(() -> FxAsync.runOnFxThread(() -> {
                            profiles.remove(current);
                            if (!profiles.isEmpty()) profileSelector.getSelectionModel().select(0);
                            dispatchStatusLabel.setText("Profile deleted.");
                        }))
                        .exceptionally(ex -> {
                            FxAsync.runOnFxThread(() -> dispatchStatusLabel.setText("Delete Failed: " + ex.getMessage()));
                            return null;
                        });
            } else {
                profiles.remove(current);
                if (!profiles.isEmpty()) profileSelector.getSelectionModel().select(0);
                dispatchStatusLabel.setText("Profile removed.");
            }
        }
    }

    @FXML
    private void onExportProfileClicked() {
        ExecutionProfile current = profileSelector.getSelectionModel().getSelectedItem();
        if (current == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Execution Profile");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        chooser.setInitialFileName(current.getProfileName().toLowerCase().replace(" ", "-") + ".json");
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try {
                updateProfileFromInputs(current);
                context.getExecutionProfileService().exportProfile(current.toDto(), file.toPath());
                dispatchStatusLabel.setText("Exported profile to " + file.getName());
            } catch (Exception e) {
                dispatchStatusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onImportProfileClicked() {
        BytecodeItem fn = functionSelector.getSelectionModel().getSelectedItem();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Execution Profile");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            try {
                RestDto.ExecutionProfileDto importedDto = context.getExecutionProfileService().importProfile(file.toPath());
                ExecutionProfile imported = ExecutionProfile.fromDto(importedDto);
                if (fn != null && fn.getFunctionId() != null) {
                    imported.setFunctionId(fn.getFunctionId());
                }
                imported.setProfileId(""); // reset ID for new import
                profiles.add(imported);
                profileSelector.getSelectionModel().select(imported);
                dispatchStatusLabel.setText("Imported profile '" + imported.getProfileName() + "'");
            } catch (Exception e) {
                dispatchStatusLabel.setText("Import failed: " + e.getMessage());
            }
        }
    }

    private void updateProfileFromInputs(ExecutionProfile p) {
        p.setArgumentsJson(argumentsArea.getText());
        try {
            p.setInstructionBudget(Long.parseLong(instructionBudgetField.getText().trim()));
        } catch (NumberFormatException ignored) {}
        p.setUnlimitedInstructions(unlimitedInstructionsCheck.isSelected());
        try {
            p.setTimeLimitMs(Long.parseLong(timeLimitField.getText().trim()));
        } catch (NumberFormatException ignored) {}
        p.setUnlimitedTime(unlimitedTimeCheck.isSelected());
        try {
            p.setMemoryMb(Long.parseLong(memoryField.getText().trim()));
        } catch (NumberFormatException ignored) {}
        p.setPriority(priorityCombo.getValue());
        p.setNotifyOnFailure(notifyOnFailureCheck.isSelected());
    }

    @FXML
    private void onExecuteClicked() {
        BytecodeItem fn = functionSelector.getSelectionModel().getSelectedItem();
        if (fn == null || fn.getSha256() == null || fn.getSha256().isBlank()) {
            dispatchStatusLabel.setText("Please select a target function first.");
            return;
        }

        // Parse arguments JSON
        Map<String, Object> argsMap = new HashMap<>();
        String rawJson = argumentsArea.getText();
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                argsMap = MAPPER.readValue(rawJson, Map.class);
            } catch (Exception e) {
                dispatchStatusLabel.setText("Cannot execute: JSON syntax error.");
                return;
            }
        }

        long budget = 10_000_000L;
        try {
            budget = Long.parseLong(instructionBudgetField.getText().trim());
        } catch (NumberFormatException ignored) {}
        boolean unlimitedBudget = unlimitedInstructionsCheck.isSelected();

        int timeout = 60_000;
        try {
            timeout = Integer.parseInt(timeLimitField.getText().trim());
        } catch (NumberFormatException ignored) {}
        boolean unlimitedTimeout = unlimitedTimeCheck.isSelected();

        long memoryMb = 64L;
        try {
            memoryMb = Long.parseLong(memoryField.getText().trim());
        } catch (NumberFormatException ignored) {}
        long memoryBytes = memoryMb * 1024L * 1024L;

        String priority = priorityCombo.getValue() != null ? priorityCombo.getValue() : "NORMAL";
        boolean notifyOnFailure = notifyOnFailureCheck.isSelected();

        executeButton.setDisable(true);
        dispatchStatusLabel.setText("Submitting task to cluster...");
        taskStatusBadge.setText("SUBMITTING");
        taskStatusBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.2); -fx-text-fill: #fbbf24;");
        terminalArea.clear();
        executionProgressBar.setProgress(0.0);
        progressMetricsLabel.setText("0 instructions executed");

        context.getTaskService().submitTask(
                fn.getFunctionName() + "-run",
                fn.getSha256(),
                fn.getEntryPoint(),
                budget,
                unlimitedBudget,
                memoryBytes,
                timeout,
                unlimitedTimeout,
                priority,
                notifyOnFailure,
                argsMap
        ).thenAccept(resp -> FxAsync.runOnFxThread(() -> {
            if (resp != null && resp.task_id() != null) {
                this.currentMonitoredTaskId = resp.task_id();
                activeTaskIdLabel.setText(resp.task_id());
                taskStatusBadge.setText(resp.status() != null ? resp.status() : "QUEUED");
                taskStatusBadge.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2); -fx-text-fill: #38bdf8;");
                dispatchStatusLabel.setText("Task dispatched: " + resp.task_id());
                terminalArea.appendText("[SYSTEM] Dispatched task " + resp.task_id() + " (entry=" + fn.getEntryPoint() + ")\n");

                // Subscribe to logs over WebSocket
                if (context.getLstcpClient() != null) {
                    context.getLstcpClient().sendFrame(Frame.create("SUBSCRIBE_TASK_LOGS", Map.of("task_id", resp.task_id())));
                }
            } else {
                executeButton.setDisable(false);
                dispatchStatusLabel.setText("Failed to dispatch task: invalid response");
            }
        })).exceptionally(ex -> {
            FxAsync.runOnFxThread(() -> {
                executeButton.setDisable(false);
                taskStatusBadge.setText("FAILED");
                taskStatusBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #f87171;");
                dispatchStatusLabel.setText("Dispatch failed: " + ex.getMessage());
                terminalArea.appendText("[ERROR] Dispatch error: " + ex.getMessage() + "\n");
            });
            return null;
        });
    }

    @FXML
    private void onClearTerminalClicked() {
        terminalArea.clear();
    }
}
