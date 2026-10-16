package com.legatus.studio.viewmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legatus.studio.config.ConfigStore;
import com.legatus.studio.config.StudioConfig;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.service.AuditLogService;
import com.legatus.studio.service.SecretsService;
import com.legatus.studio.service.UserManagementService;
import com.legatus.studio.transport.RestClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminAndSettingsViewModelTest {

    private static HttpServer server;
    private static int port;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Secrets
        server.createContext("/api/v1/admin/secrets", ex -> {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 200, List.of(new RestDto.SecretDto("JWT_KEY", "GLOBAL", "2026-10-15T15:00:00Z", "2026-10-15T15:00:00Z")));
            } else {
                sendJson(ex, 200, null);
            }
        });
        server.createContext("/api/v1/admin/secrets/JWT_KEY", ex -> sendJson(ex, 200, null));

        // Accounts
        server.createContext("/api/v1/admin/accounts", ex -> {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 200, List.of(new RestDto.AccountDto("acc-1", "user1", "u1@corp", "OPERATOR", "ACTIVE", "2026-10-15T15:00:00Z", "")));
            } else {
                sendJson(ex, 200, null);
            }
        });
        server.createContext("/api/v1/admin/accounts/acc-1/status", ex -> sendJson(ex, 200, null));
        server.createContext("/api/v1/admin/accounts/acc-1/reset-password", ex -> sendJson(ex, 200, null));
        server.createContext("/api/v1/admin/accounts/acc-1", ex -> sendJson(ex, 200, null));

        // Audit logs
        server.createContext("/api/v1/admin/audit-logs", ex -> {
            sendJson(ex, 200, List.of(new RestDto.AuditLogEntryDto(1L, "ev1", "2026-10-15T15:00:00Z", "admin", "TOKEN_ISSUED", "AUTH", "acc-1", "SUCCESS", "h1", "genesis")));
        });
        server.createContext("/api/v1/admin/audit-logs/verify", ex -> {
            sendJson(ex, 200, new RestDto.AuditVerifyResponse(true, 50L, "genesis", "head", null));
        });

        server.setExecutor(null);
        server.start();
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, Object body) throws IOException {
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
        if (server != null) server.stop(0);
    }

    @Test
    void testSecretsViewModel() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        SecretsService service = new SecretsService(client);
        SecretsViewModel vm = new SecretsViewModel(service);

        vm.refreshSecrets().get();
        assertEquals(1, vm.getSecrets().size());
        assertEquals("JWT_KEY", vm.getSecrets().get(0).getKey());

        vm.setSelectedSecret(vm.getSecrets().get(0));
        boolean deleted = vm.deleteSelectedSecret().get();
        assertTrue(deleted);
        assertEquals(0, vm.getSecrets().size());
    }

    @Test
    void testUserManagementViewModel() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        UserManagementService service = new UserManagementService(client);
        UserManagementViewModel vm = new UserManagementViewModel(service);

        vm.refreshAccounts().get();
        assertEquals(1, vm.getAccounts().size());

        vm.setSelectedAccount(vm.getAccounts().get(0));
        boolean toggled = vm.toggleStatus().get();
        assertTrue(toggled);
        assertEquals("SUSPENDED", vm.getSelectedAccount().getStatus());

        boolean usernameUpdated = vm.updateUsername("user1_updated").get();
        assertTrue(usernameUpdated);
        assertEquals("user1_updated", vm.getSelectedAccount().getUsername());

        boolean emailUpdated = vm.updateEmail("u1_updated@corp.internal").get();
        assertTrue(emailUpdated);
        assertEquals("u1_updated@corp.internal", vm.getSelectedAccount().getEmail());

        boolean roleUpdated = vm.updateRole("CLUSTER_ADMIN").get();
        assertTrue(roleUpdated);
        assertEquals("CLUSTER_ADMIN", vm.getSelectedAccount().getRole());

        // Password policy test: < 12 characters rejected
        boolean resetShort = vm.resetPassword("short").get();
        assertFalse(resetShort);

        // Valid password reset
        boolean resetValid = vm.resetPassword("ComplexAdminPass2026!").get();
        assertTrue(resetValid);
    }

    @Test
    void testAuditTrailViewModel() throws Exception {
        RestClient client = new RestClient("http://127.0.0.1:" + port);
        AuditLogService service = new AuditLogService(client);
        AuditTrailViewModel vm = new AuditTrailViewModel(service);

        vm.refreshAuditLogs().get();
        assertEquals(1, vm.getFilteredAuditLogs().size());

        boolean valid = vm.verifyChainIntegrity().get();
        assertTrue(valid);
        assertEquals("VALID_CHAIN", vm.verificationStatusProperty().get());
    }

    @Test
    void testSettingsViewModel() {
        Path configPath = tempDir.resolve("studio.json");
        ConfigStore store = new ConfigStore(configPath);
        SettingsViewModel vm = new SettingsViewModel(store);

        assertEquals(10000, vm.connectionTimeoutMsProperty().get());
        vm.connectionTimeoutMsProperty().set(20000);
        assertTrue(vm.saveSettings());

        ConfigStore reloaded = new ConfigStore(configPath);
        assertEquals(20000, reloaded.load().connectionTimeoutMs());
    }
}
