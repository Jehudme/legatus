package com.legatus.studio.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.security.OperatorSession;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * High-performance HTTP/2 REST client isolated onto Java 21 Virtual Threads.
 * Automatically injects Bearer JWT authentication and maps JSON envelopes.
 */
public class RestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final HttpClient httpClient;

    public RestClient(String baseUrl) {
        this(baseUrl, false);
    }

    public RestClient(String baseUrl, boolean trustAllCerts) {
        this.baseUrl = normalizeBaseUrl(baseUrl);

        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(DEFAULT_TIMEOUT);

        if (trustAllCerts) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new InsecureTrustManager()}, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to initialize insecure SSL context", e);
            }
        }

        this.httpClient = builder.build();
    }

    private String normalizeBaseUrl(String url) {
        Objects.requireNonNull(url, "baseUrl must not be null");
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public <T> CompletableFuture<T> get(String path, Class<T> responseClass) {
        HttpRequest request = buildRequest(path, "GET", null);
        return sendAsync(request, responseClass);
    }

    public <T> CompletableFuture<T> get(String path, TypeReference<T> responseType) {
        HttpRequest request = buildRequest(path, "GET", null);
        return sendAsync(request, responseType);
    }

    public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseClass) {
        HttpRequest request = buildRequest(path, "POST", body);
        return sendAsync(request, responseClass);
    }

    public <T> CompletableFuture<T> put(String path, Object body, Class<T> responseClass) {
        HttpRequest request = buildRequest(path, "PUT", body);
        return sendAsync(request, responseClass);
    }

    public <T> CompletableFuture<T> patch(String path, Object body, Class<T> responseClass) {
        HttpRequest request = buildRequest(path, "PATCH", body);
        return sendAsync(request, responseClass);
    }

    public <T> CompletableFuture<T> delete(String path, Class<T> responseClass) {
        HttpRequest request = buildRequest(path, "DELETE", null);
        return sendAsync(request, responseClass);
    }

    private HttpRequest buildRequest(String path, String method, Object body) {
        String fullUri = baseUrl + (path.startsWith("/") ? path : "/" + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUri))
                .timeout(DEFAULT_TIMEOUT)
                .header("Accept", "application/json");

        String token = OperatorSession.getInstance().getAccessToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            try {
                byte[] jsonBytes = MAPPER.writeValueAsBytes(body);
                publisher = HttpRequest.BodyPublishers.ofByteArray(jsonBytes);
                builder.header("Content-Type", "application/json");
            } catch (IOException e) {
                throw new RuntimeException("Failed to serialize request body to JSON", e);
            }
        }

        builder.method(method, publisher);
        return builder.build();
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseClass) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> handleResponse(response, responseClass, null));
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, TypeReference<T> responseType) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> handleResponse(response, null, responseType));
    }

    private <T> T handleResponse(HttpResponse<byte[]> response, Class<T> responseClass, TypeReference<T> responseType) {
        int status = response.statusCode();
        byte[] body = response.body();

        if (status >= 200 && status < 300) {
            if (responseClass != null && responseClass.equals(Void.class)) {
                return null;
            }
            if (body == null || body.length == 0) {
                return null;
            }
            try {
                if (responseClass != null) {
                    return MAPPER.readValue(body, responseClass);
                } else {
                    return MAPPER.readValue(body, responseType);
                }
            } catch (IOException e) {
                throw new ApiException(status, "Failed to deserialize JSON response: " + e.getMessage(), null);
            }
        }

        // Non-2xx response: attempt to parse ApiErrorResponse
        RestDto.ApiErrorResponse errorEnvelope = null;
        if (body != null && body.length > 0) {
            try {
                errorEnvelope = MAPPER.readValue(body, RestDto.ApiErrorResponse.class);
            } catch (Exception ignored) {}
        }

        String msg = errorEnvelope != null ? errorEnvelope.message() : "HTTP request failed with status: " + status;
        throw new ApiException(status, msg, errorEnvelope);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    private static class InsecureTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }
}
