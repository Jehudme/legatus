package com.legatus.studio.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.protocol.Frame;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.protocol.TaskLogChunkPayload;
import com.legatus.studio.service.ExecutionProfileService;
import com.legatus.studio.service.FxAsync;
import com.legatus.studio.service.StudioContext;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Controller for the Function Execution Workbench and Profile Manager (MS-009, MS-011).
 */
public class ExecuteWorkbenchController {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Left Side: Profiles Master Table
    @FXML private TextField profileSearchField;
    @FXML private TableView<ExecutionProfile> profilesTable;
    @FXML private TableColumn<ExecutionProfile, String> profileNameCol;
    @FXML private TableColumn<ExecutionProfile, String> functionNameCol;
    @FXML private TableColumn<ExecutionProfile, String> priorityCol;
    @FXML private TableColumn<ExecutionProfile, String> budgetCol;

    // Right Side: TabPane
    @FXML private TabPane profileTabs;
    @FXML private Tab inspectTab;
    @FXML private Tab createTab;

    // Tab 1: Inspect & Execute
    @FXML private Label inspectHeaderLabel;
    @FXML private ComboBox<BytecodeItem> inspectFunctionSelector;
    @FXML private TextField inspectProfileNameField;
    @FXML private TextArea inspectArgumentsArea;
    @FXML private Label inspectJsonValidationLabel;
    @FXML private TextField inspectInstructionBudgetField;
    @FXML private CheckBox inspectUnlimitedInstructionsCheck;
    @FXML private TextField inspectTimeLimitField;
    @FXML private CheckBox inspectUnlimitedTimeCheck;
    @FXML private TextField inspectMemoryField;
    @FXML private ComboBox<String> inspectPriorityCombo;
    @FXML private CheckBox inspectNotifyOnFailureCheck;

    @FXML private Button executeButton;
    @FXML private Button saveProfileBtn;
    @FXML private Button exportProfileBtn;
    @FXML private Button deleteProfileBtn;
    @FXML private Label dispatchStatusLabel;

    // Live Execution Monitor
    @FXML private Label activeTaskIdLabel;
    @FXML private Label activeWorkerIdLabel;
    @FXML private Label taskStatusBadge;
    @FXML private Label progressMetricsLabel;
    @FXML private ProgressBar executionProgressBar;
    @FXML private TextArea terminalArea;

    // Tab 2: Create New Profile
    @FXML private ComboBox<BytecodeItem> createFunctionSelector;
    @FXML private TextField createProfileNameField;
    @FXML private TextArea createArgumentsArea;
    @FXML private Label createJsonValidationLabel;
    @FXML private TextField createInstructionBudgetField;
    @FXML private CheckBox createUnlimitedInstructionsCheck;
    @FXML private TextField createTimeLimitField;
    @FXML private CheckBox createUnlimitedTimeCheck;
    @FXML private TextField createMemoryField;
    @FXML private ComboBox<String> createPriorityCombo;
    @FXML private CheckBox createNotifyOnFailureCheck;
    @FXML private Button createProfileBtn;
    @FXML private Button createImportProfileBtn;
    @FXML private Label createStatusLabel;

    private StudioContext context;
    private MainViewModel mainViewModel;
    private final ObservableList<ExecutionProfile> profiles = FXCollections.observableArrayList();
    private FilteredList<ExecutionProfile> filteredProfiles;
    private ExecutionProfile currentlyInspectedProfile = null;
    private String currentMonitoredTaskId = null;
    private Consumer<TaskLogChunkPayload> logChunkListener;

    public void setContext(StudioContext context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
        initializeBindings();
    }

    @FXML
    public void initialize() {
        // Priority options
        ObservableList<String> priorities = FXCollections.observableArrayList("NORMAL", "HIGH", "LOW", "CRITICAL", "BATCH");
        if (inspectPriorityCombo != null) {
            inspectPriorityCombo.setItems(priorities);
            inspectPriorityCombo.setValue("NORMAL");
        }
        if (createPriorityCombo != null) {
            createPriorityCombo.setItems(priorities);
            createPriorityCombo.setValue("NORMAL");
        }

        // String converter for BytecodeItem dropdowns
        StringConverter<BytecodeItem> functionConverter = new StringConverter<>() {
            @Override
            public String toString(BytecodeItem item) {
                if (item == null) return "";
                String sha = item.getSha256();
                String shortSha = sha != null && sha.length() > 8 ? sha.substring(0, 8) : sha;
                return item.getFunctionName() + " (" + shortSha + ")";
            }
            @Override
            public BytecodeItem fromString(String string) { return null; }
        };

        if (inspectFunctionSelector != null) inspectFunctionSelector.setConverter(functionConverter);
        if (createFunctionSelector != null) createFunctionSelector.setConverter(functionConverter);

        // Bind table columns
        if (profileNameCol != null) profileNameCol.setCellValueFactory(c -> c.getValue().profileNameProperty());
        if (functionNameCol != null) functionNameCol.setCellValueFactory(c -> c.getValue().functionNameProperty());
        if (priorityCol != null) priorityCol.setCellValueFactory(c -> c.getValue().priorityProperty());
        if (budgetCol != null) budgetCol.setCellValueFactory(c -> {
            if (c.getValue().isUnlimitedInstructions()) {
                return new SimpleStringProperty("Unlimited (∞)");
            }
            return new SimpleStringProperty(String.format("%,d", c.getValue().getInstructionBudget()));
        });

        // Search filter
        filteredProfiles = new FilteredList<>(profiles, p -> true);
        if (profileSearchField != null) {
            profileSearchField.textProperty().addListener((obs, oldV, query) -> {
                String q = query != null ? query.trim().toLowerCase() : "";
                filteredProfiles.setPredicate(p -> {
                    if (q.isEmpty()) return true;
                    return p.getProfileName().toLowerCase().contains(q)
                            || p.getFunctionName().toLowerCase().contains(q)
                            || p.getPriority().toLowerCase().contains(q);
                });
            });
        }

        SortedList<ExecutionProfile> sortedProfiles = new SortedList<>(filteredProfiles);
        if (profilesTable != null) {
            sortedProfiles.comparatorProperty().bind(profilesTable.comparatorProperty());
            profilesTable.setItems(sortedProfiles);

            profilesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
                if (selected != null) {
                    inspectProfile(selected);
                }
            });
        }

        // Live JSON validation listeners
        if (inspectArgumentsArea != null) {
            inspectArgumentsArea.textProperty().addListener((obs, oldV, text) -> validateJsonSyntax(text, inspectJsonValidationLabel));
        }
        if (createArgumentsArea != null) {
            createArgumentsArea.textProperty().addListener((obs, oldV, text) -> validateJsonSyntax(text, createJsonValidationLabel));
        }

        // Quota disable bindings
        if (inspectUnlimitedInstructionsCheck != null && inspectInstructionBudgetField != null) {
            inspectUnlimitedInstructionsCheck.selectedProperty().addListener((obs, oldV, sel) -> inspectInstructionBudgetField.setDisable(sel));
        }
        if (inspectUnlimitedTimeCheck != null && inspectTimeLimitField != null) {
            inspectUnlimitedTimeCheck.selectedProperty().addListener((obs, oldV, sel) -> inspectTimeLimitField.setDisable(sel));
        }
        if (createUnlimitedInstructionsCheck != null && createInstructionBudgetField != null) {
            createUnlimitedInstructionsCheck.selectedProperty().addListener((obs, oldV, sel) -> createInstructionBudgetField.setDisable(sel));
        }
        if (createUnlimitedTimeCheck != null && createTimeLimitField != null) {
            createUnlimitedTimeCheck.selectedProperty().addListener((obs, oldV, sel) -> createTimeLimitField.setDisable(sel));
        }

        if (context != null) {
            initializeBindings();
        }
    }

    private void initializeBindings() {
        if (context == null) return;

        // Populate functions into selectors
        if (context.getFunctionsViewModel() != null) {
            ObservableList<BytecodeItem> items = context.getFunctionsViewModel().getBytecodeItems();
            if (inspectFunctionSelector != null) inspectFunctionSelector.setItems(items);
            if (createFunctionSelector != null) {
                createFunctionSelector.setItems(items);
                if (!items.isEmpty()) createFunctionSelector.getSelectionModel().select(0);
            }
        }

        // Load all profiles
        loadAllProfiles();

        // Attach telemetry listeners
        attachTelemetryListeners();
    }

    private void attachTelemetryListeners() {
        if (context.getLstcpClient() == null) return;

        // Register multi-cast task log chunk listener
        this.logChunkListener = chunk -> {
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
        };
        context.getLstcpClient().addTaskLogChunkListener(logChunkListener);

        context.getLstcpClient().addTaskLifecycleListener(lifecycle -> {
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

    private void loadAllProfiles() {
        profiles.clear();
        ExecutionProfileService service = context.getExecutionProfileService();
        if (service == null) return;

        service.getProfiles(null).thenAccept(list -> {
            FxAsync.runOnFxThread(() -> {
                for (RestDto.ExecutionProfileDto dto : list) {
                    ExecutionProfile p = ExecutionProfile.fromDto(dto);
                    resolveFunctionName(p);
                    profiles.add(p);
                }
                if (profiles.isEmpty()) {
                    seedDefaultProfiles();
                }
                if (!profiles.isEmpty()) {
                    profilesTable.getSelectionModel().select(0);
                }
            });
        }).exceptionally(ex -> {
            FxAsync.runOnFxThread(this::seedDefaultProfiles);
            return null;
        });
    }

    private void resolveFunctionName(ExecutionProfile p) {
        if (p.getFunctionId() != null && context.getFunctionsViewModel() != null) {
            for (BytecodeItem item : context.getFunctionsViewModel().getBytecodeItems()) {
                if (p.getFunctionId().equals(item.getFunctionId())) {
                    p.setFunctionName(item.getFunctionName());
                    return;
                }
            }
        }
        if (p.getFunctionName().isBlank()) {
            p.setFunctionName(p.getFunctionId() != null ? p.getFunctionId().toString().substring(0, 8) : "General");
        }
    }

    private void seedDefaultProfiles() {
        profiles.clear();
        UUID funcId = null;
        String funcName = "Default";
        if (context.getFunctionsViewModel() != null && !context.getFunctionsViewModel().getBytecodeItems().isEmpty()) {
            BytecodeItem first = context.getFunctionsViewModel().getBytecodeItems().get(0);
            funcId = first.getFunctionId();
            funcName = first.getFunctionName();
        }

        ExecutionProfile pDefault = new ExecutionProfile("", "Standard Run", funcId);
        pDefault.setFunctionName(funcName);
        pDefault.setArgumentsJson("{\n  \"action\": \"process\",\n  \"batch_size\": 50\n}");
        pDefault.setInstructionBudget(10_000_000L);
        pDefault.setTimeLimitMs(60_000L);
        pDefault.setMemoryMb(64L);
        pDefault.setPriority("NORMAL");
        pDefault.setNotifyOnFailure(false);
        profiles.add(0, pDefault);

        ExecutionProfile pFast = new ExecutionProfile("", "Fast Verification", funcId);
        pFast.setFunctionName(funcName);
        pFast.setArgumentsJson("{\n  \"quick\": true\n}");
        pFast.setInstructionBudget(1_000_000L);
        pFast.setTimeLimitMs(5_000L);
        pFast.setMemoryMb(32L);
        pFast.setPriority("HIGH");
        pFast.setNotifyOnFailure(true);
        profiles.add(0, pFast);

        ExecutionProfile pUnbounded = new ExecutionProfile("", "Heavy Batch Computation", funcId);
        pUnbounded.setFunctionName(funcName);
        pUnbounded.setArgumentsJson("{}");
        pUnbounded.setInstructionBudget(0L);
        pUnbounded.setUnlimitedInstructions(true);
        pUnbounded.setTimeLimitMs(0L);
        pUnbounded.setUnlimitedTime(true);
        pUnbounded.setMemoryMb(256L);
        pUnbounded.setPriority("BATCH");
        pUnbounded.setNotifyOnFailure(true);
        profiles.add(0, pUnbounded);

        if (profilesTable != null && !profiles.isEmpty()) {
            profilesTable.getSelectionModel().select(0);
        }
    }

    private void inspectProfile(ExecutionProfile profile) {
        if (profile == null) return;
        this.currentlyInspectedProfile = profile;

        if (profileTabs != null && inspectTab != null) {
            profileTabs.getSelectionModel().select(inspectTab);
        }

        if (inspectHeaderLabel != null) {
            inspectHeaderLabel.setText("Profile: " + profile.getProfileName());
        }

        if (inspectProfileNameField != null) inspectProfileNameField.setText(profile.getProfileName());

        // Select function in combo
        if (inspectFunctionSelector != null && profile.getFunctionId() != null) {
            for (BytecodeItem item : inspectFunctionSelector.getItems()) {
                if (profile.getFunctionId().equals(item.getFunctionId())) {
                    inspectFunctionSelector.getSelectionModel().select(item);
                    break;
                }
            }
        }

        if (inspectArgumentsArea != null) {
            inspectArgumentsArea.setText(profile.getArgumentsJson() != null ? profile.getArgumentsJson() : "{}");
            validateJsonSyntax(inspectArgumentsArea.getText(), inspectJsonValidationLabel);
        }

        if (inspectInstructionBudgetField != null) inspectInstructionBudgetField.setText(String.valueOf(profile.getInstructionBudget()));
        if (inspectUnlimitedInstructionsCheck != null) inspectUnlimitedInstructionsCheck.setSelected(profile.isUnlimitedInstructions());

        if (inspectTimeLimitField != null) inspectTimeLimitField.setText(String.valueOf(profile.getTimeLimitMs()));
        if (inspectUnlimitedTimeCheck != null) inspectUnlimitedTimeCheck.setSelected(profile.isUnlimitedTime());

        if (inspectMemoryField != null) inspectMemoryField.setText(String.valueOf(profile.getMemoryMb()));
        if (inspectPriorityCombo != null && profile.getPriority() != null) inspectPriorityCombo.setValue(profile.getPriority());
        if (inspectNotifyOnFailureCheck != null) inspectNotifyOnFailureCheck.setSelected(profile.isNotifyOnFailure());

        if (dispatchStatusLabel != null) dispatchStatusLabel.setText("");
    }

    private void validateJsonSyntax(String text, Label label) {
        if (label == null) return;
        if (text == null || text.isBlank()) {
            label.setText("✓ Empty Arguments");
            label.setStyle("-fx-text-fill: #94a3b8;");
            return;
        }
        try {
            MAPPER.readTree(text);
            label.setText("✓ Valid JSON");
            label.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
        } catch (Exception e) {
            label.setText("✗ Invalid JSON");
            label.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void onSwitchToCreateTab() {
        if (profileTabs != null && createTab != null) {
            profileTabs.getSelectionModel().select(createTab);
        }
    }

    @FXML
    private void onFormatJsonClicked() {
        formatJsonInArea(inspectArgumentsArea, inspectJsonValidationLabel);
    }

    @FXML
    private void onCreateFormatJsonClicked() {
        formatJsonInArea(createArgumentsArea, createJsonValidationLabel);
    }

    private void formatJsonInArea(TextArea area, Label label) {
        if (area == null) return;
        String text = area.getText();
        if (text == null || text.isBlank()) return;
        try {
            Object obj = MAPPER.readValue(text, Object.class);
            String formatted = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            area.setText(formatted);
            validateJsonSyntax(formatted, label);
        } catch (Exception e) {
            if (label != null) {
                label.setText("Format Failed: " + e.getMessage());
                label.setStyle("-fx-text-fill: #f87171;");
            }
        }
    }

    @FXML
    private void onSaveProfileClicked() {
        if (currentlyInspectedProfile == null) return;

        updateProfileFromInspectInputs(currentlyInspectedProfile);
        BytecodeItem fn = inspectFunctionSelector.getSelectionModel().getSelectedItem();
        if (fn != null && fn.getFunctionId() != null) {
            currentlyInspectedProfile.setFunctionId(fn.getFunctionId());
            currentlyInspectedProfile.setFunctionName(fn.getFunctionName());
        }

        dispatchStatusLabel.setText("Saving profile '" + currentlyInspectedProfile.getProfileName() + "'...");
        context.getExecutionProfileService().saveProfile(currentlyInspectedProfile.toDto())
                .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                    currentlyInspectedProfile.setProfileId(saved.profile_id());
                    currentlyInspectedProfile.setVersion(saved.version() != null ? saved.version() : 0L);
                    profilesTable.refresh();
                    dispatchStatusLabel.setText("Profile '" + currentlyInspectedProfile.getProfileName() + "' saved successfully.");
                }))
                .exceptionally(ex -> {
                    FxAsync.runOnFxThread(() -> dispatchStatusLabel.setText("Save Failed: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    private void onDeleteProfileClicked() {
        if (currentlyInspectedProfile == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Profile");
        confirm.setHeaderText("Delete profile '" + currentlyInspectedProfile.getProfileName() + "'?");
        confirm.setContentText("Are you sure you want to permanently delete this execution profile?");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            ExecutionProfile target = currentlyInspectedProfile;
            if (target.getProfileId() != null && !target.getProfileId().isBlank()) {
                context.getExecutionProfileService().deleteProfile(target.getProfileId())
                        .thenRun(() -> FxAsync.runOnFxThread(() -> {
                            profiles.remove(target);
                            dispatchStatusLabel.setText("Profile deleted.");
                        }))
                        .exceptionally(ex -> {
                            FxAsync.runOnFxThread(() -> dispatchStatusLabel.setText("Delete Failed: " + ex.getMessage()));
                            return null;
                        });
            } else {
                profiles.remove(target);
                dispatchStatusLabel.setText("Local profile removed.");
            }
        }
    }

    @FXML
    private void onExportProfileClicked() {
        if (currentlyInspectedProfile == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Execution Profile");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        chooser.setInitialFileName(currentlyInspectedProfile.getProfileName().toLowerCase().replace(" ", "-") + ".json");
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try {
                updateProfileFromInspectInputs(currentlyInspectedProfile);
                context.getExecutionProfileService().exportProfile(currentlyInspectedProfile.toDto(), file.toPath());
                dispatchStatusLabel.setText("Exported profile to " + file.getName());
            } catch (Exception e) {
                dispatchStatusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onCreateProfileClicked() {
        String name = createProfileNameField.getText() != null ? createProfileNameField.getText().trim() : "";
        if (name.isEmpty()) {
            createStatusLabel.setText("Please enter a profile name.");
            return;
        }

        BytecodeItem fn = createFunctionSelector.getSelectionModel().getSelectedItem();
        UUID fnId = fn != null ? fn.getFunctionId() : null;
        String fnName = fn != null ? fn.getFunctionName() : "General";

        ExecutionProfile newProfile = new ExecutionProfile("", name, fnId);
        newProfile.setFunctionName(fnName);
        newProfile.setArgumentsJson(createArgumentsArea.getText());

        try {
            newProfile.setInstructionBudget(Long.parseLong(createInstructionBudgetField.getText().trim()));
        } catch (NumberFormatException ignored) {}
        newProfile.setUnlimitedInstructions(createUnlimitedInstructionsCheck.isSelected());

        try {
            newProfile.setTimeLimitMs(Long.parseLong(createTimeLimitField.getText().trim()));
        } catch (NumberFormatException ignored) {}
        newProfile.setUnlimitedTime(createUnlimitedTimeCheck.isSelected());

        try {
            newProfile.setMemoryMb(Long.parseLong(createMemoryField.getText().trim()));
        } catch (NumberFormatException ignored) {}

        newProfile.setPriority(createPriorityCombo.getValue() != null ? createPriorityCombo.getValue() : "NORMAL");
        newProfile.setNotifyOnFailure(createNotifyOnFailureCheck.isSelected());

        createStatusLabel.setText("Creating profile '" + name + "'...");
        context.getExecutionProfileService().saveProfile(newProfile.toDto())
                .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                    newProfile.setProfileId(saved.profile_id());
                    newProfile.setVersion(saved.version() != null ? saved.version() : 0L);
                    // Prepend to top of list as required
                    profiles.add(0, newProfile);
                    profilesTable.getSelectionModel().select(newProfile);
                    createStatusLabel.setText("Profile '" + name + "' created successfully.");
                    // Reset fields
                    createProfileNameField.clear();
                    createArgumentsArea.setText("{}");
                }))
                .exceptionally(ex -> {
                    FxAsync.runOnFxThread(() -> createStatusLabel.setText("Create Failed: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    private void onImportProfileClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Execution Profile");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            try {
                RestDto.ExecutionProfileDto importedDto = context.getExecutionProfileService().importProfile(file.toPath());
                ExecutionProfile imported = ExecutionProfile.fromDto(importedDto);
                imported.setProfileId(""); // reset ID for new import
                resolveFunctionName(imported);

                context.getExecutionProfileService().saveProfile(imported.toDto())
                        .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                            imported.setProfileId(saved.profile_id());
                            imported.setVersion(saved.version() != null ? saved.version() : 0L);
                            // Prepend on top
                            profiles.add(0, imported);
                            profilesTable.getSelectionModel().select(imported);
                            if (createStatusLabel != null) createStatusLabel.setText("Imported profile '" + imported.getProfileName() + "'");
                        }))
                        .exceptionally(ex -> {
                            FxAsync.runOnFxThread(() -> {
                                profiles.add(0, imported);
                                profilesTable.getSelectionModel().select(imported);
                            });
                            return null;
                        });
            } catch (Exception e) {
                if (createStatusLabel != null) createStatusLabel.setText("Import failed: " + e.getMessage());
            }
        }
    }

    private void updateProfileFromInspectInputs(ExecutionProfile p) {
        if (p == null) return;
        if (inspectProfileNameField != null && !inspectProfileNameField.getText().isBlank()) {
            p.setProfileName(inspectProfileNameField.getText().trim());
        }
        if (inspectArgumentsArea != null) {
            p.setArgumentsJson(inspectArgumentsArea.getText());
        }
        if (inspectInstructionBudgetField != null) {
            try {
                p.setInstructionBudget(Long.parseLong(inspectInstructionBudgetField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (inspectUnlimitedInstructionsCheck != null) {
            p.setUnlimitedInstructions(inspectUnlimitedInstructionsCheck.isSelected());
        }
        if (inspectTimeLimitField != null) {
            try {
                p.setTimeLimitMs(Long.parseLong(inspectTimeLimitField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (inspectUnlimitedTimeCheck != null) {
            p.setUnlimitedTime(inspectUnlimitedTimeCheck.isSelected());
        }
        if (inspectMemoryField != null) {
            try {
                p.setMemoryMb(Long.parseLong(inspectMemoryField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (inspectPriorityCombo != null && inspectPriorityCombo.getValue() != null) {
            p.setPriority(inspectPriorityCombo.getValue());
        }
        if (inspectNotifyOnFailureCheck != null) {
            p.setNotifyOnFailure(inspectNotifyOnFailureCheck.isSelected());
        }
    }

    @FXML
    private void onExecuteClicked() {
        if (currentlyInspectedProfile == null) {
            dispatchStatusLabel.setText("Please select an execution profile first.");
            return;
        }

        BytecodeItem fn = inspectFunctionSelector.getSelectionModel().getSelectedItem();
        if (fn == null || fn.getSha256() == null || fn.getSha256().isBlank()) {
            dispatchStatusLabel.setText("Please select a target function for this profile.");
            return;
        }

        // Parse arguments JSON
        Map<String, Object> argsMap = new HashMap<>();
        String rawJson = inspectArgumentsArea.getText();
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
            budget = Long.parseLong(inspectInstructionBudgetField.getText().trim());
        } catch (NumberFormatException ignored) {}
        boolean unlimitedBudget = inspectUnlimitedInstructionsCheck.isSelected();

        int timeout = 60_000;
        try {
            timeout = Integer.parseInt(inspectTimeLimitField.getText().trim());
        } catch (NumberFormatException ignored) {}
        boolean unlimitedTimeout = inspectUnlimitedTimeCheck.isSelected();

        long memoryMb = 64L;
        try {
            memoryMb = Long.parseLong(inspectMemoryField.getText().trim());
        } catch (NumberFormatException ignored) {}
        long memoryBytes = memoryMb * 1024L * 1024L;

        String priority = inspectPriorityCombo.getValue() != null ? inspectPriorityCombo.getValue() : "NORMAL";
        boolean notifyOnFailure = inspectNotifyOnFailureCheck.isSelected();

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
            // Restore execution button immediately so user can execute again anytime!
            executeButton.setDisable(false);

            if (resp != null && resp.task_id() != null) {
                this.currentMonitoredTaskId = resp.task_id();
                activeTaskIdLabel.setText(resp.task_id());
                taskStatusBadge.setText(resp.status() != null ? resp.status() : "QUEUED");
                taskStatusBadge.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2); -fx-text-fill: #38bdf8;");
                dispatchStatusLabel.setText("Task dispatched: " + resp.task_id());
                terminalArea.appendText("[SYSTEM] Dispatched task " + resp.task_id() + " (profile=" + currentlyInspectedProfile.getProfileName() + ", entry=" + fn.getEntryPoint() + ")\n");

                // Subscribe to logs over WebSocket
                if (context.getLstcpClient() != null) {
                    context.getLstcpClient().sendFrame(Frame.create("SUBSCRIBE_TASK_LOGS", Map.of("task_id", resp.task_id())));
                }
            } else {
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
        if (terminalArea != null) terminalArea.clear();
    }
}
