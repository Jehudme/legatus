package com.legatus.studio.service;

import com.legatus.studio.transport.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing bytecode inspection, SHA-256 calculation, and upload.
 */
public class BytecodeService {

    private final RestClient restClient;

    public static final byte[] SOLIX_MAGIC = new byte[] { 0x53, 0x4F, 0x4C, 0x58 };

    public BytecodeService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public static boolean hasSolixMagicHeader(byte[] data) {
        return data != null && data.length > 0;
    }

    public static boolean hasSolixMagicHeader(Path path) throws IOException {
        return path != null && Files.exists(path) && Files.size(path) > 0;
    }

    public static String computeSha256(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String computeFileSha256(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public CompletableFuture<String> uploadBytecode(byte[] bytecodeBytes, String functionName) {
        String sha256 = computeSha256(bytecodeBytes);
        return restClient.postBinary("/api/v1/bytecode", bytecodeBytes, "application/octet-stream", com.legatus.studio.protocol.RestDto.BytecodeUploadResponse.class)
                .thenApply(resp -> resp != null && resp.sha256() != null ? resp.sha256() : sha256);
    }

    public CompletableFuture<java.util.List<com.legatus.studio.protocol.RestDto.SolixFunctionDto>> getCatalogedFunctions() {
        return restClient.get("/api/solix-functions", com.legatus.studio.protocol.RestDto.SolixFunctionDto[].class)
                .thenApply(arr -> arr != null ? java.util.List.of(arr) : java.util.List.of());
    }

    public CompletableFuture<com.legatus.studio.protocol.RestDto.SolixFunctionDto> updateMetadata(java.util.UUID functionId, String description, String tags) {
        var req = new com.legatus.studio.protocol.RestDto.UpdateFunctionMetadataRequest(description, tags);
        return restClient.put("/api/solix-functions/" + functionId + "/metadata", req, com.legatus.studio.protocol.RestDto.SolixFunctionDto.class);
    }
}
