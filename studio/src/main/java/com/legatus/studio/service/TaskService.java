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
            String inputDataBase64) {
        RestDto.CreateTaskRequest request = new RestDto.CreateTaskRequest(
                name,
                bytecodeSha256,
                entrypoint != null ? entrypoint : "main",
                instructionBudget > 0 ? instructionBudget : 10_000_000L,
                maxMemoryBytes > 0 ? maxMemoryBytes : 67_108_864L,
                inputDataBase64
        );
        return restClient.post("/api/v1/tasks", request, RestDto.CreateTaskResponse.class);
    }

    public CompletableFuture<RestDto.TaskStatusResponse> getTask(String taskId) {
        return restClient.get("/api/v1/tasks/" + taskId, RestDto.TaskStatusResponse.class);
    }

    public CompletableFuture<Void> abortTask(String taskId, String reason) {
        RestDto.AbortTaskRequest request = new RestDto.AbortTaskRequest(reason != null ? reason : "Operator requested abort");
        return restClient.post("/api/v1/tasks/" + taskId + "/abort", request, Void.class);
    }
}
