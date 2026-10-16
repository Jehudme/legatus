package com.legatus.studio.controllers;

import com.legatus.studio.model.OperatorAccount;
import com.legatus.studio.security.OperatorSession;
import com.legatus.studio.viewmodel.UserManagementViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class AdminUsersController {

    @FXML private VBox accessDeniedPane;
    @FXML private SplitPane mainAdminPane;

    @FXML private Button provisionUserButton;
    @FXML private Button refreshButton;

    @FXML private TableView<OperatorAccount> accountsTable;
    @FXML private TableColumn<OperatorAccount, String> usernameCol;
    @FXML private TableColumn<OperatorAccount, String> emailCol;
    @FXML private TableColumn<OperatorAccount, String> roleCol;
    @FXML private TableColumn<OperatorAccount, String> statusCol;

    @FXML private VBox userInspectorPane;
    @FXML private TextField editUsernameField;
    @FXML private Button saveUsernameButton;
    @FXML private TextField editEmailField;
    @FXML private Button saveEmailButton;
    @FXML private Label statusBadgeLabel;
    @FXML private Button toggleStatusButton;
    @FXML private Label roleBadgeLabel;
    @FXML private Button toggleRoleButton;
    @FXML private VBox selfLockoutBanner;
    @FXML private Button resetPasswordButton;
    @FXML private Button deleteAccountButton;
    @FXML private Label statusLabel;

    // Provision Modal
    @FXML private VBox provisionModal;
    @FXML private TextField newUsernameField;
    @FXML private TextField newEmailField;
    @FXML private PasswordField newPasswordField;
    @FXML private ComboBox<String> newRoleCombo;
    @FXML private Button createAccountButton;
    @FXML private Button cancelProvisionButton;
    @FXML private Label provisionStatusLabel;

    // Password Reset Modal
    @FXML private VBox resetPasswordModal;
    @FXML private Label resetTargetUserLabel;
    @FXML private PasswordField resetPasswordField;
    @FXML private PasswordField resetConfirmPasswordField;
    @FXML private ProgressBar passwordStrengthBar;
    @FXML private Label passwordStrengthLabel;
    @FXML private Button confirmResetPasswordButton;
    @FXML private Button cancelResetPasswordButton;
    @FXML private Label resetStatusLabel;

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

        // 1. RBAC Gating
        boolean isAdmin = OperatorSession.getInstance().isAdmin();
        if (accessDeniedPane != null) {
            accessDeniedPane.setVisible(!isAdmin);
            accessDeniedPane.setManaged(!isAdmin);
        }
        if (mainAdminPane != null) {
            mainAdminPane.setVisible(isAdmin);
            mainAdminPane.setManaged(isAdmin);
        }

        if (!isAdmin) {
            return;
        }

        // 2. Table setup
        accountsTable.setItems(viewModel.getAccounts());
        usernameCol.setCellValueFactory(c -> c.getValue().usernameProperty());
        emailCol.setCellValueFactory(c -> c.getValue().emailProperty());
        roleCol.setCellValueFactory(c -> c.getValue().roleProperty());
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
                    String bg = "ACTIVE".equalsIgnoreCase(item) ? "#15803d" : "#b91c1c";
                    badge.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-padding: 2 8 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // 3. Master-Detail Inspector Visibility
        if (userInspectorPane != null) {
            userInspectorPane.visibleProperty().bind(accountsTable.getSelectionModel().selectedItemProperty().isNotNull());
            userInspectorPane.managedProperty().bind(userInspectorPane.visibleProperty());
        }

        // Selection Listener
        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            viewModel.setSelectedAccount(newV);
            if (newV != null) {
                editUsernameField.setText(newV.getUsername());
                editEmailField.setText(newV.getEmail());
                statusBadgeLabel.setText(newV.getStatus());
                roleBadgeLabel.setText(newV.getRole());

                // Self-lockout check
                String activeAccountId = OperatorSession.getInstance().getAccountId();
                String activeUsername = OperatorSession.getInstance().getUsername();
                boolean isSelf = (activeAccountId != null && activeAccountId.equalsIgnoreCase(newV.getAccountId()))
                        || (activeUsername != null && activeUsername.equalsIgnoreCase(newV.getUsername()));

                if (selfLockoutBanner != null) {
                    selfLockoutBanner.setVisible(isSelf);
                    selfLockoutBanner.setManaged(isSelf);
                }

                toggleStatusButton.setDisable(isSelf);
                toggleRoleButton.setDisable(isSelf && "CLUSTER_ADMIN".equalsIgnoreCase(newV.getRole()));
                deleteAccountButton.setDisable(isSelf);
            }
        });

        // Password strength meter listener
        if (resetPasswordField != null) {
            resetPasswordField.textProperty().addListener((obs, oldV, newV) -> updatePasswordStrength(newV));
        }

        if (statusLabel != null) {
            statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        }

        viewModel.refreshAccounts();
    }

    private void updatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            passwordStrengthBar.setProgress(0.0);
            passwordStrengthLabel.setText("Too Short");
            passwordStrengthLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px; -fx-font-weight: bold;");
            return;
        }

        double score = 0.0;
        if (password.length() >= 12) score += 0.25;
        if (password.matches(".*[A-Z].*") && password.matches(".*[a-z].*")) score += 0.25;
        if (password.matches(".*\\d.*")) score += 0.25;
        if (password.matches(".*[^A-Za-z0-9].*")) score += 0.25;

        passwordStrengthBar.setProgress(score);
        if (score < 0.5) {
            passwordStrengthLabel.setText("Weak (< 12 chars / missing mixed case or digits)");
            passwordStrengthLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else if (score < 1.0) {
            passwordStrengthLabel.setText("Moderate (Good length)");
            passwordStrengthLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            passwordStrengthLabel.setText("Strong (Meets enterprise policy)");
            passwordStrengthLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void onSaveUsernameClicked() {
        if (viewModel != null && editUsernameField != null) {
            viewModel.updateUsername(editUsernameField.getText().trim());
        }
    }

    @FXML
    private void onSaveEmailClicked() {
        if (viewModel != null && editEmailField != null) {
            viewModel.updateEmail(editEmailField.getText().trim());
        }
    }

    @FXML
    private void onToggleStatusClicked() {
        if (viewModel != null) {
            viewModel.toggleStatus().thenAccept(ok -> {
                if (ok && viewModel.getSelectedAccount() != null) {
                    statusBadgeLabel.setText(viewModel.getSelectedAccount().getStatus());
                }
            });
        }
    }

    @FXML
    private void onToggleRoleClicked() {
        if (viewModel != null && viewModel.getSelectedAccount() != null) {
            String currentRole = viewModel.getSelectedAccount().getRole();
            String nextRole = "CLUSTER_ADMIN".equalsIgnoreCase(currentRole) ? "OPERATOR" : "CLUSTER_ADMIN";
            viewModel.updateRole(nextRole).thenAccept(ok -> {
                if (ok && viewModel.getSelectedAccount() != null) {
                    roleBadgeLabel.setText(viewModel.getSelectedAccount().getRole());
                }
            });
        }
    }

    @FXML
    private void onDeleteClicked() {
        if (viewModel != null) {
            viewModel.deleteAccount();
        }
    }

    @FXML
    private void onRefreshClicked() {
        if (viewModel != null) {
            viewModel.refreshAccounts();
        }
    }

    // Provision Modal Actions
    @FXML
    private void onOpenProvisionModal() {
        newUsernameField.clear();
        newEmailField.clear();
        newPasswordField.clear();
        newRoleCombo.getItems().setAll("OPERATOR", "CLUSTER_ADMIN");
        newRoleCombo.setValue("OPERATOR");
        provisionStatusLabel.setText("");

        provisionModal.setVisible(true);
        provisionModal.setManaged(true);
    }

    @FXML
    private void onCloseProvisionModal() {
        provisionModal.setVisible(false);
        provisionModal.setManaged(false);
    }

    @FXML
    private void onCreateClicked() {
        if (viewModel == null) return;
        String u = newUsernameField.getText().trim();
        String em = newEmailField.getText().trim();
        String pw = newPasswordField.getText();
        String r = newRoleCombo.getValue();

        if (u.isBlank() || em.isBlank() || pw.isBlank()) {
            provisionStatusLabel.setText("All fields are required.");
            return;
        }

        viewModel.newUsernameProperty().set(u);
        viewModel.newEmailProperty().set(em);
        viewModel.newPasswordProperty().set(pw);
        viewModel.newRoleProperty().set(r != null ? r : "OPERATOR");

        viewModel.createAccount().thenAccept(success -> {
            if (success) {
                com.legatus.studio.service.FxAsync.runOnFxThread(this::onCloseProvisionModal);
            } else {
                com.legatus.studio.service.FxAsync.runOnFxThread(() ->
                        provisionStatusLabel.setText(viewModel.statusMessageProperty().get()));
            }
        });
    }

    // Password Reset Modal Actions
    @FXML
    private void onOpenResetPasswordModal() {
        OperatorAccount acc = viewModel != null ? viewModel.getSelectedAccount() : null;
        if (acc == null) return;

        resetTargetUserLabel.setText("Setting new credentials for operator: " + acc.getUsername());
        resetPasswordField.clear();
        resetConfirmPasswordField.clear();
        resetStatusLabel.setText("");
        updatePasswordStrength("");

        resetPasswordModal.setVisible(true);
        resetPasswordModal.setManaged(true);
    }

    @FXML
    private void onCloseResetPasswordModal() {
        resetPasswordModal.setVisible(false);
        resetPasswordModal.setManaged(false);
    }

    @FXML
    private void onConfirmResetPasswordClicked() {
        if (viewModel == null) return;
        String pw = resetPasswordField.getText();
        String confirm = resetConfirmPasswordField.getText();

        if (pw == null || pw.length() < 12) {
            resetStatusLabel.setText("Password must be at least 12 characters.");
            return;
        }
        if (!pw.equals(confirm)) {
            resetStatusLabel.setText("Passwords do not match.");
            return;
        }

        viewModel.resetPassword(pw).thenAccept(success -> {
            if (success) {
                com.legatus.studio.service.FxAsync.runOnFxThread(this::onCloseResetPasswordModal);
            } else {
                com.legatus.studio.service.FxAsync.runOnFxThread(() ->
                        resetStatusLabel.setText(viewModel.statusMessageProperty().get()));
            }
        });
    }
}
