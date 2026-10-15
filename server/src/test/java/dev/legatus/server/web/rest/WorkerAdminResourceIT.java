package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.WorkerEnrollmentService;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Integration tests for {@link WorkerAdminResource} REST endpoints:
 * api-endpoint.md Section 5.
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class WorkerAdminResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    @Autowired
    private WorkerEnrollmentService workerEnrollmentService;

    private WorkerNode quarantinedWorker;
    private WorkerNode onlineWorker;

    @BeforeEach
    void setUp() {
        quarantinedWorker = new WorkerNode();
        quarantinedWorker.setWorkerId("worker-quarantine-" + UUID.randomUUID());
        quarantinedWorker.setEnrollmentId("enr-" + UUID.randomUUID());
        quarantinedWorker.setHostname("quarantine-host.internal");
        quarantinedWorker.setOs(OperatingSystem.LINUX);
        quarantinedWorker.setArch(CpuArchitecture.X86_64);
        quarantinedWorker.setAgentVersion("1.0.0");
        quarantinedWorker.setEd25519Pubkey("test-key-base64");
        quarantinedWorker.setFingerprint("AA:BB:CC:DD:EE:FF:00:11");
        quarantinedWorker.setVisualCode("Tiger-Galaxy-77");
        quarantinedWorker.setState(WorkerState.PENDING_APPROVAL);
        quarantinedWorker.setSourceIp("192.168.1.100");
        quarantinedWorker.setDraining(false);
        quarantinedWorker.setPhysicalCores(4);
        quarantinedWorker.setLogicalCores(8);
        quarantinedWorker.setTotalMemoryBytes(17179869184L);
        quarantinedWorker.setGpuPresent(false);
        quarantinedWorker.setAllocatableCores(8);
        quarantinedWorker.setMaxMemoryBytes(16000000000L);
        quarantinedWorker.setMaxInstructionBudget(50000000L);
        quarantinedWorker.setCreatedAt(Instant.now());
        quarantinedWorker.setEnrollmentExpiresAt(Instant.now().plusSeconds(900));
        workerNodeRepository.save(quarantinedWorker);

        onlineWorker = new WorkerNode();
        onlineWorker.setWorkerId("worker-online-" + UUID.randomUUID());
        onlineWorker.setEnrollmentId("enr-" + UUID.randomUUID());
        onlineWorker.setHostname("online-host.internal");
        onlineWorker.setOs(OperatingSystem.LINUX);
        onlineWorker.setArch(CpuArchitecture.X86_64);
        onlineWorker.setAgentVersion("1.0.0");
        onlineWorker.setEd25519Pubkey("test-key-online");
        onlineWorker.setFingerprint("11:22:33:44:55:66:77:88");
        onlineWorker.setVisualCode("Eagle-Nebula-42");
        onlineWorker.setState(WorkerState.ONLINE);
        onlineWorker.setSourceIp("192.168.1.101");
        onlineWorker.setDraining(false);
        onlineWorker.setPhysicalCores(8);
        onlineWorker.setLogicalCores(16);
        onlineWorker.setTotalMemoryBytes(34359738368L);
        onlineWorker.setGpuPresent(false);
        onlineWorker.setAllocatableCores(16);
        onlineWorker.setMaxMemoryBytes(32000000000L);
        onlineWorker.setMaxInstructionBudget(50000000L);
        onlineWorker.setCreatedAt(Instant.now());
        workerNodeRepository.save(onlineWorker);
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("GET /api/v1/workers should return all fleet workers")
    void testListWorkers() throws Exception {
        mockMvc.perform(get("/api/v1/workers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())))
            .andExpect(jsonPath("$[*].worker_id", hasItem(onlineWorker.getWorkerId())));
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("GET /api/v1/admin/workers/enrollments should return only quarantined workers")
    void testListQuarantinedEnrollments() throws Exception {
        mockMvc.perform(get("/api/v1/admin/workers/enrollments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())))
            .andExpect(jsonPath("$[*].worker_id", hasItem(quarantinedWorker.getWorkerId())))
            .andExpect(jsonPath("$[*].worker_id", not(hasItem(onlineWorker.getWorkerId()))));
    }

    @Test
    @WithMockUser(username = "operator_user", roles = { "OPERATOR" })
    @DisplayName("POST /api/v1/admin/workers/enrollments/{id}/approve should approve by enrollmentId")
    void testApproveEnrollment() throws Exception {
        WorkerAdminResource.ApproveRequest req = new WorkerAdminResource.ApproveRequest("24h");

        mockMvc.perform(post("/api/v1/admin/workers/enrollments/{id}/approve", quarantinedWorker.getEnrollmentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        WorkerNode updated = workerNodeRepository.findById(quarantinedWorker.getWorkerId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(WorkerState.APPROVED);
    }

    @Test
    @WithMockUser(username = "operator_user", roles = { "OPERATOR" })
    @DisplayName("POST /api/v1/admin/workers/enrollments/{id}/reject should reject quarantined worker")
    void testRejectEnrollment() throws Exception {
        WorkerAdminResource.RejectRequest req = new WorkerAdminResource.RejectRequest("Untrusted hardware", false);

        mockMvc.perform(post("/api/v1/admin/workers/enrollments/{id}/reject", quarantinedWorker.getEnrollmentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        WorkerNode updated = workerNodeRepository.findById(quarantinedWorker.getWorkerId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(WorkerState.REVOKED);
    }

    @Test
    @WithMockUser(username = "operator_user", roles = { "OPERATOR" })
    @DisplayName("POST /api/v1/admin/workers/{workerId}/drain should toggle drain state")
    void testDrainWorker() throws Exception {
        mockMvc.perform(post("/api/v1/admin/workers/{workerId}/drain", onlineWorker.getWorkerId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAINING"))
            .andExpect(jsonPath("$.draining").value(true));

        WorkerNode updated = workerNodeRepository.findById(onlineWorker.getWorkerId()).orElseThrow();
        assertThat(updated.getDraining()).isTrue();
        assertThat(updated.getState()).isEqualTo(WorkerState.DRAINING);
    }

    @Test
    @WithMockUser(username = "operator_user", roles = { "OPERATOR" })
    @DisplayName("POST /api/v1/admin/workers/{workerId}/revoke should revoke worker")
    void testRevokeWorker() throws Exception {
        mockMvc.perform(post("/api/v1/admin/workers/{workerId}/revoke", onlineWorker.getWorkerId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"));

        WorkerNode updated = workerNodeRepository.findById(onlineWorker.getWorkerId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(WorkerState.REVOKED);
    }

    @Test
    @WithMockUser(username = "auditor_user", roles = { "AUDITOR" })
    @DisplayName("GET /api/v1/cluster/stats should return aggregate cluster statistics")
    void testGetClusterStats() throws Exception {
        mockMvc.perform(get("/api/v1/cluster/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes_online", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.total_allocatable_cores", greaterThanOrEqualTo(16)));
    }
}
