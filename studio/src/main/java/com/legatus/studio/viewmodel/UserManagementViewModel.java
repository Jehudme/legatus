package com.legatus.studio.viewmodel;

import com.legatus.studio.model.OperatorAccount;
import com.legatus.studio.service.UserManagementService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the Admin Console User Management view.
 */
public class UserManagementViewModel {

    private final ObservableList<OperatorAccount> accounts = FXCollections.observableArrayList();
    private final ObjectProperty<OperatorAccount> selectedAccount = new SimpleObjectProperty<>();

    private final StringProperty newUsername = new SimpleStringProperty("");
    private final StringProperty newEmail = new SimpleStringProperty("");
    private final StringProperty newPassword = new SimpleStringProperty("");
    private final StringProperty newRole = new SimpleStringProperty("OPERATOR");
    private final BooleanProperty isBusy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final UserManagementService userService;

    public UserManagementViewModel(UserManagementService userService) {
        this.userService = Objects.requireNonNull(userService, "userService must not be null");
    }

    public CompletableFuture<Void> refreshAccounts() {
        isBusy.set(true);
        return userService.getAccounts()
                .thenAccept(list -> {
                    accounts.clear();
                    list.forEach(a -> accounts.add(new OperatorAccount(
                            a.account_id(), a.username(), a.email(), a.role(), a.status(), a.created_at(), a.last_login_at()
                    )));
                    isBusy.set(false);
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Failed to load accounts: " + ex.getCause().getMessage());
                    return null;
                });
    }

    public CompletableFuture<Boolean> createAccount() {
        String u = newUsername.get().trim();
        String em = newEmail.get().trim();
        String pw = newPassword.get();
        String r = newRole.get();

        if (u.isBlank() || em.isBlank() || pw.isBlank()) {
            statusMessage.set("Username, email, and temporary password are required.");
            return CompletableFuture.completedFuture(false);
        }

        isBusy.set(true);
        return userService.createAccount(u, em, pw, r)
                .thenApply(v -> {
                    refreshAccounts();
                    newUsername.set("");
                    newEmail.set("");
                    newPassword.set("");
                    isBusy.set(false);
                    statusMessage.set("Account '" + u + "' created successfully.");
                    return true;
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Account creation failed: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> toggleStatus() {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        String newStatus = "ACTIVE".equalsIgnoreCase(current.getStatus()) ? "SUSPENDED" : "ACTIVE";
        isBusy.set(true);

        return userService.updateAccountStatus(current.getAccountId(), newStatus)
                .thenApply(v -> {
                    current.setStatus(newStatus);
                    isBusy.set(false);
                    statusMessage.set("Account status updated to " + newStatus);
                    return true;
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Status update failed: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> deleteAccount() {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isBusy.set(true);
        return userService.deleteAccount(current.getAccountId())
                .thenApply(v -> {
                    accounts.remove(current);
                    selectedAccount.set(accounts.isEmpty() ? null : accounts.get(0));
                    isBusy.set(false);
                    statusMessage.set("Account deleted.");
                    return true;
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Delete failed: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public ObservableList<OperatorAccount> getAccounts() { return accounts; }
    public ObjectProperty<OperatorAccount> selectedAccountProperty() { return selectedAccount; }
    public OperatorAccount getSelectedAccount() { return selectedAccount.get(); }
    public void setSelectedAccount(OperatorAccount acc) { this.selectedAccount.set(acc); }

    public StringProperty newUsernameProperty() { return newUsername; }
    public StringProperty newEmailProperty() { return newEmail; }
    public StringProperty newPasswordProperty() { return newPassword; }
    public StringProperty newRoleProperty() { return newRole; }
    public BooleanProperty isBusyProperty() { return isBusy; }
    public StringProperty statusMessageProperty() { return statusMessage; }
}
