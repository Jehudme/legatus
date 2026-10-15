package com.legatus.studio.config;

import com.legatus.studio.model.ConnectionProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadDefaultsWhenMissing() {
        Path configPath = tempDir.resolve("studio.json");
        ConfigStore store = new ConfigStore(configPath);

        StudioConfig config = store.load();
        assertNotNull(config);
        assertEquals("PRIMER_DARK", config.theme());
        assertEquals(1280.0, config.windowWidth());
        assertEquals(800.0, config.windowHeight());
        assertTrue(config.profiles().isEmpty());
    }

    @Test
    void testSaveAndLoadConfig() throws IOException {
        Path configPath = tempDir.resolve("studio.json");
        ConfigStore store = new ConfigStore(configPath);

        ConnectionProfile profile = new ConnectionProfile(
                "p-123",
                "Prod Cluster",
                "https://legatus.internal:8443",
                "admin",
                "enc-token-blob",
                true,
                null,
                Instant.now()
        );

        StudioConfig config = new StudioConfig(
                "NORD_DARK",
                1920.0,
                1080.0,
                100.0,
                100.0,
                true,
                15000,
                30000,
                5000,
                "p-123",
                List.of(profile)
        );

        store.save(config);
        assertTrue(Files.exists(configPath));

        ConfigStore reloadStore = new ConfigStore(configPath);
        StudioConfig loaded = reloadStore.load();

        assertEquals("NORD_DARK", loaded.theme());
        assertEquals(1920.0, loaded.windowWidth());
        assertEquals(1080.0, loaded.windowHeight());
        assertTrue(loaded.maximized());
        assertEquals("p-123", loaded.activeProfileId());
        assertEquals(1, loaded.profiles().size());

        ConnectionProfile loadedProfile = loaded.profiles().get(0);
        assertEquals("Prod Cluster", loadedProfile.name());
        assertEquals("https://legatus.internal:8443", loadedProfile.serverUrl());
        assertTrue(loadedProfile.trustAllCerts());
        assertEquals("enc-token-blob", loadedProfile.encryptedRefreshToken());
    }
}
