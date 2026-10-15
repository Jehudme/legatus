package com.legatus.studio.viewmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.TaskService;
import com.legatus.studio.transport.LogStreamBuffer;
import com.legatus.studio.transport.RestClient;
import com.sun.net.httpserver.HttpServer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class TasksAndDrawerViewModelTest {

    private static HttpServer server;
    private static int port;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/v1/tasks/task-1/abort", ex -> {
            ex.sendResponseHeaders(200, -1);
        });

        server.createContext("/api/v1/tasks", ex -> {
            byte[] bytes = MAPPER.writeValueAsBytes(new RestDto.CreateTaskResponse("task-new-123", "QUEUED", "2026-10-15T15:00:00Z"));
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/api/solix-functions", ex -> {
            RestDto.SolixFunctionDto fn = new RestDto.SolixFunctionDto(
                    java.util.UUID.randomUUID(), "catalog-fn", "1.0.0", "main", "RAW",
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    10000000L, 67108864L, 60000, "2026-10-15T15:00:00Z"
            );
            byte[] bytes = MAPPER.writeValueAsBytes(java.util.List.of(fn));
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.setExecutor(null);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void testTasksViewModelFilteringAndAbort() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        TaskService taskService = new TaskService(client);

        ObservableList<TaskRecord> tasks = FXCollections.observableArrayList();
        TaskRecord t1 = new TaskRecord("task-1", "compute-primes", "EXECUTING", null, "w1", 1000L, 2000L, 50L, "2026-10-15T15:00:00Z", null);
        TaskRecord t2 = new TaskRecord("task-2", "fibonacci", "COMPLETED", 0, "w2", 500L, 1000L, 20L, "2026-10-15T15:00:00Z", "2026-10-15T15:00:01Z");
        tasks.addAll(t1, t2);

        try (TasksViewModel vm = new TasksViewModel(tasks, taskService)) {
            assertEquals(2, vm.getFilteredTasks().size());

            vm.setFilterStatus("EXECUTING");
            assertEquals(1, vm.getFilteredTasks().size());
            assertEquals("task-1", vm.getFilteredTasks().get(0).getTaskId());

            vm.setFilterStatus("ALL");
            vm.setSelectedTask(t1);

            boolean abortDispatched = vm.triggerAbort("Testing abort").get();
            assertTrue(abortDispatched);
            assertEquals("ABORTING", t1.getStatus());
            assertTrue(t1.isIsAborting());

            // Dispatch task
            vm.dispatchNameProperty().set("new-dispatched-job");
            vm.dispatchBytecodeShaProperty().set("abcdef123456");
            String newTaskId = vm.executeDispatch().get();
            assertEquals("task-new-123", newTaskId);
        }
    }

    @Test
    void testDrawerViewModelLogStream() {
        LogStreamBuffer buffer = new LogStreamBuffer(100, 1024 * 1024);
        DrawerViewModel drawer = new DrawerViewModel(buffer);

        assertEquals(0, drawer.getConsoleLines().size());

        buffer.appendChunk("Log stream started\nTask worker assigned\n");
        assertEquals(2, drawer.getConsoleLines().size());
        assertEquals("Log stream started", drawer.getConsoleLines().get(0));

        drawer.clearConsole();
        assertEquals(0, drawer.getConsoleLines().size());
    }

    @Test
    void testDrawerViewModelFiltering() {
        LogStreamBuffer buffer = new LogStreamBuffer(100, 1024 * 1024);
        DrawerViewModel drawer = new DrawerViewModel(buffer);

        buffer.appendChunk("[WORKER] Worker 1 online\n[TASK] Task 10 submitted\n[SYSTEM] Ready\n");
        assertEquals(3, drawer.getFilteredConsoleLines().size());

        drawer.filterTextProperty().set("WORKER");
        assertEquals(1, drawer.getFilteredConsoleLines().size());
        assertEquals("[WORKER] Worker 1 online", drawer.getFilteredConsoleLines().get(0));

        drawer.filterTextProperty().set("");
        assertEquals(3, drawer.getFilteredConsoleLines().size());
    }

    @Test
    void testFunctionsViewModelExecutionAndCatalog() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        com.legatus.studio.service.BytecodeService bytecodeService = new com.legatus.studio.service.BytecodeService(client);
        TaskService taskService = new TaskService(client);

        FunctionsViewModel vm = new FunctionsViewModel(bytecodeService, taskService);

        // Test loading catalog
        vm.loadCatalogedFunctions().get();
        assertEquals(1, vm.getBytecodeItems().size());
        assertEquals("catalog-fn", vm.getBytecodeItems().get(0).getFunctionName());
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", vm.getBytecodeItems().get(0).getSha256());

        // Select function
        vm.setSelectedBytecode(vm.getBytecodeItems().get(0));

        // Configure parameters
        vm.argumentsJsonInputProperty().set("{\"multiplier\": 4}");
        vm.instructionBudgetInputProperty().set("25000000");
        vm.memoryLimitMbInputProperty().set("128");
        vm.taskPriorityInputProperty().set("HIGH");

        // Execute function
        RestDto.CreateTaskResponse resp = vm.executeSelectedFunction().get();
        assertNotNull(resp);
        assertEquals("task-new-123", resp.task_id());
        assertTrue(vm.executionStatusMessageProperty().get().contains("task-new-123"));
    }
}
