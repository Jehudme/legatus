package com.legatus.studio.controllers;

import com.legatus.studio.model.AuditLogEntry;
import com.legatus.studio.viewmodel.AuditTrailViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AuditLogController {

    @FXML private TextField actorFilterField;
    @FXML private TextField actionFilterField;
    @FXML private TableView<AuditLogEntry> auditTable;
    @FXML private TableColumn<AuditLogEntry, Number> seqCol;
    @FXML private TableColumn<AuditLogEntry, String> timestampCol;
    @FXML private TableColumn<AuditLogEntry, String> actorCol;
    @FXML private TableColumn<AuditLogEntry, String> actionCol;
    @FXML private TableColumn<AuditLogEntry, String> statusCol;
    @FXML private TableColumn<AuditLogEntry, String> hashCol;

    @FXML private Button verifyChainButton;
    @FXML private Label verificationBadge;
    @FXML private Label verificationDetailsLabel;

    private AuditTrailViewModel viewModel;

    public void setViewModel(AuditTrailViewModel viewModel) {
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

        actorFilterField.textProperty().bindBidirectional(viewModel.filterActorProperty());
        actionFilterField.textProperty().bindBidirectional(viewModel.filterActionProperty());
        auditTable.setItems(viewModel.getFilteredAuditLogs());

        seqCol.setCellValueFactory(c -> c.getValue().sequenceNumberProperty());
        timestampCol.setCellValueFactory(c -> c.getValue().timestampProperty());
        actorCol.setCellValueFactory(c -> c.getValue().actorIdProperty());
        actionCol.setCellValueFactory(c -> c.getValue().actionProperty());
        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        hashCol.setCellValueFactory(c -> c.getValue().sha256HashProperty());

        verifyChainButton.disableProperty().bind(viewModel.isBusyProperty());
        verificationBadge.textProperty().bind(viewModel.verificationStatusProperty());
        verificationDetailsLabel.textProperty().bind(viewModel.verificationDetailsProperty());
    }

    @FXML
    private void onVerifyClicked() {
        if (viewModel != null) viewModel.verifyChainIntegrity();
    }
}
