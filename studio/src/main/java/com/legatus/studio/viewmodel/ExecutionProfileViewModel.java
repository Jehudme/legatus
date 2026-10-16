package com.legatus.studio.viewmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.ExecutionProfileService;
import com.legatus.studio.service.FxAsync;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel managing Execution Profiles lifecycle, reactive prepending at index 0,
 * and schema-validated JSON import/export (MS-015).
 */
public class ExecutionProfileViewModel {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ObservableList<ExecutionProfile> profiles = FXCollections.observableArrayList();
    private final ObjectProperty<ExecutionProfile> selectedProfile = new SimpleObjectProperty<>();

    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private final ExecutionProfileService profileService;
    private final FunctionsViewModel functionsViewModel;

    public ExecutionProfileViewModel(ExecutionProfileService profileService) {
        this(profileService, null);
    }

    public ExecutionProfileViewModel(ExecutionProfileService profileService, FunctionsViewModel functionsViewModel) {
        this.profileService = Objects.requireNonNull(profileService, "profileService must not be null");
        this.functionsViewModel = functionsViewModel;
    }

    public CompletableFuture<Void> loadProfiles() {
        isLoading.set(true);
        return profileService.getProfiles(null)
                .thenAccept(list -> FxAsync.runOnFxThread(() -> {
                    isLoading.set(false);
                    profiles.clear();
                    for (RestDto.ExecutionProfileDto dto : list) {
                        ExecutionProfile p = ExecutionProfile.fromDto(dto);
                        resolveFunctionName(p);
                        profiles.add(p);
                    }
                    if (profiles.isEmpty()) {
                        seedDefaultProfiles();
                    }
                    if (!profiles.isEmpty() && selectedProfile.get() == null) {
                        selectedProfile.set(profiles.get(0));
                    }
                }))
                .exceptionally(ex -> {
                    FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        seedDefaultProfiles();
                        errorMessage.set("Failed to load profiles from cluster: " + ex.getMessage());
                    });
                    return null;
                });
    }

    public void resolveFunctionName(ExecutionProfile p) {
        if (p == null) return;
        if (p.getFunctionId() != null && functionsViewModel != null) {
            for (BytecodeItem item : functionsViewModel.getBytecodeItems()) {
                if (p.getFunctionId().equals(item.getFunctionId())) {
                    p.setFunctionName(item.getFunctionName());
                    return;
                }
            }
        }
        if (p.getFunctionName() == null || p.getFunctionName().isBlank()) {
            p.setFunctionName(p.getFunctionId() != null ? p.getFunctionId().toString().substring(0, 8) : "General");
        }
    }

    public void seedDefaultProfiles() {
        profiles.clear();
        UUID funcId = null;
        String funcName = "Default";
        if (functionsViewModel != null && !functionsViewModel.getBytecodeItems().isEmpty()) {
            BytecodeItem first = functionsViewModel.getBytecodeItems().get(0);
            funcId = first.getFunctionId();
            funcName = first.getFunctionName();
        }

        ExecutionProfile pDefault = new ExecutionProfile("", "Standard Run", funcId);
        pDefault.setFunctionName(funcName);
        pDefault.setArgumentsJson("{\n  \"action\": \"process\",\n  \"batch_size\": 50\n}");
        pDefault.setInstructionBudget(10_000_000L);
        pDefault.setTimeLimitMs(60_000L);
        pDefault.setMemoryMb(64L);
        pDefault.setPriority("NORMAL");
        pDefault.setNotifyOnFailure(false);
        profiles.add(0, pDefault);

        ExecutionProfile pFast = new ExecutionProfile("", "Fast Verification", funcId);
        pFast.setFunctionName(funcName);
        pFast.setArgumentsJson("{\n  \"quick\": true\n}");
        pFast.setInstructionBudget(1_000_000L);
        pFast.setTimeLimitMs(5_000L);
        pFast.setMemoryMb(32L);
        pFast.setPriority("HIGH");
        pFast.setNotifyOnFailure(true);
        profiles.add(0, pFast);

        ExecutionProfile pUnbounded = new ExecutionProfile("", "Heavy Batch Computation", funcId);
        pUnbounded.setFunctionName(funcName);
        pUnbounded.setArgumentsJson("{}");
        pUnbounded.setInstructionBudget(0L);
        pUnbounded.setUnlimitedInstructions(true);
        pUnbounded.setTimeLimitMs(0L);
        pUnbounded.setUnlimitedTime(true);
        pUnbounded.setMemoryMb(256L);
        pUnbounded.setPriority("BATCH");
        pUnbounded.setNotifyOnFailure(true);
        profiles.add(0, pUnbounded);

        if (!profiles.isEmpty()) {
            selectedProfile.set(profiles.get(0));
        }
    }

    public CompletableFuture<ExecutionProfile> createProfile(ExecutionProfile newProfile) {
        Objects.requireNonNull(newProfile, "newProfile must not be null");
        if (newProfile.getProfileName() == null || newProfile.getProfileName().isBlank()) {
            errorMessage.set("Profile name must not be empty.");
            return CompletableFuture.failedFuture(new IllegalArgumentException("Profile name must not be empty."));
        }

        isLoading.set(true);
        statusMessage.set("Creating profile '" + newProfile.getProfileName() + "'...");

        return profileService.saveProfile(newProfile.toDto())
                .thenApply(saved -> {
                    FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        newProfile.setProfileId(saved.profile_id());
                        newProfile.setVersion(saved.version() != null ? saved.version() : 0L);
                        // Enforce top-of-list prepending at index 0 (MS-015 requirement)
                        profiles.add(0, newProfile);
                        selectedProfile.set(newProfile);
                        statusMessage.set("Profile '" + newProfile.getProfileName() + "' created successfully.");
                    });
                    return newProfile;
                })
                .exceptionally(ex -> {
                    FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        errorMessage.set("Failed to create profile: " + msg);
                    });
                    throw new RuntimeException(ex);
                });
    }

    public CompletableFuture<Void> saveProfile(ExecutionProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        isLoading.set(true);
        statusMessage.set("Saving profile '" + profile.getProfileName() + "'...");

        return profileService.saveProfile(profile.toDto())
                .thenAccept(saved -> FxAsync.runOnFxThread(() -> {
                    isLoading.set(false);
                    profile.setProfileId(saved.profile_id());
                    profile.setVersion(saved.version() != null ? saved.version() : 0L);
                    statusMessage.set("Profile '" + profile.getProfileName() + "' saved successfully.");
                }))
                .exceptionally(ex -> {
                    FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        errorMessage.set("Failed to save profile: " + msg);
                    });
                    return null;
                });
    }

    public CompletableFuture<Void> deleteProfile(ExecutionProfile profile) {
        if (profile == null) return CompletableFuture.completedFuture(null);

        isLoading.set(true);
        if (profile.getProfileId() != null && !profile.getProfileId().isBlank()) {
            return profileService.deleteProfile(profile.getProfileId())
                    .thenRun(() -> FxAsync.runOnFxThread(() -> {
                        isLoading.set(false);
                        profiles.remove(profile);
                        if (selectedProfile.get() == profile) {
                            selectedProfile.set(profiles.isEmpty() ? null : profiles.get(0));
                        }
                        statusMessage.set("Profile deleted.");
                    }))
                    .exceptionally(ex -> {
                        FxAsync.runOnFxThread(() -> {
                            isLoading.set(false);
                            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                            errorMessage.set("Delete failed: " + msg);
                        });
                        return null;
                    });
        } else {
            profiles.remove(profile);
            if (selectedProfile.get() == profile) {
                selectedProfile.set(profiles.isEmpty() ? null : profiles.get(0));
            }
            isLoading.set(false);
            statusMessage.set("Local profile removed.");
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Validates and imports profile JSON file, enforcing schema integrity against ExecutionProfileDto (MS-015 P2).
     */
    public CompletableFuture<ExecutionProfile> importProfileFromJsonFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }

        String rawJson = Files.readString(file);
        RestDto.ExecutionProfileDto dto = validateProfileJsonSchema(rawJson);

        ExecutionProfile imported = ExecutionProfile.fromDto(dto);
        imported.setProfileId(""); // reset ID for new import
        resolveFunctionName(imported);

        return createProfile(imported);
    }

    /**
     * Validates JSON string against ExecutionProfileDto schema.
     */
    public static RestDto.ExecutionProfileDto validateProfileJsonSchema(String rawJson) throws JsonProcessingException {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("Profile JSON content is empty.");
        }

        JsonNode root = MAPPER.readTree(rawJson);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Invalid profile format: root must be a JSON object.");
        }

        if (!root.has("profile_name") || root.get("profile_name").asText().isBlank()) {
            throw new IllegalArgumentException("Schema validation failed: missing or empty required field 'profile_name'.");
        }

        if (root.has("arguments_json") && !root.get("arguments_json").isNull()) {
            String args = root.get("arguments_json").asText();
            if (!args.isBlank()) {
                try {
                    MAPPER.readTree(args);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Schema validation failed: arguments_json contains invalid JSON syntax: " + e.getMessage());
                }
            }
        }

        return MAPPER.treeToValue(root, RestDto.ExecutionProfileDto.class);
    }

    public ObservableList<ExecutionProfile> getProfiles() { return profiles; }
    public ObjectProperty<ExecutionProfile> selectedProfileProperty() { return selectedProfile; }
    public ExecutionProfile getSelectedProfile() { return selectedProfile.get(); }
    public void setSelectedProfile(ExecutionProfile profile) { this.selectedProfile.set(profile); }

    public BooleanProperty isLoadingProperty() { return isLoading; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty errorMessageProperty() { return errorMessage; }
}
