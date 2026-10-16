package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.scheduler.CronSchedulerService;
import java.util.Map;
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

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class CronSchedulerResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CronSchedulerService cronSchedulerService;

    @Autowired
    private SolixFunctionRepository solixFunctionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SolixFunction fn = new SolixFunction();
        fn.setName("test-cron-fn");
        fn.setSemver("1.0.0");
        fn.setEntryPoint("main");
        fn.setBytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
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
    @DisplayName("Should dynamically register, list, pause, resume, and delete cron triggers")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCronTriggerFullLifecycle() throws Exception {
        CronSchedulerResource.RegisterCronTriggerRequest req = new CronSchedulerResource.RegisterCronTriggerRequest(
            "dynamic-job-1",
            "test-cron-fn",
            "*/10 * * * * *",
            TaskPriority.HIGH,
            Map.of("key", "val"),
            Map.of("ENV_A", "1")
        );

        // 1. Register
        mockMvc.perform(post("/api/v1/admin/cron-triggers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("dynamic-job-1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 2. List
        mockMvc.perform(get("/api/v1/admin/cron-triggers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'dynamic-job-1')].status").value("ACTIVE"));

        // 3. Pause
        mockMvc.perform(post("/api/v1/admin/cron-triggers/dynamic-job-1/pause"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));

        assertThat(cronSchedulerService.isJobPaused("dynamic-job-1")).isTrue();

        // 4. Resume
        mockMvc.perform(post("/api/v1/admin/cron-triggers/dynamic-job-1/resume"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(cronSchedulerService.isJobPaused("dynamic-job-1")).isFalse();

        // 5. Delete
        mockMvc.perform(delete("/api/v1/admin/cron-triggers/dynamic-job-1"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/cron-triggers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'dynamic-job-1')]").doesNotExist());
    }
}
