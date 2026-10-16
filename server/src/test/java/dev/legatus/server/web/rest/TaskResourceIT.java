package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.dto.task.AbortTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.storage.BytecodeStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link TaskResource} endpoints:
 * {@code /api/v1/tasks/**} (Task submission, status querying, cancellation, and RBAC enforcement).
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class TaskResourceIT {

    private static final String API_URL = "/api/v1/tasks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ComputeTaskRepository computeTaskRepository;

    @Autowired
    private BytecodeStorageService bytecodeStorageService;

    private String sampleSha256;
    private String sampleBase64;

    @BeforeEach
    void setUp() throws Exception {
        byte[] raw = "SOLIX_IT_BYTECODE_CONTENT".getBytes(StandardCharsets.UTF_8);
        sampleBase64 = Base64.getEncoder().encodeToString(raw);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        sampleSha256 = HexFormat.of().formatHex(md.digest(raw));
    }

    @Test
    @DisplayName("ROLE_OPERATOR should submit task successfully (202 Accepted)")
    @WithMockUser(username = "operator@legatus.internal", roles = { "OPERATOR" })
    void testSubmitTaskAsOperator() throws Exception {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "render-frame-01",
            TaskPriority.HIGH,
            75,
            true,
            2,
            "main",
            new SubmitTaskRequest.BytecodePayload("solix_bc_v1", "base64", sampleSha256, sampleBase64),
            new SubmitTaskRequest.ConstraintsPayload(10_000_000L, 64L * 1024 * 1024, 20_000),
            Map.of("frame", 1),
            Map.of("RENDER_PASS", "diffuse"),
            List.of()
        );

        mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id").value(startsWith("task-")))
            .andExpect(jsonPath("$.status").value(isIn(List.of("QUEUED", "EXECUTING"))))
            .andExpect(jsonPath("$.submitted_at").isNumber());

        assertThat(bytecodeStorageService.exists(sampleSha256)).isTrue();
    }

    @Test
    @DisplayName("ROLE_AUDITOR should be forbidden from submitting tasks (403 Forbidden)")
    @WithMockUser(username = "auditor@legatus.internal", roles = { "AUDITOR" })
    void testSubmitTaskAsAuditorForbidden() throws Exception {
        SubmitTaskRequest request = new SubmitTaskRequest(
            "auditor-task",
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

        mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_AUDITOR should be allowed to query task status (200 OK)")
    @WithMockUser(username = "auditor@legatus.internal", roles = { "AUDITOR" })
    void testQueryTaskAsAuditor() throws Exception {
        ComputeTask task = new ComputeTask()
            .taskId("task-query-it")
            .name("audit-inspection-task")
            .status(TaskStatus.COMPLETED)
            .exitCode(0)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256(sampleSha256)
            .instructionBudget(5_000_000L)
            .maxMemoryBytes(32L * 1024 * 1024)
            .timeoutMs(10_000)
            .attemptCount(1)
            .maxRetries(0)
            .createdAt(Instant.now());

        computeTaskRepository.saveAndFlush(task);

        mockMvc.perform(get(API_URL + "/{taskId}", "task-query-it"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.task_id").value("task-query-it"))
            .andExpect(jsonPath("$.name").value("audit-inspection-task"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.exit_code").value(0));
    }

    @Test
    @DisplayName("ROLE_OPERATOR should be allowed to list all tasks (200 OK)")
    @WithMockUser(username = "operator@legatus.internal", roles = { "OPERATOR" })
    void testListTasksAsOperator() throws Exception {
        ComputeTask task1 = new ComputeTask()
            .taskId("task-list-1")
            .name("list-task-1")
            .status(TaskStatus.COMPLETED)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .instructionBudget(1000000L)
            .maxMemoryBytes(64 * 1024 * 1024L)
            .timeoutMs(5000)
            .attemptCount(0)
            .maxRetries(3)
            .entryPoint("main")
            .bytecodeSha256(sampleSha256)
            .createdAt(Instant.now());
        ComputeTask task2 = new ComputeTask()
            .taskId("task-list-2")
            .name("list-task-2")
            .status(TaskStatus.QUEUED)
            .priority(TaskPriority.HIGH)
            .priorityWeight(80)
            .idempotent(true)
            .instructionBudget(2000000L)
            .maxMemoryBytes(128 * 1024 * 1024L)
            .timeoutMs(10000)
            .attemptCount(0)
            .maxRetries(1)
            .entryPoint("main")
            .bytecodeSha256(sampleSha256)
            .createdAt(Instant.now().plusMillis(100));

        computeTaskRepository.saveAndFlush(task1);
        computeTaskRepository.saveAndFlush(task2);

        mockMvc.perform(get(API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[*].task_id", hasItems("task-list-1", "task-list-2")));
    }

    @Test
    @DisplayName("ROLE_OPERATOR should abort a queued task (202 Accepted)")
    @WithMockUser(username = "operator@legatus.internal", roles = { "OPERATOR" })
    void testAbortQueuedTaskAsOperator() throws Exception {
        ComputeTask task = new ComputeTask()
            .taskId("task-abort-it")
            .name("queued-for-abort")
            .status(TaskStatus.QUEUED)
            .priority(TaskPriority.LOW)
            .priorityWeight(25)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256(sampleSha256)
            .instructionBudget(1_000_000L)
            .maxMemoryBytes(16L * 1024 * 1024)
            .timeoutMs(5_000)
            .attemptCount(0)
            .maxRetries(0)
            .createdAt(Instant.now());

        computeTaskRepository.saveAndFlush(task);

        AbortTaskRequest abortReq = new AbortTaskRequest("USER_CANCELLED", 5000);

        mockMvc.perform(post(API_URL + "/{taskId}/abort", "task-abort-it")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(abortReq)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id").value("task-abort-it"))
            .andExpect(jsonPath("$.status").value("ABORTED"));

        ComputeTask updated = computeTaskRepository.findById("task-abort-it").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.ABORTED);
    }

    @Test
    @DisplayName("ROLE_AUDITOR should be forbidden from aborting tasks (403 Forbidden)")
    @WithMockUser(username = "auditor@legatus.internal", roles = { "AUDITOR" })
    void testAbortTaskAsAuditorForbidden() throws Exception {
        mockMvc.perform(post(API_URL + "/{taskId}/abort", "some-task")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated requests should be rejected with 401 Unauthorized")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get(API_URL + "/some-task"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
