package com.legatus.studio.viewmodel;

import com.legatus.studio.transport.LstcpClient;
import javafx.beans.property.*;

/**
 * Root ViewModel coordinating active views, connection status, and drawer visibility.
 */
public class MainViewModel {

    public enum NavigationModule {
        OVERVIEW("Cluster Overview", "cluster_overview"),
        FLEET("Worker Fleet", "fleet_topology"),
        QUARANTINE("Quarantine Queue", "quarantine_attestation"),
        FUNCTIONS("Functions & Bytecode", "functions_workbench"),
        TASKS("Task Explorer", "task_explorer"),
        SECRETS("Secrets Vault", "secrets_vault"),
        USERS("User Management", "admin_users"),
        AUDIT("Audit Trail", "audit_log"),
        SETTINGS("Studio Settings", "settings_dialog");

        private final String title;
        private final String fxmlName;

        NavigationModule(String title, String fxmlName) {
            this.title = title;
            this.fxmlName = fxmlName;
        }

        public String getTitle() { return title; }
        public String getFxmlName() { return fxmlName; }
    }

    private final ObjectProperty<NavigationModule> currentModule = new SimpleObjectProperty<>(NavigationModule.OVERVIEW);
    private final StringProperty connectionStatusText = new SimpleStringProperty("DISCONNECTED");
    private final BooleanProperty isConnected = new SimpleBooleanProperty(false);
    private final BooleanProperty isDrawerExpanded = new SimpleBooleanProperty(true);
    private final StringProperty activeProfileName = new SimpleStringProperty("No Profile");

    public MainViewModel() {}

    public void navigateTo(NavigationModule module) {
        if (module != null) {
            currentModule.set(module);
        }
    }

    public void toggleDrawer() {
        isDrawerExpanded.set(!isDrawerExpanded.get());
    }

    public void updateConnectionState(LstcpClient.State state) {
        if (state == null) {
            connectionStatusText.set("DISCONNECTED");
            isConnected.set(false);
            return;
        }
        connectionStatusText.set(state.name());
        isConnected.set(state == LstcpClient.State.STREAMING);
    }

    public ObjectProperty<NavigationModule> currentModuleProperty() { return currentModule; }
    public NavigationModule getCurrentModule() { return currentModule.get(); }
    public void setCurrentModule(NavigationModule module) { this.currentModule.set(module); }

    public StringProperty connectionStatusTextProperty() { return connectionStatusText; }
    public String getConnectionStatusText() { return connectionStatusText.get(); }

    public BooleanProperty isConnectedProperty() { return isConnected; }
    public boolean isIsConnected() { return isConnected.get(); }

    public BooleanProperty isDrawerExpandedProperty() { return isDrawerExpanded; }
    public boolean isIsDrawerExpanded() { return isDrawerExpanded.get(); }
    public void setIsDrawerExpanded(boolean value) { this.isDrawerExpanded.set(value); }

    public StringProperty activeProfileNameProperty() { return activeProfileName; }
    public String getActiveProfileName() { return activeProfileName.get(); }
    public void setActiveProfileName(String value) { this.activeProfileName.set(value); }
}
