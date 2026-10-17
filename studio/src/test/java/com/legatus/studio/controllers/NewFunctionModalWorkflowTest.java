package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.BytecodeService;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless UI automation test for the New Function modal dialog workflow (MS-015 P0, P1, P2).
 */
public class NewFunctionModalWorkflowTest {

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
    void testNewFunctionModalWorkflow() throws Exception {
        var view = ViewLoaderService.load("functions_workbench");
        assertNotNull(view);
        FunctionsWorkbenchController controller = (FunctionsWorkbenchController) view.controller();
        assertNotNull(controller);

        AtomicBoolean uploadCalled = new AtomicBoolean(false);
        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> postBinary(String path, byte[] payload, String contentType, Class<T> responseType) {
                uploadCalled.set(true);
                RestDto.BytecodeUploadResponse resp = new RestDto.BytecodeUploadResponse("1122334455667788", 128, "auth-validator");
                return CompletableFuture.completedFuture((T) resp);
            }
        };

        BytecodeService service = new BytecodeService(mockClient);
        FunctionsViewModel viewModel = new FunctionsViewModel(service);

        // Prepopulate an existing function
        BytecodeItem existing = new BytecodeItem("00000000", "existing-func", "main", 50, "2026-10-15T00:00:00Z");
        viewModel.getBytecodeItems().add(existing);

        controller.setViewModel(viewModel);

        // 1. Initial State: modal overlay must be hidden and unmanaged
        assertNotNull(controller.getNewFunctionModal());
        assertFalse(controller.getNewFunctionModal().isVisible(), "Modal must be hidden initially");
        assertFalse(controller.getNewFunctionModal().isManaged(), "Modal must be unmanaged initially");

        // 2. Open Modal via [+ New Function]
        controller.onOpenNewFunctionModal();
        assertTrue(controller.getNewFunctionModal().isVisible(), "Modal must be visible after open");
        assertTrue(controller.getNewFunctionModal().isManaged(), "Modal must be managed after open");

        // 3. Close Modal via Cancel
        controller.onCloseNewFunctionModal();
        assertFalse(controller.getNewFunctionModal().isVisible(), "Modal must be hidden after cancel");

        // 4. Re-open Modal and inspect real Solix bytecode file
        controller.onOpenNewFunctionModal();

        File validFile = tempDir.resolve("auth_validator.slxb").toFile();
        byte[] validBytes = new byte[] { 0x4B, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x05 };
        Files.write(validFile.toPath(), validBytes);

        controller.inspectAndValidateFile(validFile);
        assertTrue(controller.getMagicValidationLabel().getText().contains("Valid Solix Bytecode"),
                "Inline validation label must confirm valid Solix bytecode");
        assertFalse(controller.getUploadButton().isDisable(), "Upload button must be enabled for bytecode file");

        controller.getFunctionNameField().setText("auth-validator");
        controller.getEntrypointField().setText("authenticate");

        // 6. Submit valid function upload
        controller.onUploadClicked();

        // Allow async JavaFX dispatch to settle
        Thread.sleep(150);

        assertTrue(uploadCalled.get(), "Bytecode upload must be dispatched to server");
        assertFalse(controller.getNewFunctionModal().isVisible(), "Modal must auto-dismiss on successful upload");

        // 7. Verify newly registered function is prepended at index 0 of table (MS-015 requirement)
        assertEquals(2, viewModel.getBytecodeItems().size());
        assertEquals("auth-validator", viewModel.getBytecodeItems().get(0).getFunctionName());
        assertEquals("authenticate", viewModel.getBytecodeItems().get(0).getEntryPoint());
        assertEquals("existing-func", viewModel.getBytecodeItems().get(1).getFunctionName());
    }
}
