package dev.legatus.server.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.features.workflows.FailurePolicy;
import dev.legatus.server.features.workflows.WorkflowStep;
import dev.legatus.server.repository.SolixFunctionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
@TestPropertySource(properties = {
    "legatus.features.workflows=true"
})
class WorkflowResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SolixFunctionRepository solixFunctionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SolixFunction fn = new SolixFunction();
        fn.setName("step-fn");
        fn.setSemver("1.0.0");
        fn.setEntryPoint("main");
        fn.setBytecodeSha256("dc1238b685797a58431ed85bac23e9b6ab3c309e878564b338e2d0a117e57cd7");
        fn.setBytecodeFormat("solix_bc_v1");
        fn.setBytecodeBlob("mock-bytecode".getBytes());
        fn.setBytecodeBlobContentType("application/octet-stream");
        fn.setDefaultBudget(100000L);
        fn.setDefaultMemoryBytes(64 * 1024 * 1024L);
        fn.setDefaultTimeoutMs(10000);
        fn.setCreatedAt(java.time.Instant.now());
        solixFunctionRepository.save(fn);
    }

    @Test
    @DisplayName("Should submit valid workflow pipeline, return 201 Created, and launch steps")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testValidWorkflowSubmission() throws Exception {
        WorkflowResource.CreateWorkflowRequest req = new WorkflowResource.CreateWorkflowRequest(
            "test-data-pipeline",
            FailurePolicy.ABORT_PIPELINE,
            List.of(
                new WorkflowStep("step-extract", "step-fn", List.of(), Map.of("source", "db"), Map.of()),
                new WorkflowStep("step-transform", "step-fn", List.of("step-extract"), Map.of("op", "normalize"), Map.of())
            )
        );

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("test-data-pipeline"))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.steps.step-extract.status").value("RUNNING"))
            .andExpect(jsonPath("$.steps.step-transform.status").value("WAITING"));
    }

    @Test
    @DisplayName("Should reject cyclic workflow graph with HTTP 422 Unprocessable Entity")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCyclicWorkflowRejection() throws Exception {
        WorkflowResource.CreateWorkflowRequest req = new WorkflowResource.CreateWorkflowRequest(
            "cyclic-pipeline",
            FailurePolicy.ABORT_PIPELINE,
            List.of(
                new WorkflowStep("step-1", "step-fn", List.of("step-2"), Map.of(), Map.of()),
                new WorkflowStep("step-2", "step-fn", List.of("step-1"), Map.of(), Map.of())
            )
        );

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }
}
