package com.legatus.studio.controllers;

import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.service.WorkerService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.FleetViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FleetTopologyBindingTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testInspectorCardDynamicPropertyBindings() throws IOException {
        var view = ViewLoaderService.load("fleet_topology");
        assertNotNull(view);
        FleetTopologyController controller = (FleetTopologyController) view.controller();
        assertNotNull(controller);

        ObservableList<WorkerNode> workers = FXCollections.observableArrayList();
        WorkerService dummyService = new WorkerService(new RestClient("http://localhost:9999"));
        FleetViewModel viewModel = new FleetViewModel(workers, dummyService);

        controller.setViewModel(viewModel);

        WorkerNode worker = new WorkerNode(
                "worker-alpha", "Alpha Node", "alpha.local", "10.0.0.1", "linux", "x86_64",
                "ONLINE", true, 8, 16L * 1024 * 1024 * 1024,
                75.0, 80.0, 16, true,
                3600L, 1000L, 12.5, 4L * 1024 * 1024 * 1024, 2, "2026-10-15T12:00:00Z", "2026-10-15T13:00:00Z"
        );

        workers.add(worker);
        controller.getWorkersTable().getSelectionModel().select(worker);

        // Verify initial bindings
        assertEquals("12.5%", controller.getSelectedCpuLabel().getText());
        assertEquals("4.0 / 16.0 GB", controller.getSelectedRamLabel().getText());
        assertEquals("1h 0m", controller.getSelectedLeaseLabel().getText());
        assertEquals("2", controller.getSelectedTasksLabel().getText());
        assertEquals("ONLINE", controller.getSelectedStateLabel().getText());

        // Now mutate properties directly (simulating real-time telemetry updates arriving over LSTCP)
        worker.setCpuLoadPct(68.4);
        worker.setMemoryUsedBytes(8L * 1024 * 1024 * 1024);
        worker.setRemainingLeaseSec(150L);
        worker.setActiveTasksCount(5);
        worker.setState("DRAINING");

        // Assert that inspector labels updated dynamically WITHOUT re-clicking or re-selecting the table row
        assertEquals("68.4%", controller.getSelectedCpuLabel().getText(),
                "selectedCpuLabel must update dynamically when worker cpuLoadPct changes");
        assertEquals("8.0 / 16.0 GB", controller.getSelectedRamLabel().getText(),
                "selectedRamLabel must update dynamically when worker memoryUsedBytes changes");
        assertEquals("2m 30s", controller.getSelectedLeaseLabel().getText(),
                "selectedLeaseLabel must update dynamically when worker remainingLeaseSec changes");
        assertEquals("5", controller.getSelectedTasksLabel().getText(),
                "selectedTasksLabel must update dynamically when worker activeTasksCount changes");
        assertEquals("DRAINING", controller.getSelectedStateLabel().getText(),
                "selectedStateLabel must update dynamically when worker state changes");

        // Clear selection to verify clean unbinding
        controller.getWorkersTable().getSelectionModel().clearSelection();
        assertNull(viewModel.getSelectedWorker());
    }
}
