package com.legatus.studio.controllers;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.service.WorkerService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.QuarantineViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless UI automation test for the Quarantine Attestation workflow (MS-015 P1, P2).
 */
public class QuarantineAttestationWorkflowTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testQuarantineInlineAttestationApprovalWorkflow() throws Exception {
        var view = ViewLoaderService.load("quarantine_attestation");
        assertNotNull(view);
        QuarantineAttestationController controller = (QuarantineAttestationController) view.controller();
        assertNotNull(controller);

        AtomicReference<String> approvedEnrollmentId = new AtomicReference<>();
        AtomicReference<String> approvedDuration = new AtomicReference<>();
        AtomicReference<String> approvedFriendlyName = new AtomicReference<>();

        RestClient mockClient = new RestClient("http://localhost:9999") {
            @Override
            @SuppressWarnings("unchecked")
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
                if (body instanceof RestDto.ApproveEnrollmentRequest req) {
                    approvedDuration.set(req.lease_duration());
                    approvedFriendlyName.set(req.friendly_name());
                    // Path: /api/v1/admin/workers/enrollments/{encodedId}/approve
                    String[] parts = path.split("/");
                    if (parts.length >= 2) {
                        approvedEnrollmentId.set(parts[parts.length - 2]);
                    }
                    return CompletableFuture.completedFuture((T) null);
                }
                return CompletableFuture.completedFuture(null);
            }
        };

        WorkerService workerService = new WorkerService(mockClient);
        ObservableList<QuarantineEnrollment> enrollments = FXCollections.observableArrayList();

        QuarantineEnrollment nodeA = new QuarantineEnrollment(
                "enr-uuid-1", "wrk-raw-01", "node-alpha.dc1.internal", "192.168.1.100",
                "linux", "x86_64", "4E:8A:BC:31:09:FF:7D:12", "Tiger-Galaxy-77",
                8, 16L * 1024 * 1024 * 1024, "2026-10-15T12:00:00Z"
        );
        enrollments.add(nodeA);

        QuarantineViewModel viewModel = new QuarantineViewModel(enrollments, workerService);
        controller.setViewModel(viewModel);
        Thread.sleep(100);

        // 1. Select pending worker node in list
        controller.getEnrollmentsListView().getSelectionModel().select(nodeA);
        assertTrue(controller.getAttestationPane().isVisible(), "Attestation card must become visible on selection");

        // 2. Verify attestation card displays cryptographic and hardware telemetry
        assertEquals("wrk-raw-01", controller.getWorkerIdLabel().getText());
        assertEquals("node-alpha.dc1.internal", controller.getHostnameLabel().getText());
        assertEquals("4E:8A:BC:31:09:FF:7D:12", controller.getFingerprintLabel().getText());
        assertEquals("Tiger-Galaxy-77", controller.getVisualCodeLabel().getText());

        // 3. Operator customizes friendly name inline and custom lease duration
        controller.getFriendlyNameField().setText("Edge-Compute-01");
        controller.getLeaseDurationField().setText("48h");

        // 4. Click Approve & Issue Lease
        controller.onApproveClicked();
        Thread.sleep(150);

        // 5. Verify approval payload sent to server with custom inline parameters
        assertEquals("enr-uuid-1", approvedEnrollmentId.get(), "Approval must target selected enrollment ID");
        assertEquals("48h", approvedDuration.get(), "Custom lease duration must be submitted");
        assertEquals("Edge-Compute-01", approvedFriendlyName.get(), "Custom friendly name must be submitted");

        // 6. Verify worker node is removed from pending quarantine list
        assertTrue(enrollments.isEmpty(), "Approved worker must be removed from quarantine queue");
        assertFalse(controller.getAttestationPane().isVisible(), "Attestation pane must hide when queue is empty");
    }
}
