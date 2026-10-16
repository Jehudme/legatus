package com.legatus.studio.viewmodel;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.service.WorkerService;
import com.legatus.studio.transport.RestClient;
import com.sun.net.httpserver.HttpServer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class FleetAndQuarantineViewModelTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/v1/admin/workers/w1/drain", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/workers/w1/revoke", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/workers/w1/name", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/workers/w1/thresholds", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/workers/w1/toggle-active", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/workers/w1/lease/auto-renew", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/enrollment/auto-accept", ex -> {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] resp = "{\"auto_accept\":true}".getBytes();
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, resp.length);
                ex.getResponseBody().write(resp);
                ex.getResponseBody().close();
            } else {
                ex.sendResponseHeaders(200, -1);
            }
        });
        server.createContext("/api/v1/admin/workers/enrollments/enr1/approve", ex -> {
            ex.sendResponseHeaders(200, -1);
        });
        server.createContext("/api/v1/admin/workers/enrollments/enr1/reject", ex -> {
            ex.sendResponseHeaders(200, -1);
        });

        server.setExecutor(null);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void testFleetViewModelFilteringAndCommands() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        WorkerService workerService = new WorkerService(client);

        ObservableList<WorkerNode> allWorkers = FXCollections.observableArrayList();
        WorkerNode w1 = new WorkerNode("w1", "node-alpha", "10.0.0.1", "linux", "x86_64", "ONLINE", 8, 16000000000L, 20.0, 4000000000L, 1, "");
        WorkerNode w2 = new WorkerNode("w2", "node-beta", "10.0.0.2", "linux", "x86_64", "DRAINING", 8, 16000000000L, 5.0, 1000000000L, 0, "");
        allWorkers.addAll(w1, w2);

        FleetViewModel vm = new FleetViewModel(allWorkers, workerService);
        assertEquals(2, vm.getFilteredWorkers().size());

        // Search filter
        vm.setFilterQuery("alpha");
        assertEquals(1, vm.getFilteredWorkers().size());
        assertEquals("w1", vm.getFilteredWorkers().get(0).getWorkerId());

        vm.setFilterQuery("");
        // State filter
        vm.setStateFilter("DRAINING");
        assertEquals(1, vm.getFilteredWorkers().size());
        assertEquals("w2", vm.getFilteredWorkers().get(0).getWorkerId());

        vm.setStateFilter("ALL");
        vm.setSelectedWorker(w1);
        vm.drainSelectedWorker().get();
        assertEquals("DRAINING", w1.getState());

        server.createContext("/api/v1/admin/workers/w3", ex -> {
            ex.sendResponseHeaders(200, -1);
        });

        vm.revokeSelectedWorker().get();
        assertEquals("REVOKED", w1.getState());

        // Test delete dead/offline worker
        WorkerNode deadWorker = new WorkerNode("w3", "node-dead", "10.0.0.3", "linux", "x86_64", "OFFLINE", 8, 16000000000L, 0.0, 0L, 0, "");
        allWorkers.add(deadWorker);
        vm.setSelectedWorker(deadWorker);
        vm.deleteSelectedWorker().get();
        assertFalse(allWorkers.contains(deadWorker));

        // Test sorting priority: ONLINE workers on top
        WorkerNode offline = new WorkerNode("w-off", "offline-node", "10.0.0.4", "linux", "x86_64", "OFFLINE", 4, 8000000000L, 0.0, 0L, 0, "");
        WorkerNode online = new WorkerNode("w-on", "online-node", "10.0.0.5", "linux", "x86_64", "ONLINE", 4, 8000000000L, 10.0, 2000000000L, 1, "");
        allWorkers.clear();
        allWorkers.addAll(offline, online);
        assertEquals("w-on", vm.getFilteredWorkers().get(0).getWorkerId());
        assertEquals("w-off", vm.getFilteredWorkers().get(1).getWorkerId());
    }

    @Test
    void testFleetViewModelControlPlaneActions() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        WorkerService workerService = new WorkerService(client);

        ObservableList<WorkerNode> allWorkers = FXCollections.observableArrayList();
        WorkerNode w1 = new WorkerNode("w1", "node-alpha", "10.0.0.1", "linux", "x86_64", "ONLINE", 8, 16000000000L, 20.0, 4000000000L, 1, "");
        allWorkers.add(w1);

        FleetViewModel vm = new FleetViewModel(allWorkers, workerService);
        vm.setSelectedWorker(w1);

        // Rename worker
        vm.renameSelectedWorker("inference-gpu-01").get();
        assertEquals("inference-gpu-01", w1.getFriendlyName());
        assertEquals("inference-gpu-01", w1.getDisplayName());

        // Search filter matches friendly name
        vm.setFilterQuery("inference");
        assertEquals(1, vm.getFilteredWorkers().size());
        vm.setFilterQuery("non-existent");
        assertEquals(0, vm.getFilteredWorkers().size());
        vm.setFilterQuery("");

        // Toggle Active
        assertTrue(w1.isActive());
        vm.toggleActiveSelectedWorker().get();
        assertFalse(w1.isActive());
        vm.toggleActiveSelectedWorker().get();
        assertTrue(w1.isActive());

        // Toggle Auto Renew
        assertTrue(w1.isAutoRenewLease());
        vm.toggleAutoRenewSelectedWorker(false).get();
        assertFalse(w1.isAutoRenewLease());
        vm.toggleAutoRenewSelectedWorker(true).get();
        assertTrue(w1.isAutoRenewLease());

        // Update Thresholds
        vm.updateThresholdsSelectedWorker(70.0, 75.0, 12).get();
        assertEquals(70.0, w1.getTargetCpuPct(), 0.01);
        assertEquals(75.0, w1.getTargetRamPct(), 0.01);
        assertEquals(12, w1.getMaxConcurrentTasks());
    }

    @Test
    void testWorkerNodeFormatters() {
        WorkerNode w = new WorkerNode("w-fmt", "host-fmt", "127.0.0.1", "linux", "x86_64", "ONLINE", 4, 8000000000L, 10.0, 1000000000L, 0, "");
        
        // Display name fallback
        assertEquals("host-fmt", w.getDisplayName());
        w.setFriendlyName("   ");
        assertEquals("host-fmt", w.getDisplayName());
        w.setFriendlyName("prod-worker-1");
        assertEquals("prod-worker-1", w.getDisplayName());

        // Uptime formatter
        w.setUptimeSeconds(45);
        assertEquals("0m 45s", w.getFormattedUptime());
        w.setUptimeSeconds(125);
        assertEquals("2m 5s", w.getFormattedUptime());
        w.setUptimeSeconds(3665);
        assertEquals("1h 1m", w.getFormattedUptime());
        w.setUptimeSeconds(90065);
        assertEquals("1d 1h 1m", w.getFormattedUptime());

        // Lease remaining formatter
        w.setRemainingLeaseSec(45);
        assertEquals("0m 45s", w.getFormattedLeaseRemaining());
        w.setRemainingLeaseSec(125);
        assertEquals("2m 5s", w.getFormattedLeaseRemaining());
        w.setRemainingLeaseSec(3665);
        assertEquals("1h 1m", w.getFormattedLeaseRemaining());
        w.setRemainingLeaseSec(90065);
        assertEquals("25h 1m", w.getFormattedLeaseRemaining());
    }

    @Test
    void testQuarantineViewModelApproveRejectAndAutoAccept() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        WorkerService workerService = new WorkerService(client);

        ObservableList<QuarantineEnrollment> enrollments = FXCollections.observableArrayList();
        QuarantineEnrollment enr1 = new QuarantineEnrollment(
                "enr1", "w-new", "new.node", "10.1.1.1", "linux", "x86_64",
                "4E:8A:BC:31:09:FF:7D:12", "Tiger-Galaxy-77", 8, 16000000000L, "2026-10-15T15:00:00Z"
        );
        enrollments.add(enr1);

        QuarantineViewModel vm = new QuarantineViewModel(enrollments, workerService);
        assertEquals(1, vm.getEnrollments().size());
        assertEquals("Tiger-Galaxy-77", vm.getSelectedEnrollment().getVisualCode());

        // Auto-accept state loading
        vm.loadAutoAcceptState().get();
        assertTrue(vm.isAutoAccept());

        // Set Auto-accept
        vm.setAutoAccept(false).get();
        assertFalse(vm.isAutoAccept());

        // Approve with friendly name
        boolean approved = vm.approveSelected("custom-edge-01").get();
        assertTrue(approved);
        assertEquals(0, vm.getEnrollments().size());
        assertNull(vm.getSelectedEnrollment());
    }
}
