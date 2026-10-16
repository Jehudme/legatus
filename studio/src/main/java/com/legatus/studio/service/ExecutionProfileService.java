package com.legatus.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing Execution Profile persistence, import/export, and REST mutations (MS-009).
 */
public class ExecutionProfileService {

    private static final ObjectMapper FILE_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final RestClient restClient;

    public ExecutionProfileService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public CompletableFuture<List<RestDto.ExecutionProfileDto>> getProfiles(UUID functionId) {
        String path = "/api/v1/execution-profiles?functionId=" + (functionId != null ? functionId.toString() : "");
        return restClient.get(path, new TypeReference<List<RestDto.ExecutionProfileDto>>() {});
    }

    public CompletableFuture<RestDto.ExecutionProfileDto> getProfile(String profileId) {
        return restClient.get("/api/v1/execution-profiles/" + profileId, RestDto.ExecutionProfileDto.class);
    }

    public CompletableFuture<RestDto.ExecutionProfileDto> saveProfile(RestDto.ExecutionProfileDto profile) {
        if (profile.profile_id() == null || profile.profile_id().isBlank()) {
            return restClient.post("/api/v1/execution-profiles", profile, RestDto.ExecutionProfileDto.class);
        } else {
            return restClient.put("/api/v1/execution-profiles/" + profile.profile_id(), profile, RestDto.ExecutionProfileDto.class);
        }
    }

    public CompletableFuture<Void> deleteProfile(String profileId) {
        return restClient.delete("/api/v1/execution-profiles/" + profileId, Void.class);
    }

    public void exportProfile(RestDto.ExecutionProfileDto profile, Path destinationFile) throws IOException {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(destinationFile, "destinationFile must not be null");
        String json = FILE_MAPPER.writeValueAsString(profile);
        Files.writeString(destinationFile, json);
    }

    public RestDto.ExecutionProfileDto importProfile(Path sourceFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        String json = Files.readString(sourceFile);
        return FILE_MAPPER.readValue(json, RestDto.ExecutionProfileDto.class);
    }
}
