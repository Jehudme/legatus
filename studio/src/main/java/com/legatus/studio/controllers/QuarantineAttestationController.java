package com.legatus.studio.controllers;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.viewmodel.QuarantineViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class QuarantineAttestationController {

    @FXML private ListView<QuarantineEnrollment> enrollmentsListView;
    @FXML private Label workerIdLabel;
    @FXML private Label hostnameLabel;
    @FXML private Label ipLabel;
    @FXML private Label osArchLabel;
    @FXML private Label coresMemLabel;
    @FXML private Label fingerprintLabel;
    @FXML private Label visualCodeLabel;
    @FXML private TextField leaseDurationField;
    @FXML private TextField rejectReasonField;
    @FXML private CheckBox blacklistIpCheckBox;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;

    private QuarantineViewModel viewModel;

    public void setViewModel(QuarantineViewModel viewModel) {
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

        enrollmentsListView.setItems(viewModel.getEnrollments());
        enrollmentsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.setSelectedEnrollment(newV);
                workerIdLabel.setText(newV.getWorkerId());
                hostnameLabel.setText(newV.getHostname());
                ipLabel.setText(newV.getSourceIp());
                osArchLabel.setText(newV.getOs() + " / " + newV.getArchitecture());
                coresMemLabel.setText(newV.getAllocatableCores() + " cores | " + (newV.getMaxMemoryBytes() / (1024 * 1024 * 1024)) + " GB");
                fingerprintLabel.setText(newV.getFingerprintHex());
                visualCodeLabel.setText(newV.getVisualCode());
            }
        });

        leaseDurationField.textProperty().bindBidirectional(viewModel.leaseDurationProperty());
        rejectReasonField.textProperty().bindBidirectional(viewModel.rejectionReasonProperty());
        blacklistIpCheckBox.selectedProperty().bindBidirectional(viewModel.blacklistIpProperty());

        approveButton.disableProperty().bind(viewModel.isProcessingProperty());
        rejectButton.disableProperty().bind(viewModel.isProcessingProperty());
    }

    @FXML
    private void onApproveClicked() {
        if (viewModel != null) viewModel.approveSelected();
    }

    @FXML
    private void onRejectClicked() {
        if (viewModel != null) viewModel.rejectSelected();
    }
}
