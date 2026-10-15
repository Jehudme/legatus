package com.legatus.studio.controllers;

import com.legatus.studio.model.OperatorAccount;
import com.legatus.studio.viewmodel.UserManagementViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AdminUsersController {

    @FXML private TableView<OperatorAccount> accountsTable;
    @FXML private TableColumn<OperatorAccount, String> usernameCol;
    @FXML private TableColumn<OperatorAccount, String> emailCol;
    @FXML private TableColumn<OperatorAccount, String> roleCol;
    @FXML private TableColumn<OperatorAccount, String> statusCol;

    @FXML private TextField newUsernameField;
    @FXML private TextField newEmailField;
    @FXML private PasswordField newPasswordField;
    @FXML private ComboBox<String> newRoleCombo;
    @FXML private Button createAccountButton;
    @FXML private Button toggleStatusButton;
    @FXML private Button deleteAccountButton;
    @FXML private Label statusLabel;

    private UserManagementViewModel viewModel;

    public void setViewModel(UserManagementViewModel viewModel) {
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

        accountsTable.setItems(viewModel.getAccounts());
        usernameCol.setCellValueFactory(c -> c.getValue().usernameProperty());
        emailCol.setCellValueFactory(c -> c.getValue().emailProperty());
        roleCol.setCellValueFactory(c -> c.getValue().roleProperty());
        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());

        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            viewModel.setSelectedAccount(newV);
            boolean hasSel = (newV != null);
            toggleStatusButton.setDisable(!hasSel);
            deleteAccountButton.setDisable(!hasSel);
        });

        newUsernameField.textProperty().bindBidirectional(viewModel.newUsernameProperty());
        newEmailField.textProperty().bindBidirectional(viewModel.newEmailProperty());
        newPasswordField.textProperty().bindBidirectional(viewModel.newPasswordProperty());
        createAccountButton.disableProperty().bind(viewModel.isBusyProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    }

    @FXML
    private void onCreateClicked() {
        if (viewModel != null) viewModel.createAccount();
    }

    @FXML
    private void onToggleStatusClicked() {
        if (viewModel != null) viewModel.toggleStatus();
    }

    @FXML
    private void onDeleteClicked() {
        if (viewModel != null) viewModel.deleteAccount();
    }
}
