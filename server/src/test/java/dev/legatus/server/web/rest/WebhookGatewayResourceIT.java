package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.storage.BytecodeStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link WebhookGatewayResource} (api-endpoint.md and server-spec.md Section 6.1).
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class WebhookGatewayResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SolixFunctionRepository solixFunctionRepository;

    @Autowired
    private ComputeTaskRepository computeTaskRepository;

    @Autowired
    private BytecodeStorageService bytecodeStorageService;

    @Autowired
    private LegatusProperties legatusProperties;

    private SolixFunction savedFunction;
    private String bytecodeSha256;

    @BeforeEach
    void setUp() throws Exception {
        byte[] dummyBytecode = "SOLIX_WEBHOOK_BYTECODE_TEST".getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        bytecodeSha256 = HexFormat.of().formatHex(digest.digest(dummyBytecode));

        bytecodeStorageService.storeWithExpectedHash(bytecodeSha256, new ByteArrayInputStream(dummyBytecode));

        savedFunction = new SolixFunction()
            .name("gh-push-handler")
            .semver("1.0.0")
            .entryPoint("on_push")
            .bytecodeFormat("solix-v1")
            .bytecodeSha256(bytecodeSha256)
            .bytecodeBlob(dummyBytecode)
            .bytecodeBlobContentType("application/octet-stream")
            .defaultBudget(10_000_000L)
            .defaultMemoryBytes(67_108_864L)
            .defaultTimeoutMs(30_000)
            .createdAt(Instant.now());

        savedFunction = solixFunctionRepository.saveAndFlush(savedFunction);

        legatusProperties.getFeatures().setWebhooks(true);
        legatusProperties.getWebhooks().setEnabled(true);
        legatusProperties.getWebhooks().setSecret(null);
    }

    @AfterEach
    void tearDown() {
        legatusProperties.getFeatures().setWebhooks(true);
        legatusProperties.getWebhooks().setEnabled(true);
        legatusProperties.getWebhooks().setSecret(null);
    }

    @Test
    @DisplayName("Should successfully ingest third-party webhook payload and queue Solix task")
    void testIngestWebhookSuccess() throws Exception {
        Map<String, Object> payload = Map.of(
            "ref", "refs/heads/main",
            "repository", "legatus-core"
        );

        String responseBody = mockMvc.perform(
            post("/api/v1/webhooks/gh-push-handler")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(payload))
        )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id", not(emptyOrNullString())))
            .andExpect(jsonPath("$.status", is("QUEUED")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = om.readTree(responseBody).get("task_id").asText();
        ComputeTask task = computeTaskRepository.findById(taskId).orElseThrow();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.getPriority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(task.getEntryPoint()).isEqualTo("on_push");
        assertThat(task.getInputJson()).contains("refs/heads/main");
        assertThat(task.getInstructionBudget()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("Should ingest webhook with explicit priority and idempotency key header")
    void testIngestWebhookWithPriorityAndIdempotency() throws Exception {
        Map<String, Object> payload = Map.of("event", "payment_received");

        String responseBody = mockMvc.perform(
            post("/api/v1/webhooks/gh-push-handler?priority=HIGH")
                .header("X-Idempotency-Key", "idemp-webhook-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(payload))
        )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status", is("QUEUED")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = om.readTree(responseBody).get("task_id").asText();
        ComputeTask task = computeTaskRepository.findById(taskId).orElseThrow();

        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.getIdempotent()).isTrue();
        assertThat(task.getEnvJson()).contains("idemp-webhook-999");
    }

    @Test
    @DisplayName("Should reject webhook when target function is not registered")
    void testIngestWebhookFunctionNotFound() throws Exception {
        mockMvc.perform(
            post("/api/v1/webhooks/unknown-function")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\": \"ping\"}")
        )
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should enforce secret verification when webhook secret is configured")
    void testIngestWebhookSecretEnforcement() throws Exception {
        legatusProperties.getWebhooks().setSecret("cluster-webhook-token-2026");

        // 1. Missing secret -> 401 Unauthorized
        mockMvc.perform(
            post("/api/v1/webhooks/gh-push-handler")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\": \"ping\"}")
        )
            .andExpect(status().isUnauthorized());

        // 2. Bad secret -> 401 Unauthorized
        mockMvc.perform(
            post("/api/v1/webhooks/gh-push-handler")
                .header("X-Legatus-Webhook-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\": \"ping\"}")
        )
            .andExpect(status().isUnauthorized());

        // 3. Valid secret -> 202 Accepted
        mockMvc.perform(
            post("/api/v1/webhooks/gh-push-handler")
                .header("X-Legatus-Webhook-Secret", "cluster-webhook-token-2026")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\": \"ping\"}")
        )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status", is("QUEUED")));
    }

    @Test
    @DisplayName("Should reject webhook when webhook feature is disabled")
    void testIngestWebhookDisabled() throws Exception {
        legatusProperties.getFeatures().setWebhooks(false);

        mockMvc.perform(
            post("/api/v1/webhooks/gh-push-handler")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\": \"ping\"}")
        )
            .andExpect(status().isNotFound());
    }
}
