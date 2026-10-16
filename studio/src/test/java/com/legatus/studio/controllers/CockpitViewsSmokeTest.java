package com.legatus.studio.controllers;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.service.ViewLoaderService;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class CockpitViewsSmokeTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "login",
        "title_bar",
        "top_ribbon",
        "navigation_rail",
        "drawer",
        "shell",
        "cluster_overview",
        "fleet_topology",
        "quarantine_attestation",
        "functions_workbench",
        "execute_workbench",
        "task_explorer",
        "secrets_vault",
        "admin_users",
        "audit_log",
        "settings_dialog",
        "worker_card_cell",
        "task_row_cell",
        "quarantine_card_cell"
    })
    void testLoadAllFxmlViews(String viewName) throws IOException {
        var view = ViewLoaderService.load(viewName);
        assertNotNull(view, "ViewResult should not be null for: " + viewName);
        assertNotNull(view.root(), "Root node should be loaded for: " + viewName);
        assertNotNull(view.controller(), "Controller should be bound for: " + viewName);
    }

    @Test
    void testWorkerCardCellBinding() throws IOException {
        var view = ViewLoaderService.load("worker_card_cell");
        WorkerCardCellController controller = (WorkerCardCellController) view.controller();
        assertNotNull(controller);

        WorkerNode node = new WorkerNode(
                "wrk-test-01",
                "worker-alpha.internal",
                "10.0.0.12",
                "linux",
                "x86_64",
                "ONLINE",
                16,
                64L * 1024 * 1024 * 1024,
                38.5,
                16L * 1024 * 1024 * 1024,
                4,
                "2026-10-15T12:00:00Z"
        );
        node.recordCpuPoint(10.0);
        node.recordCpuPoint(20.0);
        node.recordCpuPoint(38.5);

        controller.updateWorker(node);
        assertEquals("wrk-test-01", controller.getWorkerIdLabel().getText());
        assertEquals("worker-alpha.internal", controller.getHostnameLabel().getText());
        assertEquals("ONLINE", controller.getStateBadge().getText());
        assertEquals("38.5%", controller.getCpuLabel().getText());
        assertFalse(controller.getSparklinePolyline().getPoints().isEmpty());
    }

    @Test
    void testTaskRowCellBinding() throws IOException {
        var view = ViewLoaderService.load("task_row_cell");
        TaskRowCellController controller = (TaskRowCellController) view.controller();
        assertNotNull(controller);

        TaskRecord task = new TaskRecord(
                "task-uuid-8899",
                "image-resize",
                "EXECUTING",
                0,
                "wrk-test-01",
                5_000_000L,
                32L * 1024 * 1024,
                450L,
                "2026-10-15T12:00:00Z",
                null
        );
        task.setInstructionBudget(10_000_000L);

        controller.updateTask(task);
        assertEquals("task-uuid-8899", controller.getTaskIdLabel().getText());
        assertEquals("image-resize", controller.getNameLabel().getText());
        assertEquals("EXECUTING", controller.getStatusBadge().getText());
        assertEquals(0.5, controller.getProgressBar().getProgress(), 0.01);
        assertEquals("50.0%", controller.getProgressLabel().getText());
    }

    @Test
    void testQuarantineCardCellBinding() throws IOException {
        var view = ViewLoaderService.load("quarantine_card_cell");
        QuarantineCardCellController controller = (QuarantineCardCellController) view.controller();
        assertNotNull(controller);

        QuarantineEnrollment enrollment = new QuarantineEnrollment(
                "enr-01",
                "wrk-quar-77",
                "untrusted-host.local",
                "192.168.1.99",
                "linux",
                "aarch64",
                "4E:8A:BC:31:09:FF:7D:12:00:11:22:33:44:55:66:77",
                "Tiger-Galaxy-77",
                8,
                16L * 1024 * 1024 * 1024,
                "2026-10-15T12:00:00Z"
        );

        controller.updateEnrollment(enrollment);
        assertEquals("wrk-quar-77", controller.getWorkerIdLabel().getText());
        assertEquals("untrusted-host.local", controller.getHostnameLabel().getText());
        assertEquals("Tiger-Galaxy-77", controller.getVisualCodeLabel().getText());
    }

    @Test
    void testMainCockpitSwitching() throws IOException {
        var shellView = ViewLoaderService.load("shell");
        MainCockpitController shellController = (MainCockpitController) shellView.controller();
        assertNotNull(shellController);

        StackPane content = shellController.getContentContainer();
        assertNotNull(content);

        ViewLoaderService.switchView(content, "cluster_overview");
        assertEquals(1, content.getChildren().size());

        ViewLoaderService.switchView(content, "fleet_topology");
        assertEquals(1, content.getChildren().size());
    }

    @Test
    void testFleetTopologyInspectorMasterDetail() throws IOException {
        var view = ViewLoaderService.load("fleet_topology");
        FleetTopologyController controller = (FleetTopologyController) view.controller();
        assertNotNull(controller);

        assertNotNull(controller.getWorkersTable());
        assertNotNull(controller.getInspectorPane());

        javafx.collections.ObservableList<WorkerNode> workersList = javafx.collections.FXCollections.observableArrayList();
        WorkerNode testWorker = new WorkerNode(
                "wrk-insp-1", "host-insp-1", "192.168.1.50", "linux", "x86_64",
                "ONLINE", 8, 16L * 1024 * 1024 * 1024, 25.0, 4L * 1024 * 1024 * 1024, 2, "2026-10-15T12:00:00Z"
        );
        workersList.add(testWorker);
        var workerService = new com.legatus.studio.service.WorkerService(new com.legatus.studio.transport.RestClient("http://127.0.0.1:9999"));
        var fleetVm = new com.legatus.studio.viewmodel.FleetViewModel(workersList, workerService);
        controller.setViewModel(fleetVm);

        // Initially no row is selected -> inspector hidden
        controller.getWorkersTable().getSelectionModel().clearSelection();
        assertFalse(controller.getInspectorPane().isVisible());

        // Select worker
        controller.getWorkersTable().getSelectionModel().select(0);
        assertTrue(controller.getInspectorPane().isVisible());
        assertTrue(controller.getInspectorPane().isManaged());

        // Deselect -> hidden again
        controller.getWorkersTable().getSelectionModel().clearSelection();
        assertFalse(controller.getInspectorPane().isVisible());
        assertFalse(controller.getInspectorPane().isManaged());
    }

    @Test
    void testQuarantineAttestationMasterDetail() throws IOException {
        var view = ViewLoaderService.load("quarantine_attestation");
        QuarantineAttestationController controller = (QuarantineAttestationController) view.controller();
        assertNotNull(controller);

        assertNotNull(controller.getEnrollmentsListView());
        assertNotNull(controller.getAttestationPane());

        javafx.collections.ObservableList<QuarantineEnrollment> enrollmentsList = javafx.collections.FXCollections.observableArrayList();
        QuarantineEnrollment enrollment = new QuarantineEnrollment(
                "enr-insp-1", "wrk-q-01", "untrusted.host", "192.168.1.100",
                "linux", "x86_64", "4E:8A:BC:31:09:FF:7D:12", "Tiger-Galaxy-77", 8, 16L * 1024 * 1024 * 1024, "2026-10-15T12:00:00Z"
        );
        enrollmentsList.add(enrollment);
        var workerService = new com.legatus.studio.service.WorkerService(new com.legatus.studio.transport.RestClient("http://127.0.0.1:9999"));
        var quarVm = new com.legatus.studio.viewmodel.QuarantineViewModel(enrollmentsList, workerService);
        controller.setViewModel(quarVm);

        // Initially no row selected -> attestation hidden
        controller.getEnrollmentsListView().getSelectionModel().clearSelection();
        assertFalse(controller.getAttestationPane().isVisible());

        // Select enrollment
        controller.getEnrollmentsListView().getSelectionModel().select(0);
        assertTrue(controller.getAttestationPane().isVisible());
        assertTrue(controller.getAttestationPane().isManaged());

        // Deselect -> hidden again
        controller.getEnrollmentsListView().getSelectionModel().clearSelection();
        assertFalse(controller.getAttestationPane().isVisible());
        assertFalse(controller.getAttestationPane().isManaged());
    }

    @Test
    void testClusterOverviewAlertBanners() throws IOException {
        var view = ViewLoaderService.load("cluster_overview");
        ClusterOverviewController controller = (ClusterOverviewController) view.controller();
        assertNotNull(controller);

        var metrics = new com.legatus.studio.model.ClusterMetrics();
        var vm = new com.legatus.studio.viewmodel.ClusterOverviewViewModel(
                metrics,
                javafx.collections.FXCollections.observableArrayList(),
                javafx.collections.FXCollections.observableArrayList()
        );
        controller.setViewModel(vm);

        assertNotNull(controller.getThresholdBreachBox());
        assertNotNull(controller.getQueueSaturatedBox());

        vm.thresholdBreachWarningProperty().set(false);
        vm.queueSaturatedErrorProperty().set(false);
        assertFalse(controller.getThresholdBreachBox().isVisible());
        assertFalse(controller.getQueueSaturatedBox().isVisible());

        // Trigger warnings
        vm.thresholdBreachWarningProperty().set(true);
        assertTrue(controller.getThresholdBreachBox().isVisible());

        vm.queueSaturatedErrorProperty().set(true);
        assertTrue(controller.getQueueSaturatedBox().isVisible());

        // Clear warnings
        vm.thresholdBreachWarningProperty().set(false);
        assertFalse(controller.getThresholdBreachBox().isVisible());
    }
}
