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
}
