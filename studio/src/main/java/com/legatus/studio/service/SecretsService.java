package com.legatus.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing encrypted cluster secrets vault.
 */
public class SecretsService {

    private final RestClient restClient;

    public SecretsService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public CompletableFuture<List<RestDto.SecretDto>> getSecrets() {
        return restClient.get("/api/v1/admin/secrets", new TypeReference<List<RestDto.SecretDto>>() {});
    }

    public CompletableFuture<Void> createSecret(String key, String scope, String plainValue) {
        RestDto.CreateSecretRequest request = new RestDto.CreateSecretRequest(
                key,
                scope != null ? scope : "GLOBAL",
                plainValue
        );
        return restClient.post("/api/v1/admin/secrets", request, Void.class);
    }

    public CompletableFuture<Void> deleteSecret(String key) {
        return restClient.delete("/api/v1/admin/secrets/" + key, Void.class);
    }
}
