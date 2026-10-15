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

        vm.revokeSelectedWorker().get();
        assertEquals("REVOKED", w1.getState());
    }

    @Test
    void testQuarantineViewModelApproveAndReject() throws Exception {
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

        // Approve
        boolean approved = vm.approveSelected().get();
        assertTrue(approved);
        assertEquals(0, vm.getEnrollments().size());
        assertNull(vm.getSelectedEnrollment());
    }
}
