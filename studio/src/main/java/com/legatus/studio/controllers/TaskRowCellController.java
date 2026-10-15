package com.legatus.studio.controllers;

import com.legatus.studio.model.TaskRecord;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class TaskRowCellController {

    @FXML private Label taskIdLabel;
    @FXML private Label nameLabel;
    @FXML private Label statusBadge;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    public void updateTask(TaskRecord task) {
        if (task == null) return;
        taskIdLabel.textProperty().bind(task.taskIdProperty());
        nameLabel.textProperty().bind(task.nameProperty());
        statusBadge.textProperty().bind(task.statusProperty());

        double pct = task.getInstructionBudget() > 0 ? (double) task.getInstructionsConsumed() / task.getInstructionBudget() : 0.0;
        progressBar.setProgress(Math.min(1.0, pct));
        progressLabel.setText(String.format("%.1f%%", pct * 100));
    }
}
