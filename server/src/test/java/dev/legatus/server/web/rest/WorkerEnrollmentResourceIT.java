package dev.legatus.server.web.rest;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.legatus.server.IntegrationTest;
import dev.legatus.server.service.WorkerEnrollmentService;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link WorkerEnrollmentResource} REST endpoints:
 * {@code POST /api/v1/workers/register}, {@code GET /api/v1/workers/register/{id}/status},
 * and {@code POST /api/v1/auth/worker}.
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class WorkerEnrollmentResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private WorkerEnrollmentService workerEnrollmentService;

    private String generateValidEd25519PubKeyBase64() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private WorkerEnrollmentService.RegisterRequest createSampleRequest(String workerId) {
        return new WorkerEnrollmentService.RegisterRequest(
            workerId,
            "wk-test-host.internal",
            "linux",
            "x86_64",
            "1.0.0",
            generateValidEd25519PubKeyBase64(),
            new WorkerEnrollmentService.RegisterRequest.HardwareProfile(8, 16, 34359738368L, false, null),
            new WorkerEnrollmentService.RegisterRequest.AllocatableCapacity(12, 17179869184L, 50000000L)
        );
    }

    @Test
    @DisplayName("Should successfully enroll quarantined worker with 202 Accepted and cryptographic fingerprints")
    void testRegisterWorkerSuccess() throws Exception {
        WorkerEnrollmentService.RegisterRequest request = createSampleRequest("worker-enroll-001");

        mockMvc.perform(post("/api/v1/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(request))
                .with(req -> {
                    req.setRemoteAddr("10.10.1.1");
                    return req;
                }))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.enrollment_id", startsWith("enr-")))
            .andExpect(jsonPath("$.status", is("PENDING_APPROVAL")))
            .andExpect(jsonPath("$.fingerprint_hex", matchesPattern("^[0-9A-F]{2}(:[0-9A-F]{2}){7}$")))
            .andExpect(jsonPath("$.visual_code", matchesPattern("^[A-Za-z]+-[A-Za-z]+-\\d{2}$")))
            .andExpect(jsonPath("$.poll_interval_ms", is(3000)))
            .andExpect(jsonPath("$.expires_at", greaterThan(0L)));
    }

    @Test
    @DisplayName("Should poll pending status and deliver secret token exactly once upon approval")
    void testStatusPollAndSingleReadDelivery() throws Exception {
        String workerId = "worker-poll-002";
        WorkerEnrollmentService.RegisterRequest request = createSampleRequest(workerId);

        // 1. Register
        MvcResult regResult = mockMvc.perform(post("/api/v1/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(request))
                .with(req -> {
                    req.setRemoteAddr("10.10.1.2");
                    return req;
                }))
            .andExpect(status().isAccepted())
            .andReturn();

        String enrollmentId = JsonPath.read(regResult.getResponse().getContentAsString(), "$.enrollment_id");

        // 2. Poll while PENDING_APPROVAL -> secret_token must be null
        mockMvc.perform(get("/api/v1/workers/register/{enrollmentId}/status", enrollmentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PENDING_APPROVAL")))
            .andExpect(jsonPath("$.secret_token", nullValue()));

        // 3. Operator approves the node
        String approvedToken = workerEnrollmentService.approveWorker(workerId, "cluster_operator", "production-us-east");

        // 4. First poll after approval -> secret_token must be delivered
        mockMvc.perform(get("/api/v1/workers/register/{enrollmentId}/status", enrollmentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("APPROVED")))
            .andExpect(jsonPath("$.secret_token", is(approvedToken)))
            .andExpect(jsonPath("$.assigned_cluster", is("production-us-east")));

        // 5. Second poll after approval -> secret_token must be null (single-read burned!)
        mockMvc.perform(get("/api/v1/workers/register/{enrollmentId}/status", enrollmentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("APPROVED")))
            .andExpect(jsonPath("$.secret_token", nullValue()));
    }

    @Test
    @DisplayName("Should authenticate approved worker and exchange token for a 30-second ticket")
    void testAuthenticateApprovedWorker() throws Exception {
        String workerId = "worker-auth-003";
        WorkerEnrollmentService.RegisterRequest regReq = createSampleRequest(workerId);

        mockMvc.perform(post("/api/v1/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(regReq))
                .with(req -> {
                    req.setRemoteAddr("10.10.1.3");
                    return req;
                }))
            .andExpect(status().isAccepted());

        String secretToken = workerEnrollmentService.approveWorker(workerId, "cluster_operator", "production-us-east");

        // Authenticate worker with valid secret token
        WorkerEnrollmentService.AuthRequest authReq = new WorkerEnrollmentService.AuthRequest(
            workerId,
            secretToken,
            "wk-auth-host.internal",
            "linux",
            "x86_64",
            "1.0.0",
            regReq.allocatableCapacity()
        );

        mockMvc.perform(post("/api/v1/auth/worker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(authReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticket", startsWith("ws_tkt_")))
            .andExpect(jsonPath("$.ticket_ttl_seconds", is(30)))
            .andExpect(jsonPath("$.websocket_url", is("/ws/worker")))
            .andExpect(jsonPath("$.heartbeat_interval_ms", is(5000)))
            .andExpect(jsonPath("$.heartbeat_timeout_ms", is(15000)))
            .andExpect(jsonPath("$.assigned_cluster", is("production-us-east")));
    }

    @Test
    @DisplayName("Should reject authentication with invalid secret token (HTTP 401)")
    void testAuthenticateWorkerInvalidSecret() throws Exception {
        String workerId = "worker-invalid-004";
        WorkerEnrollmentService.RegisterRequest regReq = createSampleRequest(workerId);

        mockMvc.perform(post("/api/v1/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(regReq))
                .with(req -> {
                    req.setRemoteAddr("10.10.1.4");
                    return req;
                }))
            .andExpect(status().isAccepted());

        workerEnrollmentService.approveWorker(workerId, "admin", "us-east");

        // Authenticate with wrong secret token
        WorkerEnrollmentService.AuthRequest authReq = new WorkerEnrollmentService.AuthRequest(
            workerId,
            "wk_sec_incorrect_wrong_secret_1234567890abcdef1234567890abcdef",
            "wk-host",
            "linux",
            "x86_64",
            "1.0.0",
            null
        );

        mockMvc.perform(post("/api/v1/auth/worker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(authReq)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject authentication of quarantined unapproved worker (HTTP 403)")
    void testAuthenticateQuarantinedWorkerRejected() throws Exception {
        String workerId = "worker-quarantine-005";
        WorkerEnrollmentService.RegisterRequest regReq = createSampleRequest(workerId);

        mockMvc.perform(post("/api/v1/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(regReq))
                .with(req -> {
                    req.setRemoteAddr("10.10.1.5");
                    return req;
                }))
            .andExpect(status().isAccepted());

        // Still PENDING_APPROVAL
        WorkerEnrollmentService.AuthRequest authReq = new WorkerEnrollmentService.AuthRequest(
            workerId,
            "wk_sec_dummy_token",
            "wk-host",
            "linux",
            "x86_64",
            "1.0.0",
            null
        );

        mockMvc.perform(post("/api/v1/auth/worker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(authReq)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should enforce 5 req/min rate limit per IP on registration (HTTP 429)")
    void testRateLimiterRejectsExcessRequests() throws Exception {
        String ip = "10.99.99.1";

        for (int i = 1; i <= 5; i++) {
            WorkerEnrollmentService.RegisterRequest regReq = createSampleRequest("worker-rl-" + i);
            mockMvc.perform(post("/api/v1/workers/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(regReq))
                    .with(req -> {
                        req.setRemoteAddr(ip);
                        return req;
                    }))
                .andExpect(status().isAccepted());
        }

        // 6th request from the same IP MUST be rejected with HTTP 429
        WorkerEnrollmentService.RegisterRequest excessReq = createSampleRequest("worker-rl-6");
        mockMvc.perform(post("/api/v1/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(excessReq))
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                }))
            .andExpect(status().isTooManyRequests());
    }
}
