package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.BytecodeService;
import com.legatus.studio.service.TaskService;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Execute Workbench allows repeated task dispatches without
 * locking the operator out or requiring view navigation reloads (MS-015 P0).
 */
public class ExecuteWorkbenchRepeatTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testConsecutiveTaskExecutionsWithoutLockout() throws Exception {
        var view = ViewLoaderService.load("execute_workbench");
        assertNotNull(view);
        ExecuteWorkbenchController controller = (ExecuteWorkbenchController) view.controller();
        assertNotNull(controller);

        AtomicInteger dispatchCounter = new AtomicInteger(0);

        // Mock RestClient returning unique task IDs
        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
                int id = dispatchCounter.incrementAndGet();
                RestDto.CreateTaskResponse resp = new RestDto.CreateTaskResponse(
                        "task-uuid-" + id,
                        "QUEUED",
                        System.currentTimeMillis()
                );
                return CompletableFuture.completedFuture((T) resp);
            }
        };

        TaskService mockTaskService = new TaskService(mockClient);
        BytecodeService mockBytecodeService = new BytecodeService(mockClient);
        FunctionsViewModel functionsVm = new FunctionsViewModel(mockBytecodeService, mockTaskService);

        UUID functionId = UUID.randomUUID();
        BytecodeItem testFunction = new BytecodeItem(
                functionId,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "payment-processor",
                "main",
                1024L,
                "2026-10-15T12:00:00Z",
                "Processes transactions",
                "prod,payments",
                12L,
                null
        );
        functionsVm.getBytecodeItems().add(testFunction);

        // Setup mock StudioContext
        com.legatus.studio.service.StudioContext ctx = com.legatus.studio.service.StudioContext.getInstance();
        ctx.initSession("http://localhost:9999", true);
        ctx.setTaskService(mockTaskService);
        ctx.setFunctionsViewModel(functionsVm);

        // Configure controller
        controller.setContext(ctx, new MainViewModel());

        // Wait for JavaFX thread to populate default profiles
        Thread.sleep(150);

        // Add a test execution profile bound to the function
        ExecutionProfile profile = new ExecutionProfile("prof-1", "High-Volume Run", functionId);
        profile.setFunctionName("payment-processor");
        profile.setInstructionBudget(5_000_000L);
        profile.setArgumentsJson("{\"batch\": 100}");
        controller.getProfiles().add(0, profile);

        // Select the profile
        controller.getProfilesTable().getSelectionModel().select(profile);
        controller.inspectProfile(profile);

        // Ensure inspectFunctionSelector is bound and has the function selected
        controller.getInspectFunctionSelector().getItems().setAll(testFunction);
        controller.getInspectFunctionSelector().getSelectionModel().select(testFunction);

        // Dispatches 5 consecutive times without reloading view
        for (int i = 1; i <= 5; i++) {
            assertFalse(controller.getExecuteButton().isDisable(), "Execute button must be enabled before dispatch #" + i);

            controller.onExecuteClicked();

            // Wait for FxAsync runOnFxThread completion
            Thread.sleep(100);

            // 1. Verify dispatch count incremented
            assertEquals(i, dispatchCounter.get(), "Task submission #" + i + " must have been executed");

            // 2. Verify active task ID updated
            assertEquals("task-uuid-" + i, controller.getActiveTaskIdLabel().getText(), "Active task ID label must update for dispatch #" + i);

            // 3. Verify status badge updated
            assertEquals("QUEUED", controller.getTaskStatusBadge().getText(), "Status badge must reflect QUEUED for dispatch #" + i);

            // 4. CRITICAL: Verify execute button is re-enabled immediately (MS-015 requirement)
            assertFalse(controller.getExecuteButton().isDisable(), "Execute button must be immediately re-enabled after dispatch #" + i);
        }
    }
}
