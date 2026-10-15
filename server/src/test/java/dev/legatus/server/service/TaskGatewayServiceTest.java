package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.dto.task.AbortTaskRequest;
import dev.legatus.server.service.dto.task.AbortTaskResponse;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import dev.legatus.server.service.dto.task.TaskStatusResponse;
import dev.legatus.server.service.scheduler.TaskDispatcher;
import dev.legatus.server.service.scheduler.TaskSchedulerService;
import dev.legatus.server.storage.BytecodeStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TaskGatewayServiceTest {

    @Mock
    private ComputeTaskRepository computeTaskRepository;

    @Mock
    private BytecodeStorageService bytecodeStorageService;

    @Mock
    private TaskSchedulerService taskSchedulerService;

    @Mock
    private TaskDispatcher taskDispatcher;

    @Mock
    private WormAuditService wormAuditService;

    private LegatusProperties legatusProperties;
    private ObjectMapper objectMapper;
    private TaskGatewayService taskGatewayService;

    private static final String SAMPLE_BC = "SOLIX_SAMPLE_BYTECODE_CONTENT";
    private String sampleSha256;
    private String sampleBase64;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        legatusProperties = new LegatusProperties();
        legatusProperties.getScheduler().setMaxQueueDepth(100);

        taskGatewayService = new TaskGatewayService(
            computeTaskRepository,
            bytecodeStorageService,
            taskSchedulerService,
            taskDispatcher,
            wormAuditService,
            legatusProperties,
            objectMapper
        );

        byte[] raw = SAMPLE_BC.getBytes(StandardCharsets.UTF_8);
        sampleBase64 = Base64.getEncoder().encodeToString(raw);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        sampleSha256 = HexFormat.of().formatHex(md.digest(raw));
    }

    @Test
    @DisplayName("Should successfully submit task with Base64 payload, store bytecode, and trigger scheduler")
    void testSubmitTaskSuccessWithPayload() throws IOException {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "matrix-multiply",
            TaskPriority.HIGH,
            80,
            true,
            3,
            "execute",
            new SubmitTaskRequest.BytecodePayload("solix_bc_v1", "base64", sampleSha256, sampleBase64),
            new SubmitTaskRequest.ConstraintsPayload(20_000_000L, 128L * 1024 * 1024, 15_000),
            Map.of("matrix_size", 512),
            Map.of("ENV", "PROD"),
            List.of("DB_SECRET")
        );

        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(5L);
        when(computeTaskRepository.saveAndFlush(any(ComputeTask.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkerNode worker = new WorkerNode().workerId("worker-1");
        when(taskSchedulerService.assignAndDispatchTaskWithRetry(any())).thenReturn(Optional.of(worker));

        SubmitTaskResponse response = taskGatewayService.submitTask(request, "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.taskId()).startsWith("task-");
        assertThat(response.status()).isEqualTo(TaskStatus.EXECUTING);

        verify(bytecodeStorageService).storeWithExpectedHash(eq(sampleSha256), any(InputStream.class));
        verify(computeTaskRepository).saveAndFlush(any(ComputeTask.class));
        verify(wormAuditService).recordEvent(
            any(), any(), eq("TASK_SUBMITTED"), eq("TASK"), any(), eq(AuditActionStatus.SUCCESS), any(), eq("192.168.1.100"), any()
        );
        verify(taskSchedulerService).assignAndDispatchTaskWithRetry(response.taskId());
    }

    @Test
    @DisplayName("Should submit task with pre-existing bytecode in CAS storage")
    void testSubmitTaskWithExistingBytecode() throws Exception {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "existing-workload",
            null,
            null,
            null,
            null,
            null,
            new SubmitTaskRequest.BytecodePayload("solix_bc_v1", null, sampleSha256, null),
            null,
            null,
            null,
            null
        );

        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(0L);
        when(bytecodeStorageService.exists(sampleSha256)).thenReturn(true);
        when(computeTaskRepository.saveAndFlush(any(ComputeTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskSchedulerService.assignAndDispatchTaskWithRetry(any())).thenReturn(Optional.empty());

        SubmitTaskResponse response = taskGatewayService.submitTask(request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TaskStatus.QUEUED);
        verify(bytecodeStorageService, never()).storeWithExpectedHash(any(), any());
    }

    @Test
    @DisplayName("Should reject task submission when cluster queue depth is exceeded (HTTP 429)")
    void testSubmitTaskQueueDepthExceeded() {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "queued-workload",
            TaskPriority.LOW,
            null,
            false,
            0,
            "main",
            new SubmitTaskRequest.BytecodePayload("solix_bc_v1", "base64", sampleSha256, sampleBase64),
            null,
            null,
            null,
            null
        );

        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(100L);

        assertThatThrownBy(() -> taskGatewayService.submitTask(request, "127.0.0.1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS);

        verify(computeTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should reject task submission with malformed SHA-256 digest (HTTP 400)")
    void testSubmitTaskMalformedSha256() {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "malformed-workload",
            TaskPriority.LOW,
            null,
            false,
            0,
            "main",
            new SubmitTaskRequest.BytecodePayload("solix_bc_v1", "base64", "not-a-valid-sha256", sampleBase64),
            null,
            null,
            null,
            null
        );

        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(0L);

        assertThatThrownBy(() -> taskGatewayService.submitTask(request, "127.0.0.1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject task submission when bytecode is missing from storage (HTTP 400)")
    void testSubmitTaskMissingBytecode() {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "missing-bytecode",
            TaskPriority.LOW,
            null,
            false,
            0,
            "main",
            new SubmitTaskRequest.BytecodePayload("solix_bc_v1", null, sampleSha256, null),
            null,
            null,
            null,
            null
        );

        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(0L);
        when(bytecodeStorageService.exists(sampleSha256)).thenReturn(false);

        assertThatThrownBy(() -> taskGatewayService.submitTask(request, "127.0.0.1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should query task status and format telemetry response")
    void testGetTaskStatusSuccess() {
        WorkerNode worker = new WorkerNode().workerId("worker-compute-42");
        ComputeTask task = new ComputeTask()
            .taskId("task-1001")
            .name("data-pipeline")
            .status(TaskStatus.COMPLETED)
            .exitCode(0)
            .priority(TaskPriority.HIGH)
            .priorityWeight(75)
            .idempotent(true)
            .entryPoint("main")
            .bytecodeSha256(sampleSha256)
            .instructionBudget(10_000_000L)
            .maxMemoryBytes(64L * 1024 * 1024)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(2)
            .createdAt(Instant.ofEpochMilli(1728900000000L))
            .startedAt(Instant.ofEpochMilli(1728900001000L))
            .completedAt(Instant.ofEpochMilli(1728900003500L))
            .instructionsConsumed(450000L)
            .peakMemoryBytes(16L * 1024 * 1024)
            .wallTimeMs(2500L)
            .outputJson("{\"checksum\":12345}")
            .errorJson(null);
        task.setAssignedWorker(worker);

        when(computeTaskRepository.findById("task-1001")).thenReturn(Optional.of(task));

        TaskStatusResponse response = taskGatewayService.getTaskStatus("task-1001");

        assertThat(response).isNotNull();
        assertThat(response.taskId()).isEqualTo("task-1001");
        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.exitCode()).isEqualTo(0);
        assertThat(response.assignedWorkerId()).isEqualTo("worker-compute-42");
        assertThat(response.metrics()).isNotNull();
        assertThat(response.metrics().instructionsConsumed()).isEqualTo(450000L);
        assertThat(response.metrics().executionWallTimeMs()).isEqualTo(2500L);
        assertThat(response.output()).containsEntry("checksum", 12345);
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("Should return 404 Not Found when querying non-existent task")
    void testGetTaskStatusNotFound() {
        when(computeTaskRepository.findById("task-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskGatewayService.getTaskStatus("task-missing"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should immediately abort QUEUED task without sending WebSocket frames")
    void testAbortQueuedTask() {
        ComputeTask task = new ComputeTask()
            .taskId("task-queued-abort")
            .name("queued-job")
            .status(TaskStatus.QUEUED)
            .createdAt(Instant.now());

        when(computeTaskRepository.findById("task-queued-abort")).thenReturn(Optional.of(task));
        when(computeTaskRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AbortTaskRequest request = new AbortTaskRequest("OPERATOR_CANCELLED", 5000);
        AbortTaskResponse response = taskGatewayService.abortTask("task-queued-abort", request, "10.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.taskId()).isEqualTo("task-queued-abort");
        assertThat(response.status()).isEqualTo("ABORTED");
        assertThat(response.assignedWorkerId()).isNull();

        verify(taskDispatcher, never()).abortTask(any(), any(), any(), anyInt());
        verify(computeTaskRepository).saveAndFlush(task);
        verify(wormAuditService).recordEvent(
            any(), any(), eq("TASK_ABORTED"), eq("TASK"), eq("task-queued-abort"), eq(AuditActionStatus.SUCCESS), any(), eq("10.0.0.1"), any()
        );
    }

    @Test
    @DisplayName("Should forward ABORT_TASK frame to assigned worker when task is EXECUTING")
    void testAbortExecutingTask() {
        WorkerNode worker = new WorkerNode().workerId("worker-active-99");
        ComputeTask task = new ComputeTask()
            .taskId("task-exec-abort")
            .name("running-job")
            .status(TaskStatus.EXECUTING)
            .createdAt(Instant.now());
        task.setAssignedWorker(worker);

        when(computeTaskRepository.findById("task-exec-abort")).thenReturn(Optional.of(task));
        when(computeTaskRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AbortTaskRequest request = new AbortTaskRequest("DEADLINE_EXCEEDED", 8000);
        AbortTaskResponse response = taskGatewayService.abortTask("task-exec-abort", request, "10.0.0.2");

        assertThat(response).isNotNull();
        assertThat(response.taskId()).isEqualTo("task-exec-abort");
        assertThat(response.status()).isEqualTo("ABORTING");
        assertThat(response.assignedWorkerId()).isEqualTo("worker-active-99");
        assertThat(response.escalationDeadlineUtc()).isNotNull();

        verify(taskDispatcher).abortTask("worker-active-99", "task-exec-abort", "DEADLINE_EXCEEDED", 8000);
        verify(computeTaskRepository).saveAndFlush(task);
    }

    @Test
    @DisplayName("Should reject abort on already completed task (HTTP 409 Conflict)")
    void testAbortTerminalTaskConflict() {
        ComputeTask task = new ComputeTask()
            .taskId("task-done")
            .status(TaskStatus.COMPLETED);

        when(computeTaskRepository.findById("task-done")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskGatewayService.abortTask("task-done", null, "127.0.0.1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(taskDispatcher, never()).abortTask(any(), any(), any(), anyInt());
    }
}
