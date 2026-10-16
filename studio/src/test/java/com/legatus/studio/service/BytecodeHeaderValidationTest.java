package com.legatus.studio.service;

import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class BytecodeHeaderValidationTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testMagicHeaderBytesDirectValidation() {
        // Valid magic: 0x534F4C58 ("SOLX")
        byte[] validHeader = new byte[] { 0x53, 0x4F, 0x4C, 0x58, 0x01, 0x02, 0x03 };
        assertTrue(BytecodeService.hasSolixMagicHeader(validHeader), "Should accept valid SOLX magic header");

        // Invalid headers
        assertFalse(BytecodeService.hasSolixMagicHeader((byte[]) null), "Null data must be rejected");
        assertFalse(BytecodeService.hasSolixMagicHeader(new byte[0]), "Empty data must be rejected");
        assertFalse(BytecodeService.hasSolixMagicHeader(new byte[] { 0x53, 0x4F, 0x4C }), "Data shorter than 4 bytes must be rejected");

        // Arbitrary ASCII text
        byte[] textBytes = "ELF\0x02This is not bytecode".getBytes(StandardCharsets.UTF_8);
        assertFalse(BytecodeService.hasSolixMagicHeader(textBytes), "Arbitrary text must be rejected");

        // Altered magic bytes (e.g. SOLZ)
        byte[] altered = new byte[] { 0x53, 0x4F, 0x4C, 0x59 };
        assertFalse(BytecodeService.hasSolixMagicHeader(altered), "Altered magic bytes must be rejected");
    }

    @Test
    void testMagicHeaderFileValidation() throws IOException {
        Path invalidFile = tempDir.resolve("not_bytecode.txt");
        Files.writeString(invalidFile, "Hello world this is plaintext");
        assertFalse(BytecodeService.hasSolixMagicHeader(invalidFile));

        Path validFile = tempDir.resolve("valid.slxb");
        byte[] validContent = new byte[] { 0x53, 0x4F, 0x4C, 0x58, 0x00, 0x01, (byte) 0xAA, (byte) 0xBB };
        Files.write(validFile, validContent);
        assertTrue(BytecodeService.hasSolixMagicHeader(validFile));
    }

    @Test
    void testFunctionsViewModelRejectsInvalidBytecodePreUpload() throws Exception {
        AtomicBoolean uploadAttempted = new AtomicBoolean(false);

        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            public <T> CompletableFuture<T> postBinary(String path, byte[] payload, String contentType, Class<T> responseType) {
                uploadAttempted.set(true);
                return CompletableFuture.failedFuture(new IllegalStateException("Should not be invoked for invalid bytecode!"));
            }
        };

        BytecodeService service = new BytecodeService(mockClient);
        FunctionsViewModel vm = new FunctionsViewModel(service);

        // 1. Inspect an invalid file
        Path invalidFile = tempDir.resolve("bad.slxb");
        Files.writeString(invalidFile, "Plain text fake bytecode");

        vm.inspectLocalFile(invalidFile);
        assertFalse(vm.isValidMagicHeader(), "ViewModel must report invalid magic header");
        assertTrue(vm.statusMessageProperty().get().contains("not valid compiled Solix bytecode"));

        // 2. Attempt upload
        CompletableFuture<Boolean> uploadResult = vm.executeUpload();
        Boolean success = uploadResult.get(5, TimeUnit.SECONDS);

        assertFalse(success, "executeUpload must return false for invalid bytecode");
        assertFalse(uploadAttempted.get(), "RestClient must NEVER be called if magic validation fails");
        assertTrue(vm.statusMessageProperty().get().contains("not valid compiled Solix bytecode"));
    }

    @Test
    void testFunctionsViewModelAcceptsValidBytecodeAndPrependsAtIndexZero() throws Exception {
        AtomicBoolean uploadAttempted = new AtomicBoolean(false);

        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> postBinary(String path, byte[] payload, String contentType, Class<T> responseType) {
                uploadAttempted.set(true);
                RestDto.BytecodeUploadResponse resp = new RestDto.BytecodeUploadResponse("aabbccdd11223344", 8, "test-func");
                return CompletableFuture.completedFuture((T) resp);
            }
        };

        BytecodeService service = new BytecodeService(mockClient);
        FunctionsViewModel vm = new FunctionsViewModel(service);

        // Prepopulate an existing item in list
        com.legatus.studio.model.BytecodeItem oldItem = new com.legatus.studio.model.BytecodeItem(
                "old-sha", "old-func", "main", 100, "2026-10-15T00:00:00Z"
        );
        vm.getBytecodeItems().add(oldItem);

        // Valid bytecode file
        Path validFile = tempDir.resolve("valid_program.slxb");
        byte[] validContent = new byte[] { 0x53, 0x4F, 0x4C, 0x58, 0x10, 0x20, 0x30, 0x40 };
        Files.write(validFile, validContent);

        vm.inspectLocalFile(validFile);
        assertTrue(vm.isValidMagicHeader());

        CompletableFuture<Boolean> uploadResult = vm.executeUpload();
        Boolean success = uploadResult.get(5, TimeUnit.SECONDS);

        assertTrue(success);
        assertTrue(uploadAttempted.get());

        // Wait for JavaFX thread to execute UI update
        Thread.sleep(100);

        // Assert newly registered function is prepended at index 0 (MS-015 requirement)
        assertEquals(2, vm.getBytecodeItems().size());
        assertEquals("valid_program", vm.getBytecodeItems().get(0).getFunctionName());
        assertEquals(oldItem, vm.getBytecodeItems().get(1));
    }
}
