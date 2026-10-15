package com.legatus.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.security.OperatorSession;
import com.legatus.studio.transport.RestClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ServicesIntegrationTest {

    private static HttpServer server;
    private static int port;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestClient restClient;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Auth
        server.createContext("/api/v1/auth/operator/login", exchange -> {
            RestDto.LoginResponse response = new RestDto.LoginResponse("acc-tok", "ref-tok", 3600, "a1", "admin", "CLUSTER_ADMIN");
            sendJson(exchange, 200, response);
        });

        server.createContext("/api/v1/auth/studio/ticket", exchange -> {
            sendJson(exchange, 200, new RestDto.TicketResponse("st_tkt_12345", 60));
        });

        server.createContext("/api/v1/auth/operator/password", exchange -> {
            sendJson(exchange, 200, null);
        });

        // Workers & Enrollments
        server.createContext("/api/v1/workers", exchange -> {
            RestDto.WorkerDto w = new RestDto.WorkerDto("w1", "h1", "127.0.0.1", "linux", "x86_64", "ONLINE", 4, 8000000000L, 10.0, 2000000000L, 0, "2026-10-15T15:00:00Z");
            sendJson(exchange, 200, List.of(w));
        });

        server.createContext("/api/v1/admin/workers/enrollments", exchange -> {
            RestDto.EnrollmentDto e = new RestDto.EnrollmentDto("e1", "w2", "h2", "127.0.0.1", "linux", "x86_64", "AA:BB", "Tiger-Galaxy-77", 4, 8000000000L, "2026-10-15T15:00:00Z");
            sendJson(exchange, 200, List.of(e));
        });

        server.createContext("/api/v1/admin/workers/enrollments/e1/approve", exchange -> sendJson(exchange, 200, null));
        server.createContext("/api/v1/admin/workers/enrollments/e1/reject", exchange -> sendJson(exchange, 200, null));
        server.createContext("/api/v1/admin/workers/w1/drain", exchange -> sendJson(exchange, 200, null));
        server.createContext("/api/v1/admin/workers/w1/revoke", exchange -> sendJson(exchange, 200, null));

        // Tasks
        server.createContext("/api/v1/tasks", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, new RestDto.CreateTaskResponse("task-99", "QUEUED", "2026-10-15T15:00:00Z"));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/v1/tasks/task-99", exchange -> {
            sendJson(exchange, 200, new RestDto.TaskStatusResponse("task-99", "test-task", "COMPLETED", 0, "w1", 1000L, 2000L, 100L, "2026-10-15T15:00:00Z", "2026-10-15T15:00:01Z"));
        });

        server.createContext("/api/v1/tasks/task-99/abort", exchange -> sendJson(exchange, 200, null));

        // Solix Functions Catalog
        server.createContext("/api/solix-functions", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                RestDto.SolixFunctionDto fn = new RestDto.SolixFunctionDto(
                        java.util.UUID.randomUUID(), "matrix-mult", "1.0.0", "main", "RAW",
                        "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                        10000000L, 67108864L, 60000, "2026-10-15T15:00:00Z"
                );
                sendJson(exchange, 200, List.of(fn));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // Secrets
        server.createContext("/api/v1/admin/secrets", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, List.of(new RestDto.SecretDto("KEY1", "GLOBAL", "2026-10-15T15:00:00Z", "2026-10-15T15:00:00Z")));
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, null);
            }
        });
        server.createContext("/api/v1/admin/secrets/KEY1", exchange -> sendJson(exchange, 200, null));

        // Accounts
        server.createContext("/api/v1/admin/accounts", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, List.of(new RestDto.AccountDto("acc1", "operator1", "op@legatus.internal", "OPERATOR", "ACTIVE", "2026-10-15T15:00:00Z", "")));
            } else {
                sendJson(exchange, 200, null);
            }
        });
        server.createContext("/api/v1/admin/accounts/acc1", exchange -> sendJson(exchange, 200, null));
        server.createContext("/api/v1/admin/accounts/acc1/status", exchange -> sendJson(exchange, 200, null));
        server.createContext("/api/v1/admin/accounts/acc1/reset-password", exchange -> sendJson(exchange, 200, null));

        // Audit Logs
        server.createContext("/api/v1/admin/audit-logs", exchange -> {
            sendJson(exchange, 200, List.of(new RestDto.AuditLogEntryDto(1L, "ev1", "2026-10-15T15:00:00Z", "admin", "LOGIN", "AUTH", "acc1", "SUCCESS", "h1", "genesis")));
        });
        server.createContext("/api/v1/admin/audit-logs/verify", exchange -> {
            sendJson(exchange, 200, new RestDto.AuditVerifyResponse(true, 100L, "genesis_hash", "head_hash", null));
        });

        server.setExecutor(null);
        server.start();
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        restClient = new RestClient("http://127.0.0.1:" + port);
        OperatorSession.getInstance().setSession(
                "acc-tok", "ref-tok", Instant.now().plus(1, ChronoUnit.HOURS),
                "admin", "a1", "CLUSTER_ADMIN"
        );
    }

    @Test
    void testAuthService() throws Exception {
        AuthService authService = new AuthService(restClient);
        var loginResp = authService.login("admin", "pass").get();
        assertEquals("acc-tok", loginResp.access_token());

        String ticket = authService.acquireStudioTicket().get();
        assertEquals("st_tkt_12345", ticket);

        assertDoesNotThrow(() -> authService.changePassword("old", "new").get());
    }

    @Test
    void testWorkerService() throws Exception {
        WorkerService workerService = new WorkerService(restClient);
        var workers = workerService.getWorkers().get();
        assertEquals(1, workers.size());
        assertEquals("w1", workers.get(0).worker_id());

        var enrollments = workerService.getEnrollments().get();
        assertEquals(1, enrollments.size());
        assertEquals("e1", enrollments.get(0).enrollment_id());

        assertDoesNotThrow(() -> workerService.approveEnrollment("e1", "24h").get());
        assertDoesNotThrow(() -> workerService.rejectEnrollment("e1", "Unauthorized", true).get());
        assertDoesNotThrow(() -> workerService.drainWorker("w1").get());
        assertDoesNotThrow(() -> workerService.revokeWorker("w1").get());
    }

    @Test
    void testTaskService() throws Exception {
        TaskService taskService = new TaskService(restClient);
        var createResp = taskService.createTask("task-name", "sha256...", "main", 1000L, 10000L, null).get();
        assertEquals("task-99", createResp.task_id());

        var task = taskService.getTask("task-99").get();
        assertEquals("COMPLETED", task.status());

        assertDoesNotThrow(() -> taskService.abortTask("task-99", "Operator cancelled").get());

        // Test structured arguments & constraints
        var parameterizedResp = taskService.createTask(
                "parameterized-task", "sha256...", "main",
                5000000L, 33554432L, 30000, "HIGH",
                java.util.Map.of("matrix_size", 1024)
        ).get();
        assertEquals("task-99", parameterizedResp.task_id());
    }

    @Test
    void testBytecodeCatalogService() throws Exception {
        BytecodeService bytecodeService = new BytecodeService(restClient);
        var catalog = bytecodeService.getCatalogedFunctions().get();
        assertEquals(1, catalog.size());
        assertEquals("matrix-mult", catalog.get(0).name());
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", catalog.get(0).effectiveSha256());
    }

    @Test
    void testSecretsService() throws Exception {
        SecretsService secretsService = new SecretsService(restClient);
        var secrets = secretsService.getSecrets().get();
        assertEquals(1, secrets.size());
        assertEquals("KEY1", secrets.get(0).key());

        assertDoesNotThrow(() -> secretsService.createSecret("NEW_KEY", "GLOBAL", "val").get());
        assertDoesNotThrow(() -> secretsService.deleteSecret("KEY1").get());
    }

    @Test
    void testUserManagementService() throws Exception {
        UserManagementService userService = new UserManagementService(restClient);
        var accounts = userService.getAccounts().get();
        assertEquals(1, accounts.size());
        assertEquals("acc1", accounts.get(0).account_id());

        assertDoesNotThrow(() -> userService.createAccount("user2", "u2@legatus.internal", "pw", "OPERATOR").get());
        assertDoesNotThrow(() -> userService.updateAccount("acc1", "updated@legatus.internal", "AUDITOR").get());
        assertDoesNotThrow(() -> userService.updateAccountStatus("acc1", "SUSPENDED").get());
        assertDoesNotThrow(() -> userService.resetPassword("acc1", "new_password").get());
        assertDoesNotThrow(() -> userService.deleteAccount("acc1").get());
    }

    @Test
    void testAuditLogService() throws Exception {
        AuditLogService auditService = new AuditLogService(restClient);
        var logs = auditService.getAuditLogs().get();
        assertEquals(1, logs.size());
        assertEquals(1L, logs.get(0).sequence_number());

        var verify = auditService.verifyHashChain().get();
        assertTrue(verify.valid());
        assertEquals(100L, verify.records_verified());
    }
}
