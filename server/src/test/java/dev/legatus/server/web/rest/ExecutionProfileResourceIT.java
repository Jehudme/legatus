package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.ExecutionProfile;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.repository.ExecutionProfileRepository;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.dto.ExecutionProfileDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Integration tests for {@link ExecutionProfileResource} and function metadata API.
 */
@AutoConfigureMockMvc
@IntegrationTest
@WithMockUser(username = "admin", roles = { "ADMIN" })
class ExecutionProfileResourceIT {

    private static final String API_URL = "/api/v1/execution-profiles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ExecutionProfileRepository executionProfileRepository;

    @Autowired
    private SolixFunctionRepository solixFunctionRepository;

    private SolixFunction testFunction;

    @BeforeEach
    void setUp() {
        SolixFunction fn = new SolixFunction()
            .name("fn-profile-test-" + UUID.randomUUID())
            .semver("1.0.0")
            .entryPoint("main")
            .bytecodeFormat("SLX_V1")
            .bytecodeSha256("sha-" + UUID.randomUUID())
            .bytecodeBlob(new byte[] { 0x01, 0x02 })
            .bytecodeBlobContentType("application/octet-stream")
            .defaultBudget(50_000L)
            .defaultMemoryBytes(8388608L)
            .defaultTimeoutMs(5000)
            .createdAt(Instant.now());
        testFunction = solixFunctionRepository.saveAndFlush(fn);
    }

    @AfterEach
    void tearDown() {
        executionProfileRepository.deleteAll();
        solixFunctionRepository.delete(testFunction);
    }

    @Test
    @Transactional
    @DisplayName("Should create an execution profile, reject duplicate name with 409, update, list, and delete")
    void testExecutionProfileLifecycle() throws Exception {
        ExecutionProfileDTO dto = new ExecutionProfileDTO();
        dto.setFunctionId(testFunction.getFunctionId());
        dto.setProfileName("Production High-Load");
        dto.setArgumentsJson("{\"batch_size\": 100}");
        dto.setInstructionBudget(1_000_000L);
        dto.setTimeLimitMs(10_000L);
        dto.setNotifyOnFailure(true);

        // 1. Create
        String responseJson = mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.profileId").isNotEmpty())
            .andExpect(jsonPath("$.profileName").value("Production High-Load"))
            .andExpect(jsonPath("$.argumentsJson").value("{\"batch_size\": 100}"))
            .andExpect(jsonPath("$.instructionBudget").value(1_000_000))
            .andExpect(jsonPath("$.timeLimitMs").value(10_000))
            .andExpect(jsonPath("$.notifyOnFailure").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        ExecutionProfileDTO created = om.readValue(responseJson, ExecutionProfileDTO.class);
        assertThat(created.getProfileId()).isNotNull();

        // 2. Reject duplicate profile name for the same function with 409 Conflict
        ExecutionProfileDTO duplicate = new ExecutionProfileDTO();
        duplicate.setFunctionId(testFunction.getFunctionId());
        duplicate.setProfileName("Production High-Load");
        duplicate.setArgumentsJson("{}");
        duplicate.setInstructionBudget(100L);
        duplicate.setTimeLimitMs(100L);

        mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(duplicate)))
            .andExpect(status().isConflict());

        // 3. List by function ID
        mockMvc.perform(get(API_URL)
                .param("functionId", testFunction.getFunctionId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].profileName").value("Production High-Load"));

        // 4. Update
        created.setArgumentsJson("{\"batch_size\": 500}");
        created.setInstructionBudget(0L); // unlimited
        created.setTimeLimitMs(0L); // unlimited
        created.setNotifyOnFailure(false);

        mockMvc.perform(put(API_URL + "/" + created.getProfileId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(created)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.argumentsJson").value("{\"batch_size\": 500}"))
            .andExpect(jsonPath("$.instructionBudget").value(0))
            .andExpect(jsonPath("$.timeLimitMs").value(0))
            .andExpect(jsonPath("$.notifyOnFailure").value(false));

        // 5. Delete
        mockMvc.perform(delete(API_URL + "/" + created.getProfileId()))
            .andExpect(status().isNoContent());

        List<ExecutionProfile> remaining = executionProfileRepository.findAllByFunctionIdOrderByProfileNameAsc(testFunction.getFunctionId());
        assertThat(remaining).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Should update function metadata (description and tags)")
    void testUpdateFunctionMetadata() throws Exception {
        SolixFunctionResource.UpdateFunctionMetadataRequest request = new SolixFunctionResource.UpdateFunctionMetadataRequest(
            "High performance crypto verification function",
            "crypto, verification, v1"
        );

        mockMvc.perform(put("/api/solix-functions/" + testFunction.getFunctionId() + "/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("High performance crypto verification function"))
            .andExpect(jsonPath("$.tags").value("crypto, verification, v1"));

        SolixFunction updated = solixFunctionRepository.findById(testFunction.getFunctionId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("High performance crypto verification function");
        assertThat(updated.getTags()).isEqualTo("crypto, verification, v1");
    }
}
