package com.legatus.studio.controllers;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.viewmodel.QuarantineViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class QuarantineAttestationController {

    @FXML private ListView<QuarantineEnrollment> enrollmentsListView;
    @FXML private Label workerIdLabel;
    @FXML private Label hostnameLabel;
    @FXML private Label ipLabel;
    @FXML private Label osArchLabel;
    @FXML private Label coresMemLabel;
    @FXML private Label submittedAtLabel;
    @FXML private Label fingerprintLabel;
    @FXML private Label visualCodeLabel;
    @FXML private TextField leaseDurationField;
    @FXML private TextField rejectReasonField;
    @FXML private CheckBox blacklistIpCheckBox;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;

    @FXML private javafx.scene.layout.HBox autoAcceptBox;
    @FXML private CheckBox autoAcceptCheckBox;
    @FXML private javafx.scene.layout.HBox autoAcceptBanner;
    @FXML private Label pendingCountBadge;
    @FXML private javafx.scene.layout.VBox attestationPane;

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

        // Auto-Accept toggle & banner
        if (autoAcceptBox != null) {
            autoAcceptBox.visibleProperty().bind(viewModel.isAdminProperty());
            autoAcceptBox.managedProperty().bind(autoAcceptBox.visibleProperty());
        }
        if (autoAcceptCheckBox != null) {
            autoAcceptCheckBox.setSelected(viewModel.isAutoAccept());
            viewModel.autoAcceptProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && autoAcceptCheckBox.isSelected() != newV) {
                    autoAcceptCheckBox.setSelected(newV);
                }
            });
        }
        if (autoAcceptBanner != null && autoAcceptCheckBox != null) {
            autoAcceptBanner.visibleProperty().bind(autoAcceptCheckBox.selectedProperty());
            autoAcceptBanner.managedProperty().bind(autoAcceptBanner.visibleProperty());
        }

        // Pending count badge
        if (pendingCountBadge != null) {
            var countBinding = javafx.beans.binding.Bindings.createStringBinding(
                    () -> viewModel.getEnrollments().size() + " pending",
                    viewModel.getEnrollments()
            );
            pendingCountBadge.textProperty().bind(countBinding);
        }

        // Custom rich card cell in enrollmentsListView
        enrollmentsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(QuarantineEnrollment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    javafx.scene.layout.VBox cellBox = new javafx.scene.layout.VBox(3);
                    cellBox.setPadding(new javafx.geometry.Insets(6, 8, 6, 8));

                    Label hostLabel = new Label(item.getHostname());
                    hostLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #f8fafc; -fx-font-size: 13px;");

                    Label metaLabel = new Label("IP: " + item.getSourceIp() + " | " + item.getAllocatableCores() + " cores | "
                            + (item.getMaxMemoryBytes() / (1024 * 1024 * 1024)) + " GB");
                    metaLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

                    String fp = item.getFingerprintHex();
                    String shortFp = fp != null && fp.length() > 23 ? fp.substring(0, 23) + "..." : (fp != null ? fp : "");
                    Label fpLabel = new Label("Key: " + shortFp);
                    fpLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-family: monospace; -fx-font-size: 10px;");

                    cellBox.getChildren().addAll(hostLabel, metaLabel, fpLabel);
                    setGraphic(cellBox);
                }
            }
        });

        enrollmentsListView.setItems(viewModel.getEnrollments());

        // Attestation pane hidden if no row is selected
        if (attestationPane != null) {
            attestationPane.visibleProperty().bind(enrollmentsListView.getSelectionModel().selectedItemProperty().isNotNull());
            attestationPane.managedProperty().bind(attestationPane.visibleProperty());
        }

        enrollmentsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.setSelectedEnrollment(newV);
                if (workerIdLabel != null) workerIdLabel.setText(newV.getWorkerId());
                if (hostnameLabel != null) hostnameLabel.setText(newV.getHostname());
                if (ipLabel != null) ipLabel.setText(newV.getSourceIp());
                if (osArchLabel != null) osArchLabel.setText(newV.getOs() + " / " + newV.getArchitecture());
                if (coresMemLabel != null) coresMemLabel.setText(newV.getAllocatableCores() + " cores | " + (newV.getMaxMemoryBytes() / (1024 * 1024 * 1024)) + " GB");
                if (submittedAtLabel != null) submittedAtLabel.setText(newV.getSubmittedAt());
                if (fingerprintLabel != null) fingerprintLabel.setText(newV.getFingerprintHex());
                if (visualCodeLabel != null) visualCodeLabel.setText(newV.getVisualCode());
            } else {
                viewModel.setSelectedEnrollment(null);
                if (workerIdLabel != null) workerIdLabel.setText("-");
                if (hostnameLabel != null) hostnameLabel.setText("-");
                if (ipLabel != null) ipLabel.setText("-");
                if (osArchLabel != null) osArchLabel.setText("-");
                if (coresMemLabel != null) coresMemLabel.setText("-");
                if (submittedAtLabel != null) submittedAtLabel.setText("-");
                if (fingerprintLabel != null) fingerprintLabel.setText("-");
                if (visualCodeLabel != null) visualCodeLabel.setText("-");
            }
        });

        if (leaseDurationField != null) leaseDurationField.textProperty().bindBidirectional(viewModel.leaseDurationProperty());
        if (rejectReasonField != null) rejectReasonField.textProperty().bindBidirectional(viewModel.rejectionReasonProperty());
        if (blacklistIpCheckBox != null) blacklistIpCheckBox.selectedProperty().bindBidirectional(viewModel.blacklistIpProperty());

        if (approveButton != null) approveButton.disableProperty().bind(viewModel.isProcessingProperty());
        if (rejectButton != null) rejectButton.disableProperty().bind(viewModel.isProcessingProperty());
    }

    @FXML
    public void onAutoAcceptToggled() {
        if (viewModel != null && autoAcceptCheckBox != null) {
            viewModel.setAutoAccept(autoAcceptCheckBox.isSelected());
        }
    }

    @FXML
    public void onApproveClicked() {
        if (viewModel != null) {
            QuarantineEnrollment selected = viewModel.getSelectedEnrollment();
            if (selected == null) return;

            TextInputDialog dialog = new TextInputDialog(selected.getHostname());
            dialog.setTitle("Assign Friendly Worker Name");
            dialog.setHeaderText("Attestation Approval for Node: " + selected.getHostname());
            dialog.setContentText("Assign human-readable friendly name for this worker:");

            var opt = dialog.showAndWait();
            if (opt.isPresent()) {
                String friendlyName = opt.get().trim();
                viewModel.approveSelected(friendlyName.isEmpty() ? selected.getHostname() : friendlyName);
            }
        }
    }

    @FXML
    public void onRejectClicked() {
        if (viewModel != null) viewModel.rejectSelected();
    }

    public ListView<QuarantineEnrollment> getEnrollmentsListView() {
        return enrollmentsListView;
    }

    public VBox getAttestationPane() {
        return attestationPane;
    }

    public CheckBox getAutoAcceptCheckBox() {
        return autoAcceptCheckBox;
    }

    public HBox getAutoAcceptBanner() {
        return autoAcceptBanner;
    }
}
