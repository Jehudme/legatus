package com.legatus.studio.service;

import com.legatus.studio.config.ConfigStore;
import com.legatus.studio.config.PathResolver;
import com.legatus.studio.model.ConnectionProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ProfileServiceTest {

    @TempDir
    Path tempDir;

    private ProfileService profileService;
    private ConfigStore configStore;

    @BeforeEach
    void setUp() {
        Path configPath = tempDir.resolve("studio.json");
        PathResolver.setConfigOverride(configPath);
        configStore = new ConfigStore(configPath);
        profileService = new ProfileService(configStore);
    }

    @AfterEach
    void tearDown() {
        PathResolver.setConfigOverride(null);
    }

    @Test
    void testSaveProfileEncryptsToken() throws IOException, GeneralSecurityException {
        ConnectionProfile profile = new ConnectionProfile(
                "cluster-dev",
                "Dev Cluster",
                "https://127.0.0.1:8443",
                "operator",
                null,
                true,
                null,
                Instant.now()
        );

        String rawToken = "super-confidential-refresh-token-xyz";
        profileService.saveProfile(profile, rawToken);

        List<ConnectionProfile> profiles = profileService.listProfiles();
        assertEquals(1, profiles.size());

        ConnectionProfile stored = profiles.get(0);
        assertEquals("cluster-dev", stored.id());
        assertNotNull(stored.encryptedRefreshToken());
        assertNotEquals(rawToken, stored.encryptedRefreshToken());

        String decryptedToken = profileService.getDecryptedRefreshToken(stored);
        assertEquals(rawToken, decryptedToken);
    }

    @Test
    void testActiveProfileAndDeletion() throws IOException, GeneralSecurityException {
        ConnectionProfile p1 = new ConnectionProfile("p1", "Cluster 1", "https://c1:8443", "u1", null, false, null, Instant.now());
        ConnectionProfile p2 = new ConnectionProfile("p2", "Cluster 2", "https://c2:8443", "u2", null, false, null, Instant.now());

        profileService.saveProfile(p1, "token1");
        profileService.saveProfile(p2, "token2");

        assertEquals(2, profileService.listProfiles().size());

        profileService.setActiveProfile("p2");
        Optional<ConnectionProfile> active = profileService.getActiveProfile();
        assertTrue(active.isPresent());
        assertEquals("p2", active.get().id());

        profileService.deleteProfile("p1");
        assertEquals(1, profileService.listProfiles().size());
        assertEquals("p2", profileService.listProfiles().get(0).id());
    }
}
