package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.BytecodeService;
import com.legatus.studio.service.ExecutionProfileService;
import com.legatus.studio.service.TaskService;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless UI automation test for the New Profile modal dialog workflow (MS-015 P0, P1, P2).
 */
public class NewProfileModalWorkflowTest {

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
    void testNewProfileModalWorkflowAndSchemaValidation() throws Exception {
        var view = ViewLoaderService.load("execute_workbench");
        assertNotNull(view);
        ExecuteWorkbenchController controller = (ExecuteWorkbenchController) view.controller();
        assertNotNull(controller);

        AtomicBoolean saveCalled = new AtomicBoolean(false);

        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
                saveCalled.set(true);
                if (body instanceof RestDto.ExecutionProfileDto dto) {
                    RestDto.ExecutionProfileDto saved = new RestDto.ExecutionProfileDto(
                            "saved-prof-uuid",
                            dto.profile_name(),
                            dto.function_id(),
                            dto.arguments_json(),
                            dto.instruction_budget(),
                            dto.unlimited_instructions(),
                            dto.time_limit_ms(),
                            dto.unlimited_time(),
                            dto.memory_mb(),
                            dto.priority(),
                            dto.notify_on_failure(),
                            1L
                    );
                    return CompletableFuture.completedFuture((T) saved);
                }
                return CompletableFuture.completedFuture(null);
            }
        };

        ExecutionProfileService profileService = new ExecutionProfileService(mockClient);
        BytecodeService bytecodeService = new BytecodeService(mockClient);
        TaskService taskService = new TaskService(mockClient);
        FunctionsViewModel functionsVm = new FunctionsViewModel(bytecodeService, taskService);

        UUID funcAId = UUID.randomUUID();
        BytecodeItem funcA = new BytecodeItem(funcAId, "sha-aaa", "order-service", "main", 500L, "2026-10-15T00:00:00Z");
        UUID funcBId = UUID.randomUUID();
        BytecodeItem funcB = new BytecodeItem(funcBId, "sha-bbb", "report-generator", "main", 600L, "2026-10-15T00:00:00Z");
        functionsVm.getBytecodeItems().addAll(funcA, funcB);

        com.legatus.studio.service.StudioContext ctx = com.legatus.studio.service.StudioContext.getInstance();
        ctx.initSession("http://localhost:9999", true);
        ctx.setExecutionProfileService(profileService);
        ctx.setFunctionsViewModel(functionsVm);

        controller.setContext(ctx, new MainViewModel());
        Thread.sleep(150);

        // 1. Initial State: modal overlay must be hidden and unmanaged
        assertNotNull(controller.getNewProfileModal());
        assertFalse(controller.getNewProfileModal().isVisible());
        assertFalse(controller.getNewProfileModal().isManaged());

        // 2. Open Modal via [+ New Profile]
        controller.onOpenNewProfileModal();
        assertTrue(controller.getNewProfileModal().isVisible());
        assertTrue(controller.getNewProfileModal().isManaged());

        // 3. Close Modal via Cancel
        controller.onCloseNewProfileModal();
        assertFalse(controller.getNewProfileModal().isVisible());

        // 4. Open Modal and test live JSON syntax validation
        controller.onOpenNewProfileModal();

        // Invalid JSON:
        controller.getCreateArgumentsArea().setText("{ invalid json broken ");
        assertTrue(controller.getCreateJsonValidationLabel().getText().contains("Invalid JSON"),
                "Validation label must report syntax error for broken JSON");

        // Valid JSON:
        controller.getCreateArgumentsArea().setText("{\n  \"action\": \"process\",\n  \"limit\": 25\n}");
        assertTrue(controller.getCreateJsonValidationLabel().getText().contains("Valid JSON"),
                "Validation label must confirm valid JSON syntax");

        // 5. Select Function B in dropdown and set profile properties
        controller.getCreateFunctionSelector().getSelectionModel().select(funcB);
        controller.getCreateProfileNameField().setText("End-Of-Month Report");
        controller.getCreateInstructionBudgetField().setText("25000000");

        // 6. Click Create Profile
        controller.onCreateProfileClicked();
        Thread.sleep(150);

        assertTrue(saveCalled.get(), "Profile save must be dispatched to server");
        assertFalse(controller.getNewProfileModal().isVisible(), "Modal must auto-dismiss after creation");

        // 7. Verify newly created profile is prepended at index 0 of table
        assertFalse(controller.getProfiles().isEmpty());
        ExecutionProfile topProfile = controller.getProfiles().get(0);
        assertEquals("End-Of-Month Report", topProfile.getProfileName());
        assertEquals("saved-prof-uuid", topProfile.getProfileId());
        assertEquals(funcBId, topProfile.getFunctionId());
        assertEquals("report-generator", topProfile.getFunctionName());
        assertEquals(25_000_000L, topProfile.getInstructionBudget());

        // 8. Test Target Function Auto-Resolution when selecting profile (MS-015 P0)
        controller.inspectProfile(topProfile);
        assertEquals(funcB, controller.getInspectFunctionSelector().getSelectionModel().getSelectedItem(),
                "Target function selector must auto-select function matching profile.getFunctionId()");

        // 9. Test Profile JSON Import with Schema Validation (MS-015 P2)
        // Corrupted schema (missing required profile_name)
        File invalidSchemaFile = tempDir.resolve("corrupted_profile.json").toFile();
        Files.writeString(invalidSchemaFile.toPath(), "{\"unknown_field\": 123, \"budget\": 1000}");
        controller.importProfileFromFile(invalidSchemaFile);

        assertTrue(controller.getCreateStatusLabel().getText().contains("Import failed"),
                "Importing invalid schema must display descriptive error");

        // Valid schema import
        File validSchemaFile = tempDir.resolve("valid_import.json").toFile();
        String validJson = """
                {
                  "profile_name": "Imported Micro-Benchmark",
                  "function_id": "%s",
                  "arguments_json": "{\\"warmup\\": true}",
                  "instruction_budget": 500000,
                  "time_limit_ms": 3000,
                  "memory_mb": 32,
                  "priority": "HIGH",
                  "notify_on_failure": true
                }
                """.formatted(funcAId.toString());
        Files.writeString(validSchemaFile.toPath(), validJson);

        controller.importProfileFromFile(validSchemaFile);
        Thread.sleep(150);

        // Verify imported profile prepended at index 0
        ExecutionProfile importedTop = controller.getProfiles().get(0);
        assertEquals("Imported Micro-Benchmark", importedTop.getProfileName());
        assertEquals(funcAId, importedTop.getFunctionId());
        assertEquals("order-service", importedTop.getFunctionName());
        assertEquals(500_000L, importedTop.getInstructionBudget());
    }
}
