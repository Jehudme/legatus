package com.legatus.studio.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.security.OperatorSession;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class RestClientTest {

    private static HttpServer server;
    private static int port;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestClient restClient;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/v1/auth/operator/login", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    RestDto.LoginResponse response = new RestDto.LoginResponse(
                            "mock-access-token", "mock-refresh-token", 3600, "acc-1", "admin", "CLUSTER_ADMIN"
                    );
                    byte[] bytes = MAPPER.writeValueAsBytes(response);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            }
        });

        server.createContext("/api/v1/workers", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    byte[] err = MAPPER.writeValueAsBytes(new RestDto.ApiErrorResponse("UNAUTHORIZED", "Missing bearer token", System.currentTimeMillis()));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(401, err.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(err);
                    }
                    return;
                }

                RestDto.WorkerDto w1 = new RestDto.WorkerDto(
                        "w-1", "host-1", "127.0.0.1", "linux", "x86_64", "ONLINE",
                        8, 16000000000L, 25.5, 4000000000L, 2, "2026-10-15T15:00:00Z"
                );
                byte[] bytes = MAPPER.writeValueAsBytes(List.of(w1));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        server.createContext("/api/v1/tasks/missing", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] err = MAPPER.writeValueAsBytes(new RestDto.ApiErrorResponse("TASK_NOT_FOUND", "Task does not exist", System.currentTimeMillis()));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, err.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(err);
                }
            }
        });

        server.setExecutor(null);
        server.start();
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
        OperatorSession.getInstance().clearSession();
    }

    @Test
    void testLoginPost() throws Exception {
        RestDto.LoginRequest request = new RestDto.LoginRequest("admin", "secret");
        RestDto.LoginResponse response = restClient.post("/api/v1/auth/operator/login", request, RestDto.LoginResponse.class).get();

        assertNotNull(response);
        assertEquals("mock-access-token", response.access_token());
        assertEquals("CLUSTER_ADMIN", response.role());
    }

    @Test
    void testDeserializeJsonWithUnknownFields() throws Exception {
        String json = """
        {
          "access_token": "tok123",
          "expires_in_seconds": 86400,
          "studio_ws_ticket": "st_tkt_12345",
          "studio_ws_url": "/ws/studio",
          "account_id": "acc-uuid",
          "username": "admin",
          "role": "CLUSTER_ADMIN",
          "extra_unknown_future_field": "some-value",
          "nested_unknown_object": { "key": "value" }
        }
        """;
        var parsed = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(json, RestDto.LoginResponse.class);

        assertNotNull(parsed);
        assertEquals("tok123", parsed.access_token());
        assertEquals("st_tkt_12345", parsed.studio_ws_ticket());
        assertEquals("/ws/studio", parsed.studio_ws_url());
        assertEquals("admin", parsed.username());
    }

    @Test
    void testGetWorkersWithBearerAuth() throws Exception {
        // Authenticate session
        OperatorSession.getInstance().setSession(
                "mock-access-token", "mock-refresh-token",
                Instant.now().plus(1, ChronoUnit.HOURS),
                "admin", "acc-1", "CLUSTER_ADMIN"
        );

        List<RestDto.WorkerDto> workers = restClient.get("/api/v1/workers", new TypeReference<List<RestDto.WorkerDto>>() {}).get();
        assertNotNull(workers);
        assertEquals(1, workers.size());
        assertEquals("w-1", workers.get(0).worker_id());
        assertEquals("ONLINE", workers.get(0).state());
    }

    @Test
    void testUnauthorizedWithoutToken() {
        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            restClient.get("/api/v1/workers", new TypeReference<List<RestDto.WorkerDto>>() {}).get();
        });

        assertTrue(ex.getCause() instanceof ApiException);
        ApiException apiEx = (ApiException) ex.getCause();
        assertEquals(401, apiEx.getStatusCode());
        assertNotNull(apiEx.getErrorResponse());
        assertEquals("UNAUTHORIZED", apiEx.getErrorResponse().error_code());
    }

    @Test
    void testNotFoundApiException() {
        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            restClient.get("/api/v1/tasks/missing", RestDto.TaskStatusResponse.class).get();
        });

        assertTrue(ex.getCause() instanceof ApiException);
        ApiException apiEx = (ApiException) ex.getCause();
        assertEquals(404, apiEx.getStatusCode());
        assertNotNull(apiEx.getErrorResponse());
        assertEquals("TASK_NOT_FOUND", apiEx.getErrorResponse().error_code());
    }
}
