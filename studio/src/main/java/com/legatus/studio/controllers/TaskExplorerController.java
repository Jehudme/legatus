package com.legatus.studio.controllers;

import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.viewmodel.TasksViewModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class TaskExplorerController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private TableView<TaskRecord> tasksTable;
    @FXML private TableColumn<TaskRecord, String> taskIdCol;
    @FXML private TableColumn<TaskRecord, String> nameCol;
    @FXML private TableColumn<TaskRecord, String> statusCol;
    @FXML private TableColumn<TaskRecord, String> durationCol;
    @FXML private TableColumn<TaskRecord, String> startTimeCol;
    @FXML private TableColumn<TaskRecord, String> endTimeCol;
    @FXML private TableColumn<TaskRecord, String> workerCol;

    @FXML private Label selectedTaskIdLabel;
    @FXML private Label selectedNameLabel;
    @FXML private Label selectedStatusLabel;
    @FXML private Label selectedWorkerLabel;
    @FXML private Label selectedDurationLabel;
    @FXML private Label selectedTimingLabel;
    @FXML private Label selectedInstructionsLabel;
    @FXML private ProgressBar instructionProgressBar;
    @FXML private Button abortButton;

    private TasksViewModel viewModel;

    public void setViewModel(TasksViewModel viewModel) {
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

        searchField.textProperty().bindBidirectional(viewModel.searchQueryProperty());
        if (statusFilterCombo != null) {
            statusFilterCombo.getItems().setAll("ALL", "QUEUED", "EXECUTING", "COMPLETED", "FAILED", "ABORTED");
            if (statusFilterCombo.getValue() == null) {
                statusFilterCombo.setValue(viewModel.filterStatusProperty().get() != null ? viewModel.filterStatusProperty().get() : "ALL");
            }
            statusFilterCombo.valueProperty().bindBidirectional(viewModel.filterStatusProperty());
        }
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

        durationCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(
                c.getValue()::getFormattedDuration, c.getValue().executionWallTimeMsProperty()
        ));

        startTimeCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(
                c.getValue()::getFormattedStartTime, c.getValue().startedAtProperty()
        ));

        endTimeCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(
                c.getValue()::getFormattedEndTime, c.getValue().finishedAtProperty(), c.getValue().statusProperty()
        ));

        workerCol.setCellValueFactory(c -> {
            String friendly = c.getValue().getWorkerFriendlyName();
            if (friendly != null && !friendly.isBlank()) {
                return c.getValue().workerFriendlyNameProperty();
            }
            return c.getValue().assignedWorkerIdProperty();
        });

        tasksTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.setSelectedTask(newV);
                selectedTaskIdLabel.setText(newV.getTaskId());

                String fnProfile = newV.getName();
                if (newV.getProfileName() != null && !newV.getProfileName().isBlank()) {
                    fnProfile += " [" + newV.getProfileName() + "]";
                }
                selectedNameLabel.setText(fnProfile);
                selectedStatusLabel.setText(newV.getStatus());

                String workerDisplay = newV.getWorkerFriendlyName() != null && !newV.getWorkerFriendlyName().isBlank()
                        ? newV.getWorkerFriendlyName() + " (" + newV.getAssignedWorkerId() + ")"
                        : (newV.getAssignedWorkerId().isBlank() ? "Unassigned" : newV.getAssignedWorkerId());
                selectedWorkerLabel.setText(workerDisplay);

                if (selectedDurationLabel != null) {
                    selectedDurationLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                            newV::getFormattedDuration, newV.executionWallTimeMsProperty()
                    ));
                }
                if (selectedTimingLabel != null) {
                    selectedTimingLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                            () -> newV.getFormattedStartTime() + " / " + newV.getFormattedEndTime(),
                            newV.startedAtProperty(), newV.finishedAtProperty(), newV.statusProperty()
                    ));
                }

                selectedInstructionsLabel.setText(newV.getInstructionsConsumed() + " / " + newV.getInstructionBudget());

                double progress = newV.getInstructionBudget() > 0
                        ? (double) newV.getInstructionsConsumed() / newV.getInstructionBudget()
                        : 0.0;
                instructionProgressBar.setProgress(Math.min(1.0, progress));

                abortButton.setDisable("COMPLETED".equalsIgnoreCase(newV.getStatus()) || "FAILED".equalsIgnoreCase(newV.getStatus()) || "ABORTED".equalsIgnoreCase(newV.getStatus()));
            }
        });

        viewModel.refreshTasks();
    }

    @FXML
    private void onRefreshClicked() {
        if (viewModel != null) {
            viewModel.refreshTasks();
        }
    }

    @FXML
    private void onAbortClicked() {
        if (viewModel != null) viewModel.triggerAbort("Operator requested abort");
    }
}
