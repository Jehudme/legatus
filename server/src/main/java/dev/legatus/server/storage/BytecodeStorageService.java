package dev.legatus.server.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Pluggable Content-Addressable Storage (CAS) contract for compiled Solix bytecode binaries.
 */
public interface BytecodeStorageService {

    /**
     * Streams bytecode into storage, computes its SHA-256 digest, and returns the hexadecimal SHA-256 hash.
     *
     * @param inputStream Binary stream of the compiled bytecode
     * @return Hexadecimal lowercase SHA-256 checksum (64 characters)
     * @throws IOException If I/O failure occurs
     */
    String store(InputStream inputStream) throws IOException;

    /**
     * Stores the bytecode byte array, computes its SHA-256 digest, and returns the hexadecimal SHA-256 hash.
     *
     * @param bytes Raw binary bytes
     * @return Hexadecimal lowercase SHA-256 checksum (64 characters)
     * @throws IOException If I/O failure occurs
     */
    String store(byte[] bytes) throws IOException;

    /**
     * Stores bytecode and verifies that the computed hash strictly matches the expected SHA-256 hash.
     *
     * @param expectedSha256 Expected hexadecimal SHA-256 digest
     * @param inputStream Binary stream of the compiled bytecode
     * @throws IOException If I/O failure occurs or hash digest does not match
     */
    void storeWithExpectedHash(String expectedSha256, InputStream inputStream) throws IOException;

    /**
     * Opens an input stream for reading the bytecode binary corresponding to the given SHA-256 digest.
     *
     * @param sha256 Hexadecimal SHA-256 digest
     * @return Optional containing the InputStream if present, empty otherwise
     * @throws IOException If I/O error occurs
     */
    Optional<InputStream> openStream(String sha256) throws IOException;

    /**
     * Checks whether bytecode with the specified SHA-256 digest is present in storage.
     *
     * @param sha256 Hexadecimal SHA-256 digest
     * @return true if exists, false otherwise
     */
    boolean exists(String sha256);

    /**
     * Returns the size in bytes of the stored bytecode binary.
     *
     * @param sha256 Hexadecimal SHA-256 digest
     * @return size in bytes
     * @throws IOException If the file does not exist or size cannot be determined
     */
    long getSize(String sha256) throws IOException;

    /**
     * Deletes the stored bytecode binary by SHA-256 digest.
     *
     * @param sha256 Hexadecimal SHA-256 digest
     * @return true if deleted, false if file was not found
     * @throws IOException If deletion fails
     */
    boolean delete(String sha256) throws IOException;
}
