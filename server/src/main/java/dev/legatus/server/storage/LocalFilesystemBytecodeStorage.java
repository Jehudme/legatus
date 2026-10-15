package dev.legatus.server.storage;

import dev.legatus.server.config.LegatusProperties;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local filesystem implementation of {@link BytecodeStorageService}.
 * <p>
 * Stores bytecode binaries named by their SHA-256 cryptographic digest in
 * {@code legatus.storage.bytecode-dir}.
 */
@Service
public class LocalFilesystemBytecodeStorage implements BytecodeStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFilesystemBytecodeStorage.class);
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final Path storageDirectory;

    public LocalFilesystemBytecodeStorage(LegatusProperties properties) {
        this.storageDirectory = Paths.get(properties.getStorage().getBytecodeDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        if (!Files.exists(storageDirectory)) {
            LOG.info("Creating bytecode storage directory: {}", storageDirectory);
            Files.createDirectories(storageDirectory);
        }
    }

    @Override
    public String store(InputStream inputStream) throws IOException {
        Path tempFile = storageDirectory.resolve("tmp_" + UUID.randomUUID() + ".tmp");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
                Files.copy(dis, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest()).toLowerCase();
            Path targetFile = resolveAndValidate(sha256);

            // Atomic move into final content-addressable location
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            LOG.debug("Stored bytecode with SHA-256 {} ({} bytes)", sha256, Files.size(targetFile));
            return sha256;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        } finally {
            if (Files.exists(tempFile)) {
                try {
                    Files.delete(tempFile);
                } catch (IOException ignored) {
                    // Ignored on cleanup
                }
            }
        }
    }

    @Override
    public String store(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            return store(bais);
        }
    }

    @Override
    public void storeWithExpectedHash(String expectedSha256, InputStream inputStream) throws IOException {
        validateSha256(expectedSha256);
        String actualSha256 = store(inputStream);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            // Delete the mismatched file to avoid orphan cache entries
            delete(actualSha256);
            throw new IllegalArgumentException(
                "Bytecode SHA-256 mismatch: expected " + expectedSha256.toLowerCase() + " but computed " + actualSha256
            );
        }
    }

    @Override
    public Optional<InputStream> openStream(String sha256) throws IOException {
        Path targetFile = resolveAndValidate(sha256);
        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(targetFile));
    }

    @Override
    public boolean exists(String sha256) {
        try {
            Path targetFile = resolveAndValidate(sha256);
            return Files.exists(targetFile) && Files.isRegularFile(targetFile);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public long getSize(String sha256) throws IOException {
        Path targetFile = resolveAndValidate(sha256);
        if (!Files.exists(targetFile)) {
            throw new IllegalArgumentException("Bytecode not found for SHA-256: " + sha256);
        }
        return Files.size(targetFile);
    }

    @Override
    public boolean delete(String sha256) throws IOException {
        Path targetFile = resolveAndValidate(sha256);
        return Files.deleteIfExists(targetFile);
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    private Path resolveAndValidate(String sha256) {
        validateSha256(sha256);
        Path resolved = storageDirectory.resolve(sha256.toLowerCase() + ".bin").normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Path traversal attempt detected: " + sha256);
        }
        return resolved;
    }

    private void validateSha256(String sha256) {
        if (sha256 == null || !SHA256_PATTERN.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 hex digest: " + sha256);
        }
    }
}
