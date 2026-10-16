package com.legatus.studio.viewmodel;

import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.service.TaskService;
import com.legatus.studio.service.WorkerService;
import com.legatus.studio.transport.RestClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FleetActionAlertTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testFleetActionErrorMessagePropagation() throws InterruptedException {
        ObservableList<WorkerNode> workers = FXCollections.observableArrayList();
        WorkerNode worker = new WorkerNode(
                "worker-err-1", "Err Node", "err.local", "127.0.0.1", "linux", "x86_64",
                "ONLINE", true, 4, 8L * 1024 * 1024 * 1024,
                75.0, 80.0, 16, true,
                1000L, 500L, 10.0, 1024L * 1024, 0, "", ""
        );
        workers.add(worker);

        // Custom failing WorkerService
        WorkerService failingService = new WorkerService(new RestClient("http://localhost:9999")) {
            @Override
            public CompletableFuture<Void> deleteWorker(String workerId) {
                return CompletableFuture.failedFuture(new IllegalStateException("Worker is currently executing non-interruptible tasks"));
            }

            @Override
            public CompletableFuture<Void> drainWorker(String workerId) {
                return CompletableFuture.failedFuture(new RuntimeException("Connection refused by control plane"));
            }
        };

        FleetViewModel viewModel = new FleetViewModel(workers, failingService);
        viewModel.setSelectedWorker(worker);

        // Trigger failing delete action
        viewModel.deleteSelectedWorker();

        // Wait for JavaFX thread dispatch
        CountDownLatch latch1 = new CountDownLatch(1);
        Platform.runLater(latch1::countDown);
        assertTrue(latch1.await(2, TimeUnit.SECONDS));

        assertTrue(viewModel.getErrorMessage().contains("Worker is currently executing non-interruptible tasks"),
                "errorMessageProperty must contain backend failure message on delete failure");

        // Trigger failing drain action
        viewModel.drainSelectedWorker();

        CountDownLatch latch2 = new CountDownLatch(1);
        Platform.runLater(latch2::countDown);
        assertTrue(latch2.await(2, TimeUnit.SECONDS));

        assertTrue(viewModel.getErrorMessage().contains("Connection refused by control plane"),
                "errorMessageProperty must contain backend failure message on drain failure");
    }

    @Test
    void testTaskAbortErrorMessagePropagation() throws InterruptedException {
        ObservableList<TaskRecord> tasks = FXCollections.observableArrayList();
        TaskRecord task = new TaskRecord("task-abort-err", "test", "EXECUTING", 0, "w1", 100L, 1024L, 50L, "", "");
        tasks.add(task);

        TaskService failingTaskService = new TaskService(new RestClient("http://localhost:9999")) {
            @Override
            public CompletableFuture<Void> abortTask(String taskId, String reason) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Task is already in terminal state: COMPLETED"));
            }
        };

        TasksViewModel viewModel = new TasksViewModel(tasks, failingTaskService);
        viewModel.setSelectedTask(task);

        viewModel.triggerAbort("Abort test");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertTrue(viewModel.getErrorMessage().contains("Task is already in terminal state: COMPLETED"),
                "errorMessageProperty in TasksViewModel must contain backend failure message on abort failure");
    }
}
