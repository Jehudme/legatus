package com.legatus.studio.controllers;

import com.legatus.studio.Application;
import com.legatus.studio.model.ConnectionProfile;
import com.legatus.studio.viewmodel.LoginViewModel;
import com.legatus.studio.service.AuthService;
import com.legatus.studio.service.ProfileService;
import com.legatus.studio.transport.RestClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {

    @FXML private TextField serverUrlField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox trustAllCertsCheckBox;
    @FXML private CheckBox rememberPasswordCheckBox;
    @FXML private ComboBox<ConnectionProfile> profileComboBox;
    @FXML private Button connectButton;
    @FXML private ProgressIndicator progressSpinner;
    @FXML private Label errorLabel;

    private LoginViewModel viewModel;

    public void setViewModel(LoginViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    @FXML
    public void initialize() {
        if (viewModel == null) {
            RestClient restClient = new RestClient("http://127.0.0.1:8443", true);
            AuthService authService = new AuthService(restClient);
            ProfileService profileService = new ProfileService();
            this.viewModel = new LoginViewModel(authService, profileService);
        }
        bindViewModel();
    }

    private void bindViewModel() {
        if (viewModel == null) return;

        serverUrlField.textProperty().bindBidirectional(viewModel.serverUrlProperty());
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        trustAllCertsCheckBox.selectedProperty().bindBidirectional(viewModel.trustAllCertsProperty());
        rememberPasswordCheckBox.selectedProperty().bindBidirectional(viewModel.rememberPasswordProperty());

        profileComboBox.setItems(viewModel.getProfiles());
        profileComboBox.valueProperty().bindBidirectional(viewModel.selectedProfileProperty());
        profileComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.selectProfile(newV);
            }
        });

        connectButton.disableProperty().bind(viewModel.isLoadingProperty());
        progressSpinner.visibleProperty().bind(viewModel.isLoadingProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
    }

    @FXML
    private void onConnectClicked() {
        if (viewModel == null) return;
        viewModel.executeLogin().thenAccept(success -> {
            if (success) {
                // Initialize StudioContext with active credentials and connection parameters
                com.legatus.studio.service.StudioContext.getInstance()
                        .initSession(viewModel.getServerUrl(), viewModel.isTrustAllCerts());

                Platform.runLater(() -> {
                    try {
                        Application.setRoot("shell");
                    } catch (Exception e) {
                        errorLabel.setText("Failed to load cockpit: " + e.getMessage());
                    }
                });
            }
        });
    }
}
