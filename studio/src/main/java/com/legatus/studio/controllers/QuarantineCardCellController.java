package com.legatus.studio.controllers;

import com.legatus.studio.model.QuarantineEnrollment;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class QuarantineCardCellController {

    @FXML private Label workerIdLabel;
    @FXML private Label hostnameLabel;
    @FXML private Label visualCodeLabel;

    public void updateEnrollment(QuarantineEnrollment enrollment) {
        if (enrollment == null) return;
        workerIdLabel.textProperty().bind(enrollment.workerIdProperty());
        hostnameLabel.textProperty().bind(enrollment.hostnameProperty());
        visualCodeLabel.textProperty().bind(enrollment.visualCodeProperty());
    }

    public Label getWorkerIdLabel() { return workerIdLabel; }
    public Label getHostnameLabel() { return hostnameLabel; }
    public Label getVisualCodeLabel() { return visualCodeLabel; }
}
