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

        // 4. Re-open Modal and test invalid bytecode rejection
        controller.onOpenNewFunctionModal();

        File invalidFile = tempDir.resolve("fake.slxb").toFile();
        Files.writeString(invalidFile.toPath(), "This is not solix bytecode!");
        controller.inspectAndValidateFile(invalidFile);

        assertTrue(controller.getMagicValidationLabel().getText().contains("not valid compiled Solix bytecode"),
                "Inline validation label must warn about invalid bytecode");
        assertTrue(controller.getUploadButton().isDisable(), "Upload button must be disabled for invalid bytecode");

        // Attempting upload must be blocked
        controller.onUploadClicked();
        assertFalse(uploadCalled.get(), "Upload must NOT be invoked when file has invalid magic header");
        assertTrue(controller.getNewFunctionModal().isVisible(), "Modal must remain open after blocked upload");

        // 5. Inspect valid Solix bytecode file (with magic header 0x534F4C58)
        File validFile = tempDir.resolve("auth_validator.slxb").toFile();
        byte[] validBytes = new byte[] { 0x53, 0x4F, 0x4C, 0x58, 0x01, 0x00, 0x00, 0x05 };
        Files.write(validFile.toPath(), validBytes);

        controller.inspectAndValidateFile(validFile);
        assertTrue(controller.getMagicValidationLabel().getText().contains("Valid Solix Bytecode Header"),
                "Inline validation label must confirm valid SOLX magic header");
        assertFalse(controller.getUploadButton().isDisable(), "Upload button must be enabled for valid bytecode");

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
