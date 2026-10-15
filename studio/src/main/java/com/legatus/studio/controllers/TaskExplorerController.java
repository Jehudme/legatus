package com.legatus.studio.controllers;

import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.viewmodel.TasksViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class TaskExplorerController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private TableView<TaskRecord> tasksTable;
    @FXML private TableColumn<TaskRecord, String> taskIdCol;
    @FXML private TableColumn<TaskRecord, String> nameCol;
    @FXML private TableColumn<TaskRecord, String> statusCol;
    @FXML private TableColumn<TaskRecord, Number> instructionsCol;

    @FXML private Label selectedTaskIdLabel;
    @FXML private Label selectedNameLabel;
    @FXML private Label selectedStatusLabel;
    @FXML private Label selectedWorkerLabel;
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
        tasksTable.setItems(viewModel.getFilteredTasks());

        taskIdCol.setCellValueFactory(c -> c.getValue().taskIdProperty());
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        instructionsCol.setCellValueFactory(c -> c.getValue().instructionsConsumedProperty());

        tasksTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.setSelectedTask(newV);
                selectedTaskIdLabel.setText(newV.getTaskId());
                selectedNameLabel.setText(newV.getName());
                selectedStatusLabel.setText(newV.getStatus());
                selectedWorkerLabel.setText(newV.getAssignedWorkerId().isBlank() ? "Unassigned" : newV.getAssignedWorkerId());
                selectedInstructionsLabel.setText(newV.getInstructionsConsumed() + " / " + newV.getInstructionBudget());

                double progress = newV.getInstructionBudget() > 0
                        ? (double) newV.getInstructionsConsumed() / newV.getInstructionBudget()
                        : 0.0;
                instructionProgressBar.setProgress(Math.min(1.0, progress));

                abortButton.setDisable("COMPLETED".equalsIgnoreCase(newV.getStatus()) || "FAILED".equalsIgnoreCase(newV.getStatus()) || "ABORTED".equalsIgnoreCase(newV.getStatus()));
            }
        });
    }

    @FXML
    private void onAbortClicked() {
        if (viewModel != null) viewModel.triggerAbort("Operator requested abort");
    }
}
