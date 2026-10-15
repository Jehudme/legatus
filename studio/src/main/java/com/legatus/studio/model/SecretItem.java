package com.legatus.studio.model;

import javafx.beans.property.*;

/**
 * Observable cluster secret item model for the Secrets Vault view.
 */
public class SecretItem {

    private final StringProperty key = new SimpleStringProperty("");
    private final StringProperty scope = new SimpleStringProperty("GLOBAL");
    private final StringProperty createdAt = new SimpleStringProperty("");
    private final StringProperty updatedAt = new SimpleStringProperty("");

    public SecretItem() {}

    public SecretItem(String key, String scope, String createdAt, String updatedAt) {
        this.key.set(key);
        this.scope.set(scope);
        this.createdAt.set(createdAt);
        this.updatedAt.set(updatedAt);
    }

    public StringProperty keyProperty() { return key; }
    public String getKey() { return key.get(); }
    public void setKey(String value) { this.key.set(value); }

    public StringProperty scopeProperty() { return scope; }
    public String getScope() { return scope.get(); }
    public void setScope(String value) { this.scope.set(value); }

    public StringProperty createdAtProperty() { return createdAt; }
    public String getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(String value) { this.createdAt.set(value); }

    public StringProperty updatedAtProperty() { return updatedAt; }
    public String getUpdatedAt() { return updatedAt.get(); }
    public void setUpdatedAt(String value) { this.updatedAt.set(value); }
}
