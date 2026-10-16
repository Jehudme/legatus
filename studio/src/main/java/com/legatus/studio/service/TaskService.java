package com.legatus.studio.service;

import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing task creation, querying, and two-stage abort cancellations.
 */
public class TaskService {

    private final RestClient restClient;

    public TaskService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public CompletableFuture<RestDto.CreateTaskResponse> createTask(
            String name,
            String bytecodeSha256,
            String entrypoint,
            long instructionBudget,
            long maxMemoryBytes,
            int timeoutMs,
            String priority,
            java.util.Map<String, Object> arguments) {
        RestDto.BytecodeRef bytecodeRef = new RestDto.BytecodeRef("RAW", bytecodeSha256);
        RestDto.ConstraintsPayload constraints = new RestDto.ConstraintsPayload(
                instructionBudget > 0 ? instructionBudget : 10_000_000L,
                maxMemoryBytes > 0 ? maxMemoryBytes : 67_108_864L,
                timeoutMs > 0 ? timeoutMs : 60_000
        );
        RestDto.CreateTaskRequest request = new RestDto.CreateTaskRequest(
                name != null && !name.isBlank() ? name : "task-" + System.currentTimeMillis(),
                priority != null && !priority.isBlank() ? priority : "NORMAL",
                entrypoint != null && !entrypoint.isBlank() ? entrypoint : "main",
                bytecodeRef,
                constraints,
                arguments != null ? arguments : java.util.Map.of()
        );
        return restClient.post("/api/v1/tasks", request, RestDto.CreateTaskResponse.class);
    }

    public CompletableFuture<RestDto.CreateTaskResponse> submitTask(
            String name,
            String bytecodeSha256,
            String entrypoint,
            long instructionBudget,
            boolean unlimitedInstructions,
            long maxMemoryBytes,
            int timeoutMs,
            boolean unlimitedTime,
            String priority,
            boolean notifyOnFailure,
            java.util.Map<String, Object> arguments) {
        RestDto.BytecodeRef bytecodeRef = new RestDto.BytecodeRef("RAW", bytecodeSha256);
        RestDto.ConstraintsPayload constraints = new RestDto.ConstraintsPayload(
                unlimitedInstructions ? 0L : (instructionBudget > 0 ? instructionBudget : 10_000_000L),
                maxMemoryBytes > 0 ? maxMemoryBytes : 67_108_864L,
                unlimitedTime ? 0 : (timeoutMs > 0 ? timeoutMs : 60_000),
                unlimitedInstructions,
                unlimitedTime
        );
        RestDto.CreateTaskRequest request = new RestDto.CreateTaskRequest(
                name != null && !name.isBlank() ? name : "task-" + System.currentTimeMillis(),
                priority != null && !priority.isBlank() ? priority : "NORMAL",
                entrypoint != null && !entrypoint.isBlank() ? entrypoint : "main",
                bytecodeRef,
                constraints,
                arguments != null ? arguments : java.util.Map.of(),
                notifyOnFailure,
                unlimitedInstructions,
                unlimitedTime
        );
        return restClient.post("/api/v1/tasks", request, RestDto.CreateTaskResponse.class);
    }

    public CompletableFuture<RestDto.CreateTaskResponse> createTask(
            String name,
            String bytecodeSha256,
            String entrypoint,
            long instructionBudget,
            long maxMemoryBytes,
            String inputDataBase64) {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        if (inputDataBase64 != null && !inputDataBase64.isBlank()) {
            args.put("input_data_base64", inputDataBase64);
        }
        return createTask(name, bytecodeSha256, entrypoint, instructionBudget, maxMemoryBytes, 60_000, "NORMAL", args);
    }

    public CompletableFuture<java.util.List<RestDto.TaskStatusResponse>> getTasks() {
        return restClient.get("/api/v1/tasks", new com.fasterxml.jackson.core.type.TypeReference<java.util.List<RestDto.TaskStatusResponse>>() {});
    }

    public CompletableFuture<RestDto.TaskStatusResponse> getTask(String taskId) {
        return restClient.get("/api/v1/tasks/" + taskId, RestDto.TaskStatusResponse.class);
    }

    public CompletableFuture<Void> abortTask(String taskId, String reason) {
        RestDto.AbortTaskRequest request = new RestDto.AbortTaskRequest(reason != null ? reason : "Operator requested abort");
        return restClient.post("/api/v1/tasks/" + taskId + "/abort", request, Void.class);
    }
}
