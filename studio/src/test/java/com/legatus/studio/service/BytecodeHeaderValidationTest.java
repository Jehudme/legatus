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

        // Genuine raw bytecode without SOLX prefix (e.g. sample_task.slxb starting with opcode 0x4B)
        byte[] rawBytecode = new byte[] { 0x4B, 0x00, 0x00, 0x00, 0x01, (byte) 0xFF };
        assertTrue(BytecodeService.hasSolixMagicHeader(rawBytecode), "Genuine compiled bytecode without SOLX prefix must be accepted");

        // Invalid / empty data
        assertFalse(BytecodeService.hasSolixMagicHeader((byte[]) null), "Null data must be rejected");
        assertFalse(BytecodeService.hasSolixMagicHeader(new byte[0]), "Empty data must be rejected");
    }

    @Test
    void testMagicHeaderFileValidation() throws IOException {
        Path missingFile = tempDir.resolve("missing_bytecode.slxb");
        assertFalse(BytecodeService.hasSolixMagicHeader(missingFile), "Missing file must be rejected");

        Path validFile = tempDir.resolve("valid.slxb");
        byte[] validContent = new byte[] { 0x4B, 0x00, 0x00, 0x00, (byte) 0xAA, (byte) 0xBB };
        Files.write(validFile, validContent);
        assertTrue(BytecodeService.hasSolixMagicHeader(validFile), "Genuine bytecode file must be accepted");
    }

    @Test
    void testFunctionsViewModelRejectsEmptyFilePathPreUpload() throws Exception {
        AtomicBoolean uploadAttempted = new AtomicBoolean(false);

        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            public <T> CompletableFuture<T> postBinary(String path, byte[] payload, String contentType, Class<T> responseType) {
                uploadAttempted.set(true);
                return CompletableFuture.failedFuture(new IllegalStateException("Should not be invoked for invalid path!"));
            }
        };

        BytecodeService service = new BytecodeService(mockClient);
        FunctionsViewModel vm = new FunctionsViewModel(service);

        // Attempt upload with empty file path
        vm.selectedFilePathProperty().set("");
        CompletableFuture<Boolean> uploadResult = vm.executeUpload();
        Boolean success = uploadResult.get(5, TimeUnit.SECONDS);

        assertFalse(success, "executeUpload must return false for empty file path");
        assertFalse(uploadAttempted.get(), "RestClient must NEVER be called if path is empty");
        assertTrue(vm.statusMessageProperty().get().contains("select a valid"));
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
