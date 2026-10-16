package com.legatus.studio.controllers;

import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.protocol.Frame;
import com.legatus.studio.protocol.TaskLogChunkPayload;
import com.legatus.studio.service.StudioContext;
import com.legatus.studio.viewmodel.TasksViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller driving the Task Explorer Ledger, Live Inspector, and Embedded Streaming Console (MS-004, MS-011).
 */
public class TaskExplorerController {

    // Left Side: Ledger Table
    @FXML private TextField searchField;
    @FXML private TableView<TaskRecord> tasksTable;
    @FXML private TableColumn<TaskRecord, String> taskIdCol;
    @FXML private TableColumn<TaskRecord, String> nameCol;
    @FXML private TableColumn<TaskRecord, String> statusCol;
    @FXML private TableColumn<TaskRecord, String> durationCol;
    @FXML private TableColumn<TaskRecord, String> startTimeCol;
    @FXML private TableColumn<TaskRecord, String> endTimeCol;
    @FXML private TableColumn<TaskRecord, String> workerCol;

    // Right Side: Task Live Inspector
    @FXML private Label selectedTaskIdLabel;
    @FXML private Label selectedNameLabel;
    @FXML private Label selectedStatusLabel;
    @FXML private Label selectedWorkerLabel;

    @FXML private Label selectedDurationLabel;
    @FXML private Label selectedTimingLabel;
    @FXML private VBox durationProgressBox;
    @FXML private Label durationProgressLabel;
    @FXML private ProgressBar durationProgressBar;

    @FXML private VBox instructionProgressBox;
    @FXML private Label selectedInstructionsLabel;
    @FXML private ProgressBar instructionProgressBar;

    @FXML private TextArea taskTerminalArea;
    @FXML private Button abortButton;
    @FXML private Label abortStatusLabel;

    private TasksViewModel viewModel;
    private StudioContext context;
    private String currentlyInspectedTaskId = null;
    private Consumer<TaskLogChunkPayload> logChunkListener;

    public void setContext(StudioContext context) {
        this.context = context;
        attachWebSocketLogStreaming();
    }

    public void setViewModel(TasksViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        if (viewModel != null) {
            bindViewModel();
        }
        if (context != null) {
            attachWebSocketLogStreaming();
        }
    }

    private void attachWebSocketLogStreaming() {
        if (context == null || context.getLstcpClient() == null) return;

        // Register multi-cast task log chunk listener
        if (this.logChunkListener == null) {
            this.logChunkListener = chunk -> {
                if (chunk == null || chunk.task_id() == null) return;
                if (chunk.task_id().equals(currentlyInspectedTaskId)) {
                    Platform.runLater(() -> {
                        if (chunk.log_chunk() != null && taskTerminalArea != null) {
                            taskTerminalArea.appendText(chunk.log_chunk() + "\n");
                        }
                    });
                }
            };
            context.getLstcpClient().addTaskLogChunkListener(logChunkListener);
        }
    }

    private void bindViewModel() {
        if (viewModel == null) return;

        // Search query binding
        searchField.textProperty().bindBidirectional(viewModel.searchQueryProperty());

        // Tasks table bindings
        tasksTable.setItems(viewModel.getFilteredTasks());

        taskIdCol.setCellValueFactory(c -> c.getValue().taskIdProperty());
        nameCol.setCellValueFactory(c -> {
            String pName = c.getValue().getProfileName();
            String fName = c.getValue().getName();
            if (pName != null && !pName.isBlank()) {
                return new SimpleStringProperty(fName + " [" + pName + "]");
            }
            return c.getValue().nameProperty();
        });

        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.toUpperCase());
                    String bg = switch (item.toUpperCase()) {
                        case "COMPLETED" -> "#15803d";
                        case "EXECUTING" -> "#0284c7";
                        case "FAILED" -> "#b91c1c";
                        case "ABORTED", "ABORTING" -> "#b45309";
                        default -> "#475569";
                    };
                    badge.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-padding: 2 8 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        durationCol.setCellValueFactory(c -> Bindings.createStringBinding(
                c.getValue()::getFormattedDuration, c.getValue().executionWallTimeMsProperty()
        ));

        startTimeCol.setCellValueFactory(c -> Bindings.createStringBinding(
                c.getValue()::getFormattedStartTime, c.getValue().startedAtProperty()
        ));

        endTimeCol.setCellValueFactory(c -> Bindings.createStringBinding(
                c.getValue()::getFormattedEndTime, c.getValue().finishedAtProperty(), c.getValue().statusProperty()
        ));

        workerCol.setCellValueFactory(c -> {
            String friendly = c.getValue().getWorkerFriendlyName();
            if (friendly != null && !friendly.isBlank()) {
                return c.getValue().workerFriendlyNameProperty();
            }
            return c.getValue().assignedWorkerIdProperty();
        });

        // Task selection listener
        tasksTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            if (selected != null) {
                inspectTask(selected);
            } else {
                unbindCurrentTask();
            }
        });

        if (abortStatusLabel != null) {
            abortStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        }

        if (viewModel.errorMessageProperty() != null) {
            viewModel.errorMessageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    showErrorAlert("Task Action Failure", newVal);
                }
            });
        }

        viewModel.refreshTasks();
    }

    private final java.util.List<Runnable> taskUnbinders = new java.util.ArrayList<>();

    public void unbindCurrentTask() {
        for (Runnable unbinder : taskUnbinders) {
            try { unbinder.run(); } catch (Exception ignored) {}
        }
        taskUnbinders.clear();

        if (selectedDurationLabel != null) selectedDurationLabel.textProperty().unbind();
        if (selectedTimingLabel != null) selectedTimingLabel.textProperty().unbind();
    }

    public void inspectTask(TaskRecord task) {
        if (task == null) return;
        unbindCurrentTask();

        this.currentlyInspectedTaskId = task.getTaskId();
        viewModel.setSelectedTask(task);

        if (selectedTaskIdLabel != null) selectedTaskIdLabel.setText(task.getTaskId());

        String fnProfile = task.getName();
        if (task.getProfileName() != null && !task.getProfileName().isBlank()) {
            fnProfile += " [" + task.getProfileName() + "]";
        }
        if (selectedNameLabel != null) selectedNameLabel.setText(fnProfile);

        // Status badge styling with listener tracking
        updateStatusBadge(task.getStatus());
        javafx.beans.value.ChangeListener<String> statusListener = (obs, oldV, newStatus) -> updateStatusBadge(newStatus);
        task.statusProperty().addListener(statusListener);
        taskUnbinders.add(() -> task.statusProperty().removeListener(statusListener));

        // Worker display
        String workerDisplay = task.getWorkerFriendlyName() != null && !task.getWorkerFriendlyName().isBlank()
                ? task.getWorkerFriendlyName() + " (" + task.getAssignedWorkerId() + ")"
                : (task.getAssignedWorkerId().isBlank() ? "Unassigned" : task.getAssignedWorkerId());
        if (selectedWorkerLabel != null) selectedWorkerLabel.setText(workerDisplay);

        // Duration text
        if (selectedDurationLabel != null) {
            selectedDurationLabel.textProperty().bind(Bindings.createStringBinding(
                    task::getFormattedDuration, task.executionWallTimeMsProperty()
            ));
        }

        // Timestamps text
        if (selectedTimingLabel != null) {
            selectedTimingLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> task.getFormattedStartTime() + " / " + task.getFormattedEndTime(),
                    task.startedAtProperty(), task.finishedAtProperty(), task.statusProperty()
            ));
        }

        // Duration progress bar (hide if unlimited or <= 0)
        updateDurationMeter(task);
        javafx.beans.value.ChangeListener<Number> durationListener = (obs, oldV, cur) -> updateDurationMeter(task);
        task.executionWallTimeMsProperty().addListener(durationListener);
        taskUnbinders.add(() -> task.executionWallTimeMsProperty().removeListener(durationListener));

        javafx.beans.value.ChangeListener<Number> timeoutListener = (obs, oldV, cur) -> updateDurationMeter(task);
        task.timeoutMsProperty().addListener(timeoutListener);
        taskUnbinders.add(() -> task.timeoutMsProperty().removeListener(timeoutListener));

        // Instruction progress bar (hide if unlimited or <= 0)
        updateInstructionMeter(task);
        javafx.beans.value.ChangeListener<Number> instructionsListener = (obs, oldV, cur) -> updateInstructionMeter(task);
        task.instructionsConsumedProperty().addListener(instructionsListener);
        taskUnbinders.add(() -> task.instructionsConsumedProperty().removeListener(instructionsListener));

        javafx.beans.value.ChangeListener<Number> budgetListener = (obs, oldV, cur) -> updateInstructionMeter(task);
        task.instructionBudgetProperty().addListener(budgetListener);
        taskUnbinders.add(() -> task.instructionBudgetProperty().removeListener(budgetListener));

        // Abort button enable/disable (strictly disable if not executing/queued)
        updateAbortButtonState(task.getStatus());
        javafx.beans.value.ChangeListener<String> abortStateListener = (obs, oldV, curStatus) -> updateAbortButtonState(curStatus);
        task.statusProperty().addListener(abortStateListener);
        taskUnbinders.add(() -> task.statusProperty().removeListener(abortStateListener));

        // Clear terminal and subscribe to logs
        if (taskTerminalArea != null) {
            taskTerminalArea.clear();
            taskTerminalArea.appendText("[SYSTEM] Subscribed to logs for task " + task.getTaskId() + " (status=" + task.getStatus() + ")\n");
        }

        if (context != null && context.getLstcpClient() != null) {
            context.getLstcpClient().sendFrame(Frame.create("SUBSCRIBE_TASK_LOGS", Map.of("task_id", task.getTaskId())));
        }
    }

    private void updateStatusBadge(String status) {
        if (selectedStatusLabel == null) return;
        String st = status != null ? status.toUpperCase() : "UNKNOWN";
        selectedStatusLabel.setText(st);

        String bg = switch (st) {
            case "COMPLETED" -> "#15803d";
            case "EXECUTING" -> "#0284c7";
            case "FAILED" -> "#b91c1c";
            case "ABORTED", "ABORTING" -> "#b45309";
            default -> "#475569";
        };
        selectedStatusLabel.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-padding: 3 10 3 10; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
    }

    private void updateDurationMeter(TaskRecord task) {
        if (durationProgressBox == null) return;
        long timeoutMs = task.getTimeoutMs();

        // If unlimited (<= 0), do not show the bar as requested
        if (timeoutMs <= 0) {
            durationProgressBox.setVisible(false);
            durationProgressBox.setManaged(false);
        } else {
            durationProgressBox.setVisible(true);
            durationProgressBox.setManaged(true);
            long elapsed = task.getExecutionWallTimeMs();
            double progress = (double) elapsed / timeoutMs;
            if (durationProgressBar != null) {
                durationProgressBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
            }
            if (durationProgressLabel != null) {
                durationProgressLabel.setText(String.format("%,dms / %,dms", elapsed, timeoutMs));
            }
        }
    }

    private void updateInstructionMeter(TaskRecord task) {
        if (instructionProgressBox == null) return;
        long budget = task.getInstructionBudget();

        // If unlimited (<= 0), do not show the bar as requested
        if (budget <= 0) {
            instructionProgressBox.setVisible(false);
            instructionProgressBox.setManaged(false);
        } else {
            instructionProgressBox.setVisible(true);
            instructionProgressBox.setManaged(true);
            long consumed = task.getInstructionsConsumed();
            double progress = (double) consumed / budget;
            if (instructionProgressBar != null) {
                instructionProgressBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
            }
            if (selectedInstructionsLabel != null) {
                selectedInstructionsLabel.setText(String.format("%,d / %,d instructions", consumed, budget));
            }
        }
    }

    private void updateAbortButtonState(String status) {
        if (abortButton == null) return;
        String st = status != null ? status.toUpperCase() : "";
        boolean canAbort = "EXECUTING".equals(st) || "QUEUED".equals(st) || "SUBMITTING".equals(st) || "DISPATCHED".equals(st);
        abortButton.setDisable(!canAbort);
    }

    private void showErrorAlert(String title, String message) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("[ALERT ERROR] " + title + ": " + message);
        }
    }

    @FXML
    private void onClearLogsClicked() {
        if (taskTerminalArea != null) {
            taskTerminalArea.clear();
        }
    }

    @FXML
    private void onAbortClicked() {
        if (viewModel != null) {
            viewModel.triggerAbort("Operator requested abort");
        }
    }

    public Label getSelectedTaskIdLabel() { return selectedTaskIdLabel; }
    public Label getSelectedDurationLabel() { return selectedDurationLabel; }
    public ProgressBar getDurationProgressBar() { return durationProgressBar; }
    public ProgressBar getInstructionProgressBar() { return instructionProgressBar; }
    public VBox getDurationProgressBox() { return durationProgressBox; }
    public VBox getInstructionProgressBox() { return instructionProgressBox; }
    public Button getAbortButton() { return abortButton; }
    public TextArea getTaskTerminalArea() { return taskTerminalArea; }
    public int getActiveUnbinderCount() { return taskUnbinders.size(); }
}
