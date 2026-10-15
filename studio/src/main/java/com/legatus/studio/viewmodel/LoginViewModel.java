package com.legatus.studio.viewmodel;

import com.legatus.studio.model.ConnectionProfile;
import com.legatus.studio.service.AuthService;
import com.legatus.studio.service.ProfileService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel managing operator login, cluster connection profiles, and credential encryption.
 */
public class LoginViewModel {

    private final StringProperty serverUrl = new SimpleStringProperty("http://127.0.0.1:8443");
    private final StringProperty username = new SimpleStringProperty("admin");
    private final StringProperty password = new SimpleStringProperty("admin");
    private final BooleanProperty trustAllCerts = new SimpleBooleanProperty(true);
    private final BooleanProperty rememberPassword = new SimpleBooleanProperty(true);
    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private final ObservableList<ConnectionProfile> profiles = FXCollections.observableArrayList();
    private final ObjectProperty<ConnectionProfile> selectedProfile = new SimpleObjectProperty<>();

    private final AuthService authService;
    private final ProfileService profileService;

    public LoginViewModel(AuthService authService, ProfileService profileService) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService must not be null");
        loadProfiles();
    }

    public void loadProfiles() {
        profiles.clear();
        profiles.addAll(profileService.listProfiles());
        profileService.getActiveProfile().ifPresent(this::selectProfile);
    }

    public void selectProfile(ConnectionProfile profile) {
        if (profile == null) return;
        selectedProfile.set(profile);
        serverUrl.set(profile.serverUrl());
        username.set(profile.username());
        trustAllCerts.set(profile.trustAllCerts());
    }

    public CompletableFuture<Boolean> executeLogin() {
        errorMessage.set("");
        isLoading.set(true);

        String u = username.get();
        String p = password.get();

        if (u.isBlank() || p.isBlank()) {
            errorMessage.set("Username and password are required.");
            isLoading.set(false);
            return CompletableFuture.completedFuture(false);
        }

        try {
            authService.setRestClient(new com.legatus.studio.transport.RestClient(serverUrl.get(), trustAllCerts.get()));
        } catch (Exception e) {
            errorMessage.set("Invalid server URL: " + e.getMessage());
            isLoading.set(false);
            return CompletableFuture.completedFuture(false);
        }

        return authService.login(u, p)
                .thenApply(response -> {
                    isLoading.set(false);
                    // Persist or update profile if rememberPassword is checked
                    if (rememberPassword.get()) {
                        try {
                            ConnectionProfile prof = selectedProfile.get();
                            if (prof == null) {
                                prof = ConnectionProfile.createDefault(u + "@" + serverUrl.get(), serverUrl.get(), u);
                            }
                            profileService.saveProfile(prof, response.refresh_token());
                            loadProfiles();
                        } catch (Exception ignored) {}
                    }
                    return true;
                })
                .exceptionally(ex -> {
                    isLoading.set(false);
                    errorMessage.set("Authentication failed: " + ex.getCause().getMessage());
                    return false;
                });
    }

    public StringProperty serverUrlProperty() { return serverUrl; }
    public String getServerUrl() { return serverUrl.get(); }
    public void setServerUrl(String value) { this.serverUrl.set(value); }

    public StringProperty usernameProperty() { return username; }
    public String getUsername() { return username.get(); }
    public void setUsername(String value) { this.username.set(value); }

    public StringProperty passwordProperty() { return password; }
    public String getPassword() { return password.get(); }
    public void setPassword(String value) { this.password.set(value); }

    public BooleanProperty trustAllCertsProperty() { return trustAllCerts; }
    public boolean isTrustAllCerts() { return trustAllCerts.get(); }
    public void setTrustAllCerts(boolean value) { this.trustAllCerts.set(value); }

    public BooleanProperty rememberPasswordProperty() { return rememberPassword; }
    public boolean isRememberPassword() { return rememberPassword.get(); }
    public void setRememberPassword(boolean value) { this.rememberPassword.set(value); }

    public BooleanProperty isLoadingProperty() { return isLoading; }
    public boolean isIsLoading() { return isLoading.get(); }

    public StringProperty errorMessageProperty() { return errorMessage; }
    public String getErrorMessage() { return errorMessage.get(); }

    public ObservableList<ConnectionProfile> getProfiles() { return profiles; }
    public ObjectProperty<ConnectionProfile> selectedProfileProperty() { return selectedProfile; }
}
