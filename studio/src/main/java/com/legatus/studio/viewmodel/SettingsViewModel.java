package com.legatus.studio.viewmodel;

import com.legatus.studio.config.ConfigStore;
import com.legatus.studio.config.PathResolver;
import com.legatus.studio.config.StudioConfig;
import com.legatus.studio.service.ThemeService;
import javafx.beans.property.*;

import java.io.IOException;
import java.util.Objects;

/**
 * ViewModel driving the Studio Settings view and theme toggling (Issue #410).
 */
public class SettingsViewModel {

    private final ObjectProperty<ThemeService.Theme> currentTheme = new SimpleObjectProperty<>(ThemeService.getCurrentTheme());
    private final IntegerProperty connectionTimeoutMs = new SimpleIntegerProperty(10000);
    private final IntegerProperty heartbeatIntervalMs = new SimpleIntegerProperty(15000);
    private final IntegerProperty logBufferLimit = new SimpleIntegerProperty(10000);

    private final StringProperty configPath = new SimpleStringProperty(PathResolver.getConfigPath().toString());
    private final StringProperty logPath = new SimpleStringProperty(PathResolver.getLogPath().toString());
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final ConfigStore configStore;

    public SettingsViewModel(ConfigStore configStore) {
        this.configStore = Objects.requireNonNull(configStore, "configStore must not be null");
        loadFromConfig();
    }

    public void loadFromConfig() {
        StudioConfig config = configStore.load();
        connectionTimeoutMs.set(config.connectionTimeoutMs());
        heartbeatIntervalMs.set(config.heartbeatIntervalMs());
        logBufferLimit.set(config.logBufferLimit());
    }

    public void toggleTheme() {
        ThemeService.toggleTheme();
        currentTheme.set(ThemeService.getCurrentTheme());
    }

    public boolean saveSettings() {
        try {
            StudioConfig current = configStore.load();
            StudioConfig updated = new StudioConfig(
                    currentTheme.get().name(),
                    current.windowWidth(),
                    current.windowHeight(),
                    current.windowX(),
                    current.windowY(),
                    current.maximized(),
                    connectionTimeoutMs.get(),
                    heartbeatIntervalMs.get(),
                    logBufferLimit.get(),
                    current.activeProfileId(),
                    current.profiles()
            );
            configStore.save(updated);
            statusMessage.set("Settings saved.");
            return true;
        } catch (IOException e) {
            statusMessage.set("Failed to save settings: " + e.getMessage());
            return false;
        }
    }

    public ObjectProperty<ThemeService.Theme> currentThemeProperty() { return currentTheme; }
    public IntegerProperty connectionTimeoutMsProperty() { return connectionTimeoutMs; }
    public IntegerProperty heartbeatIntervalMsProperty() { return heartbeatIntervalMs; }
    public IntegerProperty logBufferLimitProperty() { return logBufferLimit; }
    public StringProperty configPathProperty() { return configPath; }
    public StringProperty logPathProperty() { return logPath; }
    public StringProperty statusMessageProperty() { return statusMessage; }
}
