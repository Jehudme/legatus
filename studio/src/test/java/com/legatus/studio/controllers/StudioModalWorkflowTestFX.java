package com.legatus.studio.controllers;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.BytecodeService;
import com.legatus.studio.service.ExecutionProfileService;
import com.legatus.studio.service.TaskService;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.service.WorkerService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.ExecutionProfileViewModel;
import com.legatus.studio.viewmodel.FleetViewModel;
import com.legatus.studio.viewmodel.FunctionsViewModel;
import com.legatus.studio.viewmodel.MainViewModel;
import com.legatus.studio.viewmodel.QuarantineViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Military-Grade TestFX UI Automated Verification Suite (MS-016 Defect Scenarios 5, 8, 9).
 * Tests all interactive modal workflows, dynamic inspector bindings, repeated execution,
 * and reactive top-of-list prepending.
 */
public class StudioModalWorkflowTestFX {

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
    void testEndToEndStudioModalWorkflowsAndCockpitUX() throws Exception {
        // --- 1. Functions Workbench & New Function Modal ---
        var funcView = ViewLoaderService.load("functions_workbench");
        assertNotNull(funcView);
        FunctionsWorkbenchController funcCtrl = (FunctionsWorkbenchController) funcView.controller();
        assertNotNull(funcCtrl);

        AtomicBoolean bytecodeUploaded = new AtomicBoolean(false);
        RestClient mockRest = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> postBinary(String path, byte[] payload, String contentType, Class<T> responseType) {
                bytecodeUploaded.set(true);
                return CompletableFuture.completedFuture((T) new RestDto.BytecodeUploadResponse("sha256-verified-solx", 64, "billing-calc"));
            }
        };

        BytecodeService bytecodeService = new BytecodeService(mockRest);
        FunctionsViewModel functionsVm = new FunctionsViewModel(bytecodeService);
        funcCtrl.setViewModel(functionsVm);

        // Assert modal initial state
        assertFalse(funcCtrl.getNewFunctionModal().isVisible());
        funcCtrl.onOpenNewFunctionModal();
        assertTrue(funcCtrl.getNewFunctionModal().isVisible());

        // Select valid Solix bytecode with magic header 0x534F4C58
        File validBytecode = tempDir.resolve("billing_calc.slxb").toFile();
        Files.write(validBytecode.toPath(), new byte[] { 0x53, 0x4F, 0x4C, 0x58, 0x01, 0x02, 0x03, 0x04 });
        funcCtrl.inspectAndValidateFile(validBytecode);
        assertTrue(funcCtrl.getMagicValidationLabel().getText().contains("Valid Solix Bytecode"));
        assertFalse(funcCtrl.getUploadButton().isDisable());

        funcCtrl.onUploadClicked();
        Thread.sleep(150);

        assertTrue(bytecodeUploaded.get());
        assertFalse(funcCtrl.getNewFunctionModal().isVisible(), "Modal must dismiss on upload");
        assertEquals("billing_calc", functionsVm.getBytecodeItems().get(0).getFunctionName());

        // --- 2. Execute Workbench & Repeated Dispatch ---
        var execView = ViewLoaderService.load("execute_workbench");
        assertNotNull(execView);
        ExecuteWorkbenchController execCtrl = (ExecuteWorkbenchController) execView.controller();
        assertNotNull(execCtrl);

        AtomicInteger dispatchCount = new AtomicInteger(0);
        RestClient execRest = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
                if (body instanceof RestDto.ExecutionProfileDto dto) {
                    return CompletableFuture.completedFuture((T) new RestDto.ExecutionProfileDto(
                            "prof-billing-id", dto.profile_name(), dto.function_id(), dto.arguments_json(),
                            dto.instruction_budget(), false, dto.time_limit_ms(), false, 64L, "NORMAL", false, 1L
                    ));
                }
                int count = dispatchCount.incrementAndGet();
                return CompletableFuture.completedFuture((T) new RestDto.CreateTaskResponse("task-run-" + count, "QUEUED", System.currentTimeMillis()));
            }
        };

        ExecutionProfileService profileService = new ExecutionProfileService(execRest);
        com.legatus.studio.service.StudioContext ctx = com.legatus.studio.service.StudioContext.getInstance();
        ctx.initSession("http://localhost:9999", true);
        ctx.setTaskService(new TaskService(execRest));
        ctx.setExecutionProfileService(profileService);
        ctx.setFunctionsViewModel(functionsVm);

        execCtrl.setContext(ctx, new MainViewModel());
        Thread.sleep(150);

        // Open New Profile modal
        execCtrl.onOpenNewProfileModal();
        assertTrue(execCtrl.getNewProfileModal().isVisible());

        execCtrl.getCreateProfileNameField().setText("Daily Billing Run");
        execCtrl.getCreateArgumentsArea().setText("{\"invoice_period\": \"2026-10\"}");
        execCtrl.onCreateProfileClicked();
        Thread.sleep(150);

        assertFalse(execCtrl.getNewProfileModal().isVisible(), "New Profile modal must auto-dismiss");
        assertEquals("Daily Billing Run", execCtrl.getProfiles().get(0).getProfileName());

        // Repeated execution: dispatch 3 times consecutively without view reloads (DEF-08 verification)
        BytecodeItem billingFunc = functionsVm.getBytecodeItems().get(0);
        execCtrl.getInspectFunctionSelector().getItems().setAll(billingFunc);
        execCtrl.getInspectFunctionSelector().getSelectionModel().select(billingFunc);

        for (int i = 1; i <= 3; i++) {
            assertFalse(execCtrl.getExecuteButton().isDisable());
            execCtrl.onExecuteClicked();
            Thread.sleep(100);
            assertEquals(i, dispatchCount.get());
            assertEquals("task-run-" + i, execCtrl.getActiveTaskIdLabel().getText());
            assertFalse(execCtrl.getExecuteButton().isDisable(), "Execute button must re-enable immediately");
        }

        // --- 3. Quarantine Attestation Workflow ---
        var quarView = ViewLoaderService.load("quarantine_attestation");
        assertNotNull(quarView);
        QuarantineAttestationController quarCtrl = (QuarantineAttestationController) quarView.controller();
        assertNotNull(quarCtrl);

        ObservableList<QuarantineEnrollment> enrollments = FXCollections.observableArrayList();
        QuarantineEnrollment node = new QuarantineEnrollment(
                "enr-auto-1", "wrk-q-1", "worker-node-1.internal", "192.168.1.10",
                "linux", "x86_64", "AA:BB:CC:DD:EE:FF:00:11", "Eagle-Solar-42",
                16, 32L * 1024 * 1024 * 1024, "2026-10-15T12:00:00Z"
        );
        enrollments.add(node);

        AtomicBoolean approved = new AtomicBoolean(false);
        WorkerService workerService = new WorkerService(new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
                approved.set(true);
                return CompletableFuture.completedFuture((T) null);
            }
        });

        QuarantineViewModel quarVm = new QuarantineViewModel(enrollments, workerService);
        quarCtrl.setViewModel(quarVm);
        Thread.sleep(100);

        quarCtrl.getEnrollmentsListView().getSelectionModel().select(node);
        assertTrue(quarCtrl.getAttestationPane().isVisible());
        assertEquals("Eagle-Solar-42", quarCtrl.getVisualCodeLabel().getText());

        quarCtrl.getFriendlyNameField().setText("Production-Worker-01");
        quarCtrl.onApproveClicked();
        Thread.sleep(150);

        assertTrue(approved.get(), "Worker approval must be submitted");
        assertTrue(enrollments.isEmpty(), "Worker must be removed from quarantine queue");
    }
}
