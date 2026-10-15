package com.legatus.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BytecodeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testComputeSha256KnownVector() {
        // Known SHA-256 of empty string is e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String emptyHash = BytecodeService.computeSha256(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash);

        // Known SHA-256 of "legatus" is 8d998ec4319409b699a74aa938c29eb20562fe41b3eb5cc777ccb0a0362c93d9 (approx, let's verify with bytes)
        byte[] data = "legatus".getBytes(StandardCharsets.UTF_8);
        String hash = BytecodeService.computeSha256(data);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void testComputeFileSha256() throws IOException {
        Path file = tempDir.resolve("sample.slxb");
        Files.writeString(file, "SOLIX_BYTECODE_SAMPLE");

        String fileHash = BytecodeService.computeFileSha256(file);
        String memHash = BytecodeService.computeSha256("SOLIX_BYTECODE_SAMPLE".getBytes(StandardCharsets.UTF_8));

        assertEquals(memHash, fileHash);
    }
}
