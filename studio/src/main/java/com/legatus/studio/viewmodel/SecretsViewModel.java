package com.legatus.studio.viewmodel;

import com.legatus.studio.model.SecretItem;
import com.legatus.studio.service.SecretsService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the Secrets Vault management view (Issue #409).
 */
public class SecretsViewModel {

    private final ObservableList<SecretItem> secrets = FXCollections.observableArrayList();
    private final ObjectProperty<SecretItem> selectedSecret = new SimpleObjectProperty<>();

    private final StringProperty newKeyInput = new SimpleStringProperty("");
    private final StringProperty newScopeInput = new SimpleStringProperty("GLOBAL");
    private final StringProperty newValueInput = new SimpleStringProperty("");
    private final BooleanProperty isBusy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final SecretsService secretsService;

    public SecretsViewModel(SecretsService secretsService) {
        this.secretsService = Objects.requireNonNull(secretsService, "secretsService must not be null");
    }

    public CompletableFuture<Void> refreshSecrets() {
        isBusy.set(true);
        return secretsService.getSecrets()
                .thenAccept(list -> {
                    secrets.clear();
                    list.forEach(dto -> secrets.add(new SecretItem(dto.key(), dto.scope(), dto.created_at(), dto.updated_at())));
                    isBusy.set(false);
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Failed to load secrets: " + ex.getCause().getMessage());
                    return null;
                });
    }

    public CompletableFuture<Boolean> createSecret() {
        String key = newKeyInput.get().trim();
        String val = newValueInput.get();
        String scope = newScopeInput.get();

        if (key.isBlank() || val.isBlank()) {
            statusMessage.set("Secret key and plaintext value are required.");
            return CompletableFuture.completedFuture(false);
        }

        isBusy.set(true);
        return secretsService.createSecret(key, scope, val)
                .thenApply(v -> {
                    SecretItem item = new SecretItem(key, scope, Instant.now().toString(), Instant.now().toString());
                    secrets.add(item);
                    selectedSecret.set(item);
                    newKeyInput.set("");
                    newValueInput.set("");
                    isBusy.set(false);
                    statusMessage.set("Secret '" + key + "' stored securely.");
                    return true;
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Failed to create secret: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> deleteSelectedSecret() {
        SecretItem current = selectedSecret.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isBusy.set(true);
        return secretsService.deleteSecret(current.getKey())
                .thenApply(v -> {
                    secrets.remove(current);
                    selectedSecret.set(secrets.isEmpty() ? null : secrets.get(0));
                    isBusy.set(false);
                    statusMessage.set("Secret '" + current.getKey() + "' deleted.");
                    return true;
                })
                .exceptionally(ex -> {
                    isBusy.set(false);
                    statusMessage.set("Failed to delete secret: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public ObservableList<SecretItem> getSecrets() { return secrets; }
    public ObjectProperty<SecretItem> selectedSecretProperty() { return selectedSecret; }
    public SecretItem getSelectedSecret() { return selectedSecret.get(); }
    public void setSelectedSecret(SecretItem item) { this.selectedSecret.set(item); }

    public StringProperty newKeyInputProperty() { return newKeyInput; }
    public StringProperty newScopeInputProperty() { return newScopeInput; }
    public StringProperty newValueInputProperty() { return newValueInput; }
    public BooleanProperty isBusyProperty() { return isBusy; }
    public StringProperty statusMessageProperty() { return statusMessage; }
}
