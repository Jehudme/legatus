package com.legatus.studio.controllers;

import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.service.TaskService;
import com.legatus.studio.service.ViewLoaderService;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.TasksViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TaskExplorerListenerLeakTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testTaskSelectionListenerLeakPrevention() throws IOException {
        var view = ViewLoaderService.load("task_explorer");
        assertNotNull(view);
        TaskExplorerController controller = (TaskExplorerController) view.controller();
        assertNotNull(controller);

        ObservableList<TaskRecord> tasks = FXCollections.observableArrayList();
        TaskService dummyService = new TaskService(new RestClient("http://localhost:9999"));
        TasksViewModel viewModel = new TasksViewModel(tasks, dummyService);
        controller.setViewModel(viewModel);

        // Populate 50 tasks
        for (int i = 1; i <= 50; i++) {
            TaskRecord task = new TaskRecord(
                    "task-" + i, "taskName-" + i, "EXECUTING", 0, "worker-1",
                    1000L * i, 1024L * 1024, 500L * i, "2026-10-15T12:00:00Z", null
            );
            task.setTimeoutMs(30_000L);
            task.setInstructionBudget(5_000_000L);
            tasks.add(task);
        }

        // Inspect 50 tasks sequentially
        for (TaskRecord task : tasks) {
            controller.inspectTask(task);
            // Exactly 6 listeners must be registered for the active task (status, wallTime, timeout, instructions, budget, abortState)
            assertEquals(6, controller.getActiveUnbinderCount(),
                    "Active unbinders must not leak or grow unbounded when switching task selections");
        }

        // Unbind explicitly and assert 0 active unbinders
        controller.unbindCurrentTask();
        assertEquals(0, controller.getActiveUnbinderCount(),
                "Active unbinders count must be 0 after unbindCurrentTask");
    }

    @Test
    void testProgressBarsHiddenForInfiniteBudgetsAndDurations() throws IOException {
        var view = ViewLoaderService.load("task_explorer");
        TaskExplorerController controller = (TaskExplorerController) view.controller();

        ObservableList<TaskRecord> tasks = FXCollections.observableArrayList();
        TaskService dummyService = new TaskService(new RestClient("http://localhost:9999"));
        TasksViewModel viewModel = new TasksViewModel(tasks, dummyService);
        controller.setViewModel(viewModel);

        // Task with infinite duration (timeoutMs <= 0) and infinite budget (instructionBudget <= 0)
        TaskRecord infiniteTask = new TaskRecord(
                "task-inf", "infiniteTask", "EXECUTING", 0, "worker-1",
                50_000L, 1024L * 1024, 2500L, "2026-10-15T12:00:00Z", null
        );
        infiniteTask.setTimeoutMs(0L); // Infinite
        infiniteTask.setInstructionBudget(0L); // Infinite

        controller.inspectTask(infiniteTask);

        assertFalse(controller.getDurationProgressBox().isVisible(),
                "Duration progress box must be hidden when timeout is infinite (<= 0)");
        assertFalse(controller.getInstructionProgressBox().isVisible(),
                "Instruction progress box must be hidden when budget is infinite (<= 0)");

        // Now inspect bounded task
        TaskRecord boundedTask = new TaskRecord(
                "task-bounded", "boundedTask", "EXECUTING", 0, "worker-1",
                2_500_000L, 1024L * 1024, 15_000L, "2026-10-15T12:00:00Z", null
        );
        boundedTask.setTimeoutMs(30_000L);
        boundedTask.setInstructionBudget(5_000_000L);

        controller.inspectTask(boundedTask);

        assertTrue(controller.getDurationProgressBox().isVisible(),
                "Duration progress box must be visible when timeout is positive");
        assertTrue(controller.getInstructionProgressBox().isVisible(),
                "Instruction progress box must be visible when budget is positive");
        assertEquals(0.5, controller.getDurationProgressBar().getProgress(), 0.01);
        assertEquals(0.5, controller.getInstructionProgressBar().getProgress(), 0.01);
    }

    @Test
    void testAbortButtonStateTransitions() throws IOException {
        var view = ViewLoaderService.load("task_explorer");
        TaskExplorerController controller = (TaskExplorerController) view.controller();

        ObservableList<TaskRecord> tasks = FXCollections.observableArrayList();
        TaskService dummyService = new TaskService(new RestClient("http://localhost:9999"));
        TasksViewModel viewModel = new TasksViewModel(tasks, dummyService);
        controller.setViewModel(viewModel);

        TaskRecord task = new TaskRecord(
                "task-st", "stateTest", "QUEUED", 0, "", 0L, 0L, 0L, "2026-10-15T12:00:00Z", null
        );
        controller.inspectTask(task);
        assertFalse(controller.getAbortButton().isDisabled(), "Abort button must be enabled for QUEUED tasks");

        task.setStatus("EXECUTING");
        assertFalse(controller.getAbortButton().isDisabled(), "Abort button must be enabled for EXECUTING tasks");

        task.setStatus("COMPLETED");
        assertTrue(controller.getAbortButton().isDisabled(), "Abort button must be disabled for COMPLETED tasks");

        task.setStatus("FAILED");
        assertTrue(controller.getAbortButton().isDisabled(), "Abort button must be disabled for FAILED tasks");

        task.setStatus("ABORTED");
        assertTrue(controller.getAbortButton().isDisabled(), "Abort button must be disabled for ABORTED tasks");
    }
}
