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
import com.legatus.studio.viewmodel.ExecutionProfileViewModel;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/**
 * Controller for the Function Execution Workbench and Profile Manager (MS-009, MS-015).
 */
public class ExecuteWorkbenchController {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Left Side: Profiles Master Table
    @FXML private Button newProfileButton;
    @FXML private TextField profileSearchField;
    @FXML private TableView<ExecutionProfile> profilesTable;
    @FXML private TableColumn<ExecutionProfile, String> profileNameCol;
    @FXML private TableColumn<ExecutionProfile, String> functionNameCol;
    @FXML private TableColumn<ExecutionProfile, String> priorityCol;
    @FXML private TableColumn<ExecutionProfile, String> budgetCol;

    // Right Side: Dedicated Inspect & Execute Workbench
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

    // Modal: Create New Profile Overlay
    @FXML private VBox newProfileModal;
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
    @FXML private Button cancelCreateProfileBtn;
    @FXML private Button createImportProfileBtn;
    @FXML private Button createProfileBtn;
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
                    if (chunk.log_chunk() != null && terminalArea != null) {
                        terminalArea.appendText(chunk.log_chunk() + "\n");
                    }
                    if (chunk.progress_pct() != null && executionProgressBar != null) {
                        executionProgressBar.setProgress(chunk.progress_pct() / 100.0);
                    }
                    if (chunk.instructions_consumed() != null && progressMetricsLabel != null) {
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
                    if (taskStatusBadge != null) {
                        taskStatusBadge.setText(lifecycle.status());
                        if ("COMPLETED".equalsIgnoreCase(lifecycle.status())) {
                            taskStatusBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #34d399;");
                            if (executionProgressBar != null) executionProgressBar.setProgress(1.0);
                        } else if ("FAILED".equalsIgnoreCase(lifecycle.status()) || "ABORTED".equalsIgnoreCase(lifecycle.status())) {
                            taskStatusBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #f87171;");
                        } else if ("EXECUTING".equalsIgnoreCase(lifecycle.status())) {
                            taskStatusBadge.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2); -fx-text-fill: #38bdf8;");
                            if (lifecycle.assigned_worker_id() != null && activeWorkerIdLabel != null) {
                                activeWorkerIdLabel.setText(lifecycle.assigned_worker_id());
                            }
                        }
                    }
                    // Guarantee executeButton stays enabled for subsequent dispatches
                    if (executeButton != null) executeButton.setDisable(false);

                    if (lifecycle.instructions_consumed() != null && progressMetricsLabel != null) {
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
                if (!profiles.isEmpty() && profilesTable != null) {
                    profilesTable.getSelectionModel().select(0);
                }
            });
        }).exceptionally(ex -> {
            FxAsync.runOnFxThread(this::seedDefaultProfiles);
            return null;
        });
    }

    private void resolveFunctionName(ExecutionProfile p) {
        if (p.getFunctionId() != null && context != null && context.getFunctionsViewModel() != null) {
            for (BytecodeItem item : context.getFunctionsViewModel().getBytecodeItems()) {
                if (p.getFunctionId().equals(item.getFunctionId())) {
                    p.setFunctionName(item.getFunctionName());
                    return;
                }
            }
        }
        if (p.getFunctionName() == null || p.getFunctionName().isBlank()) {
            p.setFunctionName(p.getFunctionId() != null ? p.getFunctionId().toString().substring(0, 8) : "General");
        }
    }

    private void seedDefaultProfiles() {
        profiles.clear();
        UUID funcId = null;
        String funcName = "Default";
        if (context != null && context.getFunctionsViewModel() != null && !context.getFunctionsViewModel().getBytecodeItems().isEmpty()) {
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

    public void inspectProfile(ExecutionProfile profile) {
        if (profile == null) return;
        this.currentlyInspectedProfile = profile;

        if (inspectHeaderLabel != null) {
            inspectHeaderLabel.setText("Profile: " + profile.getProfileName());
        }

        if (inspectProfileNameField != null) inspectProfileNameField.setText(profile.getProfileName());

        // Auto-select function in combo based on profile.getFunctionId() (MS-015 P0)
        if (inspectFunctionSelector != null) {
            boolean selectedMatch = false;
            if (profile.getFunctionId() != null) {
                for (BytecodeItem item : inspectFunctionSelector.getItems()) {
                    if (profile.getFunctionId().equals(item.getFunctionId())) {
                        inspectFunctionSelector.getSelectionModel().select(item);
                        selectedMatch = true;
                        break;
                    }
                }
            }
            if (!selectedMatch && !inspectFunctionSelector.getItems().isEmpty()) {
                // If no exact functionId match, check by name
                for (BytecodeItem item : inspectFunctionSelector.getItems()) {
                    if (item.getFunctionName().equalsIgnoreCase(profile.getFunctionName())) {
                        inspectFunctionSelector.getSelectionModel().select(item);
                        selectedMatch = true;
                        break;
                    }
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

    // Modal Lifecycle (MS-015 P0)
    @FXML
    public void onOpenNewProfileModal() {
        if (createProfileNameField != null) createProfileNameField.clear();
        if (createArgumentsArea != null) {
            createArgumentsArea.setText("{}");
            validateJsonSyntax("{}", createJsonValidationLabel);
        }
        if (createInstructionBudgetField != null) createInstructionBudgetField.setText("10000000");
        if (createUnlimitedInstructionsCheck != null) createUnlimitedInstructionsCheck.setSelected(false);
        if (createTimeLimitField != null) createTimeLimitField.setText("60000");
        if (createUnlimitedTimeCheck != null) createUnlimitedTimeCheck.setSelected(false);
        if (createMemoryField != null) createMemoryField.setText("64");
        if (createPriorityCombo != null) createPriorityCombo.setValue("NORMAL");
        if (createNotifyOnFailureCheck != null) createNotifyOnFailureCheck.setSelected(false);
        if (createStatusLabel != null) createStatusLabel.setText("");

        if (createFunctionSelector != null && !createFunctionSelector.getItems().isEmpty()) {
            createFunctionSelector.getSelectionModel().select(0);
        }

        if (newProfileModal != null) {
            newProfileModal.setVisible(true);
            newProfileModal.setManaged(true);
        }
    }

    @FXML
    public void onCloseNewProfileModal() {
        if (newProfileModal != null) {
            newProfileModal.setVisible(false);
            newProfileModal.setManaged(false);
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
        BytecodeItem fn = inspectFunctionSelector != null ? inspectFunctionSelector.getSelectionModel().getSelectedItem() : null;
        if (fn != null && fn.getFunctionId() != null) {
            currentlyInspectedProfile.setFunctionId(fn.getFunctionId());
            currentlyInspectedProfile.setFunctionName(fn.getFunctionName());
        }

        if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Saving profile '" + currentlyInspectedProfile.getProfileName() + "'...");
        context.getExecutionProfileService().saveProfile(currentlyInspectedProfile.toDto())
                .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                    currentlyInspectedProfile.setProfileId(saved.profile_id());
                    currentlyInspectedProfile.setVersion(saved.version() != null ? saved.version() : 0L);
                    if (profilesTable != null) profilesTable.refresh();
                    if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Profile '" + currentlyInspectedProfile.getProfileName() + "' saved successfully.");
                }))
                .exceptionally(ex -> {
                    FxAsync.runOnFxThread(() -> {
                        if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Save Failed: " + ex.getMessage());
                    });
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
                            if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Profile deleted.");
                        }))
                        .exceptionally(ex -> {
                            FxAsync.runOnFxThread(() -> {
                                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Delete Failed: " + ex.getMessage());
                            });
                            return null;
                        });
            } else {
                profiles.remove(target);
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Local profile removed.");
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
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Exported profile to " + file.getName());
            } catch (Exception e) {
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    public void onCreateProfileClicked() {
        String name = createProfileNameField != null && createProfileNameField.getText() != null
                ? createProfileNameField.getText().trim()
                : "";
        if (name.isEmpty()) {
            if (createStatusLabel != null) createStatusLabel.setText("Please enter a profile name.");
            return;
        }

        BytecodeItem fn = createFunctionSelector != null ? createFunctionSelector.getSelectionModel().getSelectedItem() : null;
        UUID fnId = fn != null ? fn.getFunctionId() : null;
        String fnName = fn != null ? fn.getFunctionName() : "General";

        ExecutionProfile newProfile = new ExecutionProfile("", name, fnId);
        newProfile.setFunctionName(fnName);
        if (createArgumentsArea != null) {
            newProfile.setArgumentsJson(createArgumentsArea.getText());
        }

        if (createInstructionBudgetField != null) {
            try {
                newProfile.setInstructionBudget(Long.parseLong(createInstructionBudgetField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (createUnlimitedInstructionsCheck != null) {
            newProfile.setUnlimitedInstructions(createUnlimitedInstructionsCheck.isSelected());
        }

        if (createTimeLimitField != null) {
            try {
                newProfile.setTimeLimitMs(Long.parseLong(createTimeLimitField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (createUnlimitedTimeCheck != null) {
            newProfile.setUnlimitedTime(createUnlimitedTimeCheck.isSelected());
        }

        if (createMemoryField != null) {
            try {
                newProfile.setMemoryMb(Long.parseLong(createMemoryField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }

        if (createPriorityCombo != null && createPriorityCombo.getValue() != null) {
            newProfile.setPriority(createPriorityCombo.getValue());
        } else {
            newProfile.setPriority("NORMAL");
        }

        if (createNotifyOnFailureCheck != null) {
            newProfile.setNotifyOnFailure(createNotifyOnFailureCheck.isSelected());
        }

        if (createStatusLabel != null) createStatusLabel.setText("Creating profile '" + name + "'...");

        // Save and prepend at index 0 (MS-015 requirement)
        if (context != null && context.getExecutionProfileService() != null) {
            context.getExecutionProfileService().saveProfile(newProfile.toDto())
                    .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                        newProfile.setProfileId(saved.profile_id());
                        newProfile.setVersion(saved.version() != null ? saved.version() : 0L);
                        profiles.add(0, newProfile);
                        if (profilesTable != null) {
                            profilesTable.getSelectionModel().select(newProfile);
                        }
                        onCloseNewProfileModal();
                    }))
                    .exceptionally(ex -> {
                        FxAsync.runOnFxThread(() -> {
                            if (createStatusLabel != null) createStatusLabel.setText("Create Failed: " + ex.getMessage());
                        });
                        return null;
                    });
        } else {
            // Local fallback
            profiles.add(0, newProfile);
            if (profilesTable != null) profilesTable.getSelectionModel().select(newProfile);
            onCloseNewProfileModal();
        }
    }

    @FXML
    public void onImportProfileClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Execution Profile");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            importProfileFromFile(file);
        }
    }

    public void importProfileFromFile(File file) {
        try {
            String rawJson = Files.readString(file.toPath());
            RestDto.ExecutionProfileDto importedDto = ExecutionProfileViewModel.validateProfileJsonSchema(rawJson);
            ExecutionProfile imported = ExecutionProfile.fromDto(importedDto);
            imported.setProfileId(""); // reset ID for new import
            resolveFunctionName(imported);

            if (context != null && context.getExecutionProfileService() != null) {
                context.getExecutionProfileService().saveProfile(imported.toDto())
                        .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                            imported.setProfileId(saved.profile_id());
                            imported.setVersion(saved.version() != null ? saved.version() : 0L);
                            profiles.add(0, imported);
                            if (profilesTable != null) profilesTable.getSelectionModel().select(imported);
                            onCloseNewProfileModal();
                        }))
                        .exceptionally(ex -> {
                            FxAsync.runOnFxThread(() -> {
                                profiles.add(0, imported);
                                if (profilesTable != null) profilesTable.getSelectionModel().select(imported);
                                onCloseNewProfileModal();
                            });
                            return null;
                        });
            } else {
                profiles.add(0, imported);
                if (profilesTable != null) profilesTable.getSelectionModel().select(imported);
                onCloseNewProfileModal();
            }
        } catch (Exception e) {
            if (createStatusLabel != null) {
                createStatusLabel.setText("Import failed: " + e.getMessage());
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
    public void onExecuteClicked() {
        if (currentlyInspectedProfile == null) {
            if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Please select an execution profile first.");
            return;
        }

        BytecodeItem fn = inspectFunctionSelector != null ? inspectFunctionSelector.getSelectionModel().getSelectedItem() : null;
        if (fn == null || fn.getSha256() == null || fn.getSha256().isBlank()) {
            if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Please select a target function for this profile.");
            return;
        }

        // Parse arguments JSON
        Map<String, Object> argsMap = new HashMap<>();
        String rawJson = inspectArgumentsArea != null ? inspectArgumentsArea.getText() : "{}";
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                argsMap = MAPPER.readValue(rawJson, Map.class);
            } catch (Exception e) {
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Cannot execute: JSON syntax error.");
                return;
            }
        }

        long budget = 10_000_000L;
        if (inspectInstructionBudgetField != null) {
            try {
                budget = Long.parseLong(inspectInstructionBudgetField.getText().trim());
            } catch (NumberFormatException ignored) {}
        }
        boolean unlimitedBudget = inspectUnlimitedInstructionsCheck != null && inspectUnlimitedInstructionsCheck.isSelected();

        int timeout = 60_000;
        if (inspectTimeLimitField != null) {
            try {
                timeout = Integer.parseInt(inspectTimeLimitField.getText().trim());
            } catch (NumberFormatException ignored) {}
        }
        boolean unlimitedTimeout = inspectUnlimitedTimeCheck != null && inspectUnlimitedTimeCheck.isSelected();

        long memoryMb = 64L;
        if (inspectMemoryField != null) {
            try {
                memoryMb = Long.parseLong(inspectMemoryField.getText().trim());
            } catch (NumberFormatException ignored) {}
        }
        long memoryBytes = memoryMb * 1024L * 1024L;

        String priority = inspectPriorityCombo != null && inspectPriorityCombo.getValue() != null
                ? inspectPriorityCombo.getValue()
                : "NORMAL";
        boolean notifyOnFailure = inspectNotifyOnFailureCheck != null && inspectNotifyOnFailureCheck.isSelected();

        if (executeButton != null) executeButton.setDisable(true);
        if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Submitting task to cluster...");
        if (taskStatusBadge != null) {
            taskStatusBadge.setText("SUBMITTING");
            taskStatusBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.2); -fx-text-fill: #fbbf24;");
        }
        if (terminalArea != null) terminalArea.clear();
        if (executionProgressBar != null) executionProgressBar.setProgress(0.0);
        if (progressMetricsLabel != null) progressMetricsLabel.setText("0 instructions executed");

        if (context == null || context.getTaskService() == null) {
            if (executeButton != null) executeButton.setDisable(false);
            if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Task service unavailable.");
            return;
        }

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
            // Restore execution button immediately so user can execute again anytime (MS-015 P0)
            if (executeButton != null) executeButton.setDisable(false);

            if (resp != null && resp.task_id() != null) {
                this.currentMonitoredTaskId = resp.task_id();
                if (activeTaskIdLabel != null) activeTaskIdLabel.setText(resp.task_id());
                if (taskStatusBadge != null) {
                    taskStatusBadge.setText(resp.status() != null ? resp.status() : "QUEUED");
                    taskStatusBadge.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2); -fx-text-fill: #38bdf8;");
                }
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Task dispatched: " + resp.task_id());
                if (terminalArea != null) {
                    terminalArea.appendText("[SYSTEM] Dispatched task " + resp.task_id() + " (profile=" + currentlyInspectedProfile.getProfileName() + ", entry=" + fn.getEntryPoint() + ")\n");
                }

                // Subscribe to logs over WebSocket
                if (context.getLstcpClient() != null) {
                    context.getLstcpClient().sendFrame(Frame.create("SUBSCRIBE_TASK_LOGS", Map.of("task_id", resp.task_id())));
                }
            } else {
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Failed to dispatch task: invalid response");
            }
        })).exceptionally(ex -> {
            FxAsync.runOnFxThread(() -> {
                if (executeButton != null) executeButton.setDisable(false);
                if (taskStatusBadge != null) {
                    taskStatusBadge.setText("FAILED");
                    taskStatusBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #f87171;");
                }
                if (dispatchStatusLabel != null) dispatchStatusLabel.setText("Dispatch failed: " + ex.getMessage());
                if (terminalArea != null) terminalArea.appendText("[ERROR] Dispatch error: " + ex.getMessage() + "\n");
            });
            return null;
        });
    }

    @FXML
    private void onClearTerminalClicked() {
        if (terminalArea != null) terminalArea.clear();
    }

    // Getters for UI assertions and test automation
    public TableView<ExecutionProfile> getProfilesTable() { return profilesTable; }
    public VBox getNewProfileModal() { return newProfileModal; }
    public Button getNewProfileButton() { return newProfileButton; }
    public Button getExecuteButton() { return executeButton; }
    public ComboBox<BytecodeItem> getInspectFunctionSelector() { return inspectFunctionSelector; }
    public TextField getInspectProfileNameField() { return inspectProfileNameField; }
    public TextArea getInspectArgumentsArea() { return inspectArgumentsArea; }
    public TextField getInspectInstructionBudgetField() { return inspectInstructionBudgetField; }
    public Label getTaskStatusBadge() { return taskStatusBadge; }
    public Label getActiveTaskIdLabel() { return activeTaskIdLabel; }
    public TextArea getTerminalArea() { return terminalArea; }
    public Button getCancelCreateProfileBtn() { return cancelCreateProfileBtn; }
    public Button getCreateProfileBtn() { return createProfileBtn; }
    public TextField getCreateProfileNameField() { return createProfileNameField; }
    public TextArea getCreateArgumentsArea() { return createArgumentsArea; }
    public Label getCreateJsonValidationLabel() { return createJsonValidationLabel; }
    public TextField getCreateInstructionBudgetField() { return createInstructionBudgetField; }
    public ComboBox<BytecodeItem> getCreateFunctionSelector() { return createFunctionSelector; }
    public Label getCreateStatusLabel() { return createStatusLabel; }
    public ObservableList<ExecutionProfile> getProfiles() { return profiles; }
    public ExecutionProfile getCurrentlyInspectedProfile() { return currentlyInspectedProfile; }
}
