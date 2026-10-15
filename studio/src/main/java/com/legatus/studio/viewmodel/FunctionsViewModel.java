package com.legatus.studio.viewmodel;

import com.legatus.studio.model.BytecodeItem;
import com.legatus.studio.service.BytecodeService;
import com.legatus.studio.service.TaskService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel driving the Functions & Bytecode Workbench.
 */
public class FunctionsViewModel {

    private final ObservableList<BytecodeItem> bytecodeItems = FXCollections.observableArrayList();
    private final ObjectProperty<BytecodeItem> selectedBytecode = new SimpleObjectProperty<>();

    private final StringProperty selectedFilePath = new SimpleStringProperty("");
    private final StringProperty calculatedSha256 = new SimpleStringProperty("");
    private final StringProperty functionNameInput = new SimpleStringProperty("");
    private final StringProperty entrypointInput = new SimpleStringProperty("main");
    private final BooleanProperty isUploading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final BytecodeService bytecodeService;
    private TaskService taskService;

    // Execution parameters
    private final StringProperty argumentsJsonInput = new SimpleStringProperty("{}");
    private final StringProperty instructionBudgetInput = new SimpleStringProperty("10000000");
    private final StringProperty memoryLimitMbInput = new SimpleStringProperty("64");
    private final StringProperty timeoutSecondsInput = new SimpleStringProperty("60");
    private final StringProperty taskPriorityInput = new SimpleStringProperty("NORMAL");
    private final BooleanProperty isExecuting = new SimpleBooleanProperty(false);
    private final StringProperty executionStatusMessage = new SimpleStringProperty("");

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    public FunctionsViewModel(BytecodeService bytecodeService) {
        this(bytecodeService, null);
    }

    public FunctionsViewModel(BytecodeService bytecodeService, TaskService taskService) {
        this.bytecodeService = Objects.requireNonNull(bytecodeService, "bytecodeService must not be null");
        this.taskService = taskService;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    public void inspectLocalFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        selectedFilePath.set(file.toAbsolutePath().toString());
        String sha256 = BytecodeService.computeFileSha256(file);
        calculatedSha256.set(sha256);

        String fileName = file.getFileName().toString();
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        functionNameInput.set(fileName);
    }

    public CompletableFuture<Void> loadCatalogedFunctions() {
        return bytecodeService.getCatalogedFunctions()
                .thenAccept(list -> {
                    com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                        for (com.legatus.studio.protocol.RestDto.SolixFunctionDto fn : list) {
                            String sha = fn.effectiveSha256();
                            if (!sha.isBlank() && bytecodeItems.stream().noneMatch(b -> b.getSha256().equalsIgnoreCase(sha))) {
                                BytecodeItem item = new BytecodeItem(
                                        sha,
                                        fn.name() != null ? fn.name() : "function",
                                        fn.effectiveEntryPoint(),
                                        0L,
                                        fn.createdAt() != null ? String.valueOf(fn.createdAt()) : ""
                                );
                                bytecodeItems.add(item);
                            }
                        }
                    });
                })
                .exceptionally(ex -> null);
    }

    public CompletableFuture<Boolean> executeUpload() {
        String filePath = selectedFilePath.get();
        if (filePath.isBlank()) {
            statusMessage.set("Please select a valid .slxb bytecode file first.");
            return CompletableFuture.completedFuture(false);
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            statusMessage.set("File does not exist: " + filePath);
            return CompletableFuture.completedFuture(false);
        }

        isUploading.set(true);
        try {
            byte[] bytes = Files.readAllBytes(path);
            String name = functionNameInput.get().isBlank() ? "function" : functionNameInput.get();
            String entry = entrypointInput.get().isBlank() ? "main" : entrypointInput.get();

            return bytecodeService.uploadBytecode(bytes, name)
                    .thenApply(sha -> {
                        com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                            BytecodeItem item = new BytecodeItem(sha, name, entry, bytes.length, java.time.Instant.now().toString());
                            boolean exists = bytecodeItems.stream().anyMatch(b -> b.getSha256().equalsIgnoreCase(sha));
                            if (!exists) {
                                bytecodeItems.add(item);
                            }
                            selectedBytecode.set(item);
                            isUploading.set(false);
                            statusMessage.set("Bytecode registered: " + sha.substring(0, Math.min(16, sha.length())) + "...");
                        });
                        return true;
                    })
                    .exceptionally(ex -> {
                        com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                            isUploading.set(false);
                            statusMessage.set("Upload failed: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                        });
                        return false;
                    });
        } catch (IOException e) {
            isUploading.set(false);
            statusMessage.set("Failed to read file: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<com.legatus.studio.protocol.RestDto.CreateTaskResponse> executeSelectedFunction() {
        BytecodeItem item = selectedBytecode.get();
        if (item == null || item.getSha256().isBlank()) {
            executionStatusMessage.set("Please select a function from the table or upload bytecode first.");
            return CompletableFuture.completedFuture(null);
        }
        if (taskService == null) {
            executionStatusMessage.set("Task service unavailable.");
            return CompletableFuture.completedFuture(null);
        }

        java.util.Map<String, Object> argsMap;
        String rawJson = argumentsJsonInput.get();
        try {
            if (rawJson != null && !rawJson.isBlank()) {
                argsMap = MAPPER.readValue(rawJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            } else {
                argsMap = java.util.Map.of();
            }
        } catch (Exception e) {
            executionStatusMessage.set("Invalid Arguments JSON: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        long budget;
        try {
            budget = Long.parseLong(instructionBudgetInput.get().trim());
        } catch (NumberFormatException e) {
            executionStatusMessage.set("Invalid instruction budget number.");
            return CompletableFuture.completedFuture(null);
        }

        long memMb;
        try {
            memMb = Long.parseLong(memoryLimitMbInput.get().trim());
        } catch (NumberFormatException e) {
            executionStatusMessage.set("Invalid memory limit (MB).");
            return CompletableFuture.completedFuture(null);
        }
        long memoryBytes = memMb * 1024L * 1024L;

        int timeoutSec;
        try {
            timeoutSec = Integer.parseInt(timeoutSecondsInput.get().trim());
        } catch (NumberFormatException e) {
            executionStatusMessage.set("Invalid timeout seconds.");
            return CompletableFuture.completedFuture(null);
        }
        int timeoutMs = timeoutSec * 1000;

        String priority = taskPriorityInput.get();
        if (priority == null || priority.isBlank()) priority = "NORMAL";

        isExecuting.set(true);
        executionStatusMessage.set("Submitting task to cluster...");

        return taskService.createTask(
                item.getFunctionName(),
                item.getSha256(),
                item.getEntryPoint(),
                budget,
                memoryBytes,
                timeoutMs,
                priority,
                argsMap
        ).thenApply(resp -> {
            com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                isExecuting.set(false);
                if (resp != null) {
                    executionStatusMessage.set("Task submitted: " + resp.task_id() + " [Status: " + resp.status() + "]");
                } else {
                    executionStatusMessage.set("Task accepted.");
                }
            });
            return resp;
        }).exceptionally(ex -> {
            com.legatus.studio.service.FxAsync.runOnFxThread(() -> {
                isExecuting.set(false);
                executionStatusMessage.set("Execution error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
            });
            return null;
        });
    }

    public ObservableList<BytecodeItem> getBytecodeItems() { return bytecodeItems; }
    public ObjectProperty<BytecodeItem> selectedBytecodeProperty() { return selectedBytecode; }
    public BytecodeItem getSelectedBytecode() { return selectedBytecode.get(); }
    public void setSelectedBytecode(BytecodeItem item) { this.selectedBytecode.set(item); }

    public StringProperty selectedFilePathProperty() { return selectedFilePath; }
    public StringProperty calculatedSha256Property() { return calculatedSha256; }
    public StringProperty functionNameInputProperty() { return functionNameInput; }
    public StringProperty entrypointInputProperty() { return entrypointInput; }
    public BooleanProperty isUploadingProperty() { return isUploading; }
    public StringProperty statusMessageProperty() { return statusMessage; }

    public StringProperty argumentsJsonInputProperty() { return argumentsJsonInput; }
    public StringProperty instructionBudgetInputProperty() { return instructionBudgetInput; }
    public StringProperty memoryLimitMbInputProperty() { return memoryLimitMbInput; }
    public StringProperty timeoutSecondsInputProperty() { return timeoutSecondsInput; }
    public StringProperty taskPriorityInputProperty() { return taskPriorityInput; }
    public BooleanProperty isExecutingProperty() { return isExecuting; }
    public StringProperty executionStatusMessageProperty() { return executionStatusMessage; }
}
