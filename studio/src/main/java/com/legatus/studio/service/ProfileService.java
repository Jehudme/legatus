package com.legatus.studio.service;

import com.legatus.studio.config.ConfigStore;
import com.legatus.studio.config.StudioConfig;
import com.legatus.studio.model.ConnectionProfile;
import com.legatus.studio.security.AesGcmCrypto;
import com.legatus.studio.security.KeyStoreService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing cluster connection profiles and AES-256-GCM encrypted
 * token persistence.
 */
public class ProfileService {

    private final ConfigStore configStore;

    public ProfileService() {
        this(new ConfigStore());
    }

    public ProfileService(ConfigStore configStore) {
        this.configStore = Objects.requireNonNull(configStore, "configStore must not be null");
    }

    public List<ConnectionProfile> listProfiles() {
        return configStore.load().profiles();
    }

    public Optional<ConnectionProfile> getProfile(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return listProfiles().stream().filter(p -> id.equals(p.id())).findFirst();
    }

    public Optional<ConnectionProfile> getActiveProfile() {
        StudioConfig config = configStore.load();
        String activeId = config.activeProfileId();
        if (activeId == null) {
            return Optional.empty();
        }
        return getProfile(activeId);
    }

    public void setActiveProfile(String profileId) throws IOException {
        StudioConfig current = configStore.load();
        StudioConfig updated = new StudioConfig(
                current.theme(),
                current.windowWidth(),
                current.windowHeight(),
                current.windowX(),
                current.windowY(),
                current.maximized(),
                current.connectionTimeoutMs(),
                current.heartbeatIntervalMs(),
                current.logBufferLimit(),
                profileId,
                current.profiles()
        );
        configStore.save(updated);
    }

    public void saveProfile(ConnectionProfile profile, String plaintextRefreshToken) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(profile, "profile must not be null");

        String encryptedToken = profile.encryptedRefreshToken();
        if (plaintextRefreshToken != null && !plaintextRefreshToken.isBlank()) {
            byte[] masterKey = KeyStoreService.getMasterKey();
            encryptedToken = AesGcmCrypto.encryptString(plaintextRefreshToken, masterKey);
        }

        ConnectionProfile updatedProfile = new ConnectionProfile(
                profile.id(),
                profile.name(),
                profile.serverUrl(),
                profile.username(),
                encryptedToken,
                profile.trustAllCerts(),
                profile.caCertPath(),
                profile.lastConnected()
        );

        StudioConfig current = configStore.load();
        List<ConnectionProfile> profiles = new ArrayList<>(current.profiles());

        int existingIndex = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id().equals(updatedProfile.id())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            profiles.set(existingIndex, updatedProfile);
        } else {
            profiles.add(updatedProfile);
        }

        String activeId = current.activeProfileId() == null ? updatedProfile.id() : current.activeProfileId();

        StudioConfig updated = new StudioConfig(
                current.theme(),
                current.windowWidth(),
                current.windowHeight(),
                current.windowX(),
                current.windowY(),
                current.maximized(),
                current.connectionTimeoutMs(),
                current.heartbeatIntervalMs(),
                current.logBufferLimit(),
                activeId,
                profiles
        );
        configStore.save(updated);
    }

    public String getDecryptedRefreshToken(ConnectionProfile profile) throws GeneralSecurityException {
        if (profile == null || profile.encryptedRefreshToken() == null || profile.encryptedRefreshToken().isBlank()) {
            return null;
        }
        byte[] masterKey = KeyStoreService.getMasterKey();
        return AesGcmCrypto.decryptString(profile.encryptedRefreshToken(), masterKey);
    }

    public void deleteProfile(String profileId) throws IOException {
        if (profileId == null) {
            return;
        }
        StudioConfig current = configStore.load();
        List<ConnectionProfile> profiles = new ArrayList<>(current.profiles());
        profiles.removeIf(p -> profileId.equals(p.id()));

        String activeId = current.activeProfileId();
        if (profileId.equals(activeId)) {
            activeId = profiles.isEmpty() ? null : profiles.get(0).id();
        }

        StudioConfig updated = new StudioConfig(
                current.theme(),
                current.windowWidth(),
                current.windowHeight(),
                current.windowX(),
                current.windowY(),
                current.maximized(),
                current.connectionTimeoutMs(),
                current.heartbeatIntervalMs(),
                current.logBufferLimit(),
                activeId,
                profiles
        );
        configStore.save(updated);
    }
}
