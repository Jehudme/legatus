package com.legatus.studio.controllers;

import com.legatus.studio.model.SecretItem;
import com.legatus.studio.viewmodel.SecretsViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SecretsVaultController {

    @FXML private TableView<SecretItem> secretsTable;
    @FXML private TableColumn<SecretItem, String> keyCol;
    @FXML private TableColumn<SecretItem, String> scopeCol;
    @FXML private TableColumn<SecretItem, String> createdAtCol;

    @FXML private TextField newKeyField;
    @FXML private TextField newScopeField;
    @FXML private PasswordField newValueField;
    @FXML private Button createSecretButton;
    @FXML private Button deleteSecretButton;
    @FXML private Label statusLabel;

    private SecretsViewModel viewModel;

    public void setViewModel(SecretsViewModel viewModel) {
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

        secretsTable.setItems(viewModel.getSecrets());
        keyCol.setCellValueFactory(c -> c.getValue().keyProperty());
        scopeCol.setCellValueFactory(c -> c.getValue().scopeProperty());
        createdAtCol.setCellValueFactory(c -> c.getValue().createdAtProperty());

        secretsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            viewModel.setSelectedSecret(newV);
            deleteSecretButton.setDisable(newV == null);
        });

        newKeyField.textProperty().bindBidirectional(viewModel.newKeyInputProperty());
        newScopeField.textProperty().bindBidirectional(viewModel.newScopeInputProperty());
        newValueField.textProperty().bindBidirectional(viewModel.newValueInputProperty());
        createSecretButton.disableProperty().bind(viewModel.isBusyProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    }

    @FXML
    private void onCreateClicked() {
        if (viewModel != null) viewModel.createSecret();
    }

    @FXML
    private void onDeleteClicked() {
        if (viewModel != null) viewModel.deleteSelectedSecret();
    }
}
