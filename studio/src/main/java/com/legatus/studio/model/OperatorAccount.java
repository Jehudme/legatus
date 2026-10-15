package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable operator account model driving the Admin Console User Management view.
 */
public class OperatorAccount {

    private final StringProperty accountId = new SimpleStringProperty("");
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");
    private final StringProperty role = new SimpleStringProperty("OPERATOR");
    private final StringProperty status = new SimpleStringProperty("ACTIVE");
    private final StringProperty createdAt = new SimpleStringProperty("");
    private final StringProperty lastLoginAt = new SimpleStringProperty("");

    public OperatorAccount() {}

    public OperatorAccount(String accountId, String username, String email, String role, String status, String createdAt, String lastLoginAt) {
        this.accountId.set(accountId);
        this.username.set(username);
        this.email.set(email);
        this.role.set(role);
        this.status.set(status);
        this.createdAt.set(createdAt);
        this.lastLoginAt.set(lastLoginAt);
    }

    public StringProperty accountIdProperty() { return accountId; }
    public String getAccountId() { return accountId.get(); }
    public void setAccountId(String value) { this.accountId.set(value); }

    public StringProperty usernameProperty() { return username; }
    public String getUsername() { return username.get(); }
    public void setUsername(String value) { this.username.set(value); }

    public StringProperty emailProperty() { return email; }
    public String getEmail() { return email.get(); }
    public void setEmail(String value) { this.email.set(value); }

    public StringProperty roleProperty() { return role; }
    public String getRole() { return role.get(); }
    public void setRole(String value) { this.role.set(value); }

    public StringProperty statusProperty() { return status; }
    public String getStatus() { return status.get(); }
    public void setStatus(String value) { this.status.set(value); }

    public StringProperty createdAtProperty() { return createdAt; }
    public String getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(String value) { this.createdAt.set(value); }

    public StringProperty lastLoginAtProperty() { return lastLoginAt; }
    public String getLastLoginAt() { return lastLoginAt.get(); }
    public void setLastLoginAt(String value) { this.lastLoginAt.set(value); }
}
