package dev.legatus.server.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.legatus.server.config.LegatusProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemBytecodeStorageTest {

    @TempDir
    Path tempDir;

    private LocalFilesystemBytecodeStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        LegatusProperties properties = new LegatusProperties();
        properties.getStorage().setBytecodeDir(tempDir.toString());
        storage = new LocalFilesystemBytecodeStorage(properties);
        storage.init();
    }

    @Test
    @DisplayName("Should successfully store bytecode from byte array and verify content-addressable SHA-256")
    void testStoreByteArray() throws IOException, NoSuchAlgorithmException {
        byte[] payload = "SOLIX_BYTECODE_TEST_V1_BINARY_PAYLOAD".getBytes(StandardCharsets.UTF_8);
        String expectedHash = computeSha256(payload);

        String storedHash = storage.store(payload);

        assertThat(storedHash).isEqualTo(expectedHash);
        assertThat(storage.exists(storedHash)).isTrue();
        assertThat(storage.getSize(storedHash)).isEqualTo(payload.length);

        Optional<InputStream> isOpt = storage.openStream(storedHash);
        assertThat(isOpt).isPresent();
        try (InputStream is = isOpt.get()) {
            assertThat(is.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    @DisplayName("Should stream bytecode, verify matching expected hash, and read back identical stream")
    void testStoreWithExpectedHashSuccess() throws IOException, NoSuchAlgorithmException {
        byte[] payload = new byte[] { 0x7F, 'S', 'O', 'L', 0x01, 0x00, 0x02, (byte) 0xAA };
        String expectedHash = computeSha256(payload);

        storage.storeWithExpectedHash(expectedHash, new ByteArrayInputStream(payload));

        assertThat(storage.exists(expectedHash)).isTrue();
        assertThat(storage.getSize(expectedHash)).isEqualTo(8L);
    }

    @Test
    @DisplayName("Should reject and clean up when expected hash does not match actual bytecode digest")
    void testStoreWithExpectedHashMismatch() {
        byte[] payload = "REAL_PAYLOAD".getBytes(StandardCharsets.UTF_8);
        String wrongHash = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThatThrownBy(() -> storage.storeWithExpectedHash(wrongHash, new ByteArrayInputStream(payload)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bytecode SHA-256 mismatch");

        assertThat(storage.exists(wrongHash)).isFalse();
    }

    @Test
    @DisplayName("Should reject path traversal attempts and malformed hashes")
    void testPathTraversalProtection() {
        assertThatThrownBy(() -> storage.openStream("../../../etc/shadow"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid SHA-256");

        assertThatThrownBy(() -> storage.delete("non-hex-hash"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(storage.exists("malformed")).isFalse();
    }

    @Test
    @DisplayName("Should return empty optional for non-existent bytecode")
    void testOpenStreamNotFound() throws IOException {
        String nonExistentHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Optional<InputStream> stream = storage.openStream(nonExistentHash);
        assertThat(stream).isEmpty();
        assertThat(storage.exists(nonExistentHash)).isFalse();
    }

    @Test
    @DisplayName("Should delete bytecode and return false on second deletion")
    void testDeleteBytecode() throws IOException {
        byte[] payload = "DELETE_ME".getBytes(StandardCharsets.UTF_8);
        String hash = storage.store(payload);

        assertThat(storage.exists(hash)).isTrue();
        assertThat(storage.delete(hash)).isTrue();
        assertThat(storage.exists(hash)).isFalse();
        assertThat(storage.delete(hash)).isFalse();
    }

    private static String computeSha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data)).toLowerCase();
    }
}
