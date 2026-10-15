package com.legatus.studio.viewmodel;

import com.legatus.studio.model.TaskRecord;
import com.legatus.studio.service.TaskService;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * ViewModel driving the Task Explorer, Live Inspector, and Two-Stage Abort Countdown (Issue #406).
 */
public class TasksViewModel implements AutoCloseable {

    private final ObservableList<TaskRecord> allTasks;
    private final FilteredList<TaskRecord> filteredTasks;
    private final ObjectProperty<TaskRecord> selectedTask = new SimpleObjectProperty<>();

    private final StringProperty filterStatus = new SimpleStringProperty("ALL");
    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final BooleanProperty isBusy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    // Dispatch form properties
    private final StringProperty dispatchName = new SimpleStringProperty("");
    private final StringProperty dispatchBytecodeSha = new SimpleStringProperty("");
    private final StringProperty dispatchEntrypoint = new SimpleStringProperty("main");
    private final LongProperty dispatchBudget = new SimpleLongProperty(10_000_000L);
    private final LongProperty dispatchMemory = new SimpleLongProperty(67_108_864L);

    private final TaskService taskService;
    private final ScheduledExecutorService countdownExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-abort-countdown");
        t.setDaemon(true);
        return t;
    });

    public TasksViewModel(ObservableList<TaskRecord> allTasks, TaskService taskService) {
        this.allTasks = Objects.requireNonNull(allTasks, "allTasks must not be null");
        this.taskService = Objects.requireNonNull(taskService, "taskService must not be null");

        this.filteredTasks = new FilteredList<>(allTasks, this::matchesFilter);
        this.filterStatus.addListener((obs, oldV, newV) -> filteredTasks.setPredicate(this::matchesFilter));
        this.searchQuery.addListener((obs, oldV, newV) -> filteredTasks.setPredicate(this::matchesFilter));

        if (!allTasks.isEmpty()) {
            selectedTask.set(allTasks.get(0));
        }
    }

    private boolean matchesFilter(TaskRecord task) {
        if (task == null) return false;

        String query = searchQuery.get().toLowerCase().trim();
        if (!query.isEmpty()) {
            boolean matchesId = task.getTaskId().toLowerCase().contains(query);
            boolean matchesName = task.getName().toLowerCase().contains(query);
            if (!matchesId && !matchesName) return false;
        }

        String status = filterStatus.get();
        if (!"ALL".equalsIgnoreCase(status) && !status.equalsIgnoreCase(task.getStatus())) {
            return false;
        }

        return true;
    }

    public CompletableFuture<Boolean> triggerAbort(String reason) {
        TaskRecord current = selectedTask.get();
        if (current == null) return CompletableFuture.completedFuture(false);

        isBusy.set(true);
        current.setIsAborting(true);
        current.setAbortCountdownSeconds(10);

        return taskService.abortTask(current.getTaskId(), reason)
                .thenApply(v -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        current.setStatus("ABORTING");
                        startAbortCountdown(current);
                        statusMessage.set("Task " + current.getTaskId() + " abort initiated (Stage 1 Grace Period - 10s).");
                    });
                    return true;
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        current.setIsAborting(false);
                        statusMessage.set("Abort request failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return false;
                });
    }

    private void startAbortCountdown(TaskRecord task) {
        ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];
        futureHolder[0] = countdownExecutor.scheduleAtFixedRate(() -> {
            com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                int current = task.getAbortCountdownSeconds();
                if (current <= 1 || !"ABORTING".equalsIgnoreCase(task.getStatus())) {
                    futureHolder[0].cancel(false);
                    task.setAbortCountdownSeconds(0);
                    task.setIsAborting(false);
                } else {
                    task.setAbortCountdownSeconds(current - 1);
                }
            });
        }, 1, 1, TimeUnit.SECONDS);
    }

    public CompletableFuture<String> executeDispatch() {
        if (dispatchName.get().isBlank() || dispatchBytecodeSha.get().isBlank()) {
            statusMessage.set("Task name and bytecode SHA-256 are required.");
            return CompletableFuture.completedFuture(null);
        }

        isBusy.set(true);
        return taskService.createTask(
                dispatchName.get(),
                dispatchBytecodeSha.get(),
                dispatchEntrypoint.get(),
                dispatchBudget.get(),
                dispatchMemory.get(),
                null
        ).thenApply(resp -> {
            com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                isBusy.set(false);
                statusMessage.set("Task dispatched: " + resp.task_id());
            });
            return resp.task_id();
        }).exceptionally(ex -> {
            com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                isBusy.set(false);
                statusMessage.set("Dispatch failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
            });
            return null;
        });
    }

    public CompletableFuture<Void> refreshTasks() {
        isBusy.set(true);
        return taskService.getTasks()
                .thenAccept(tasks -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        for (var t : tasks) {
                            boolean found = false;
                            for (var existing : allTasks) {
                                if (existing.getTaskId().equals(t.task_id())) {
                                    existing.setName(t.name() != null ? t.name() : "");
                                    existing.setStatus(t.status());
                                    if (t.exit_code() != null) existing.setExitCode(t.exit_code());
                                    if (t.assigned_worker_id() != null) existing.setAssignedWorkerId(t.assigned_worker_id());
                                    existing.setInstructionsConsumed(t.effectiveInstructions());
                                    existing.setPeakMemoryBytes(t.effectiveMemory());
                                    existing.setExecutionWallTimeMs(t.effectiveWallTime());
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                allTasks.add(new TaskRecord(
                                        t.task_id(),
                                        t.name() != null ? t.name() : "",
                                        t.status(),
                                        t.exit_code(),
                                        t.assigned_worker_id(),
                                        t.effectiveInstructions(),
                                        t.effectiveMemory(),
                                        t.effectiveWallTime(),
                                        t.created_at() != null ? t.created_at().toString() : "",
                                        t.finished_at() != null ? t.finished_at().toString() : ""
                                ));
                            }
                        }
                        if (selectedTask.get() == null && !allTasks.isEmpty()) {
                            selectedTask.set(allTasks.get(0));
                        }
                    });
                })
                .exceptionally(ex -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        isBusy.set(false);
                        statusMessage.set("Failed to refresh tasks: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    });
                    return null;
                });
    }

    public FilteredList<TaskRecord> getFilteredTasks() { return filteredTasks; }
    public ObjectProperty<TaskRecord> selectedTaskProperty() { return selectedTask; }
    public TaskRecord getSelectedTask() { return selectedTask.get(); }
    public void setSelectedTask(TaskRecord task) { this.selectedTask.set(task); }

    public StringProperty filterStatusProperty() { return filterStatus; }
    public String getFilterStatus() { return filterStatus.get(); }
    public void setFilterStatus(String value) { this.filterStatus.set(value); }

    public StringProperty searchQueryProperty() { return searchQuery; }
    public String getSearchQuery() { return searchQuery.get(); }
    public void setSearchQuery(String value) { this.searchQuery.set(value); }

    public BooleanProperty isBusyProperty() { return isBusy; }
    public StringProperty statusMessageProperty() { return statusMessage; }

    public StringProperty dispatchNameProperty() { return dispatchName; }
    public StringProperty dispatchBytecodeShaProperty() { return dispatchBytecodeSha; }
    public StringProperty dispatchEntrypointProperty() { return dispatchEntrypoint; }
    public LongProperty dispatchBudgetProperty() { return dispatchBudget; }
    public LongProperty dispatchMemoryProperty() { return dispatchMemory; }

    @Override
    public void close() {
        countdownExecutor.shutdownNow();
    }
}
