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
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        accounts.clear();
                        list.forEach(a -> accounts.add(new OperatorAccount(
                                a.account_id(), a.username(), a.email(), a.role(), a.status(), a.created_at(), a.last_login_at()
                        )));
                        isBusy.set(false);
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Failed to load accounts: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
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
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        newUsername.set("");
                        newEmail.set("");
                        newPassword.set("");
                        isBusy.set(false);
                        statusMessage.set("Account '" + u + "' created successfully.");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Account creation failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public CompletableFuture<Boolean> updateUsername(String newUsername) {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);
        if (newUsername == null || newUsername.trim().isBlank()) {
            statusMessage.set("Username cannot be empty.");
            return CompletableFuture.completedFuture(false);
        }
        String trimmed = newUsername.trim();
        isBusy.set(true);
        return userService.updateAccount(current.getAccountId(), trimmed, current.getEmail(), current.getRole())
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        current.setUsername(trimmed);
                        isBusy.set(false);
                        statusMessage.set("Username updated to '" + trimmed + "'.");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Failed to update username: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public CompletableFuture<Boolean> updateEmail(String newEmail) {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);
        if (newEmail == null || newEmail.trim().isBlank() || !newEmail.contains("@")) {
            statusMessage.set("Valid email address is required.");
            return CompletableFuture.completedFuture(false);
        }
        String trimmed = newEmail.trim();
        isBusy.set(true);
        return userService.updateAccount(current.getAccountId(), current.getUsername(), trimmed, current.getRole())
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        current.setEmail(trimmed);
                        isBusy.set(false);
                        statusMessage.set("Email updated to '" + trimmed + "'.");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Failed to update email: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public CompletableFuture<Boolean> updateRole(String newRole) {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);
        isBusy.set(true);
        return userService.updateAccount(current.getAccountId(), current.getUsername(), current.getEmail(), newRole)
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        current.setRole(newRole);
                        isBusy.set(false);
                        statusMessage.set("Role updated to " + newRole);
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Failed to update role: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public CompletableFuture<Boolean> resetPassword(String newPassword) {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);
        if (newPassword == null || newPassword.length() < 12) {
            statusMessage.set("Password must be at least 12 characters.");
            return CompletableFuture.completedFuture(false);
        }
        isBusy.set(true);
        return userService.resetPassword(current.getAccountId(), newPassword)
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Password reset successfully for user '" + current.getUsername() + "'.");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Password reset failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
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
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        current.setStatus(newStatus);
                        isBusy.set(false);
                        statusMessage.set("Account status updated to " + newStatus);
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Status update failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    public CompletableFuture<Boolean> deleteAccount() {
        OperatorAccount current = selectedAccount.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isBusy.set(true);
        return userService.deleteAccount(current.getAccountId())
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        accounts.remove(current);
                        selectedAccount.set(accounts.isEmpty() ? null : accounts.get(0));
                        isBusy.set(false);
                        statusMessage.set("Account deleted.");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Delete failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
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
