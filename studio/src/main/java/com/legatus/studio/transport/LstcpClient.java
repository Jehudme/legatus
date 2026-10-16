package com.legatus.studio.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legatus.studio.protocol.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Full-duplex WebSocket client implementing the Legatus Studio Telemetry & Control Protocol (LSTCP).
 * Conforms to docs/specs/protocols/studio-wire.md.
 */
public class LstcpClient implements WebSocket.Listener {

    public enum State {
        DISCONNECTED,
        CONNECTING,
        NEGOTIATING,
        STREAMING
    }

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long DEFAULT_HEARTBEAT_MS = 10_000;
    private static final long DEAD_MAN_TIMEOUT_MS = 30_000;

    private final String wsBaseUrl;
    private final boolean trustAllCerts;
    private final Random random = new Random();

    private volatile State state = State.DISCONNECTED;
    private volatile WebSocket webSocket;
    private volatile String currentTicket;
    private volatile long lastReceivedTimestamp = System.currentTimeMillis();
    private volatile long lastHeartbeatSentTimestamp = 0L;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;

    private final ScheduledExecutorService backgroundExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "lstcp-client");
        t.setDaemon(true);
        return t;
    });

    private final StringBuilder messageBuffer = new StringBuilder();

    // Event handlers (Multicast event listener lists)
    private final java.util.List<Consumer<State>> stateListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<SessionAckPayload>> sessionAckListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<Double>> rttListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<ClusterMetricTickPayload>> metricTickListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<WorkerStateChangedPayload>> workerStateListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<EnrollmentRequestedPayload>> enrollmentListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<TaskLifecyclePayload>> taskLifecycleListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<TaskLogChunkPayload>> taskLogChunkListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<TaskFailedAlertPayload>> taskFailedAlertListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Consumer<RestDto.UserStateChangedPayload>> userStateListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public LstcpClient(String serverUrl, boolean trustAllCerts) {
        this.wsBaseUrl = normalizeWsUrl(serverUrl);
        this.trustAllCerts = trustAllCerts;

        // Start dead-man's switch monitor
        backgroundExecutor.scheduleWithFixedDelay(this::checkDeadManTimeout, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    private String normalizeWsUrl(String url) {
        Objects.requireNonNull(url, "serverUrl must not be null");
        String trimmed = url.trim();
        if (trimmed.startsWith("https://")) {
            trimmed = "wss://" + trimmed.substring(8);
        } else if (trimmed.startsWith("http://")) {
            trimmed = "ws://" + trimmed.substring(7);
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/ws/studio")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/ws/studio".length());
        }
        return trimmed;
    }

    public synchronized CompletableFuture<Void> connect(String ticket) {
        this.currentTicket = ticket;
        this.shouldReconnect = true;
        this.reconnectAttempts = 0;
        return initiateConnection();
    }

    private synchronized CompletableFuture<Void> initiateConnection() {
        if (state == State.CONNECTING || state == State.NEGOTIATING || state == State.STREAMING) {
            return CompletableFuture.completedFuture(null);
        }

        transitionState(State.CONNECTING);
        lastReceivedTimestamp = System.currentTimeMillis();

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor());

        if (trustAllCerts) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new InsecureTrustManager()}, new SecureRandom());
                clientBuilder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException ignored) {}
        }

        String fullWsUri = wsBaseUrl + "/ws/studio?ticket=" + currentTicket;
        return clientBuilder.build().newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(URI.create(fullWsUri), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.reconnectAttempts = 0;
                    transitionState(State.NEGOTIATING);
                    sendSessionInit();
                    scheduleHeartbeat();
                })
                .exceptionally(ex -> {
                    transitionState(State.DISCONNECTED);
                    scheduleReconnect();
                    return null;
                });
    }

    private void transitionState(State newState) {
        this.state = newState;
        for (var l : stateListeners) {
            try { l.accept(newState); } catch (Exception ignored) {}
        }
    }

    private void sendSessionInit() {
        Frame<SessionInitPayload> initFrame = Frame.create("STUDIO_SESSION_INIT", SessionInitPayload.createDefault());
        sendFrame(initFrame);
    }

    public synchronized void sendFrame(Frame<?> frame) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                String json = MAPPER.writeValueAsString(frame);
                webSocket.sendText(json, true);
            } catch (Exception e) {
                // Ignore or log send error
            }
        }
    }

    public void subscribeTaskLogs(String taskId) {
        Frame<TaskLogSubscriptionPayload> frame = Frame.create("SUBSCRIBE_TASK_LOGS", new TaskLogSubscriptionPayload(taskId));
        sendFrame(frame);
    }

    public void unsubscribeTaskLogs(String taskId) {
        Frame<TaskLogSubscriptionPayload> frame = Frame.create("UNSUBSCRIBE_TASK_LOGS", new TaskLogSubscriptionPayload(taskId));
        sendFrame(frame);
    }

    public synchronized void sendHeartbeatNow() {
        if (state == State.STREAMING || state == State.NEGOTIATING) {
            lastHeartbeatSentTimestamp = System.currentTimeMillis();
            Frame<HeartbeatPayload.Request> hb = Frame.create("STUDIO_HEARTBEAT", new HeartbeatPayload.Request(lastHeartbeatSentTimestamp));
            sendFrame(hb);
        }
    }

    private void scheduleHeartbeat() {
        if (state != State.STREAMING && state != State.NEGOTIATING) {
            return;
        }

        long jitter = (random.nextInt(2000) - 1000); // +/- 1000ms
        long delay = Math.max(5000, DEFAULT_HEARTBEAT_MS + jitter);

        backgroundExecutor.schedule(() -> {
            if (state == State.STREAMING || state == State.NEGOTIATING) {
                sendHeartbeatNow();
                scheduleHeartbeat();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void checkDeadManTimeout() {
        if (state == State.STREAMING || state == State.NEGOTIATING) {
            long elapsed = System.currentTimeMillis() - lastReceivedTimestamp;
            if (elapsed > DEAD_MAN_TIMEOUT_MS) {
                disconnectAndReconnect();
            }
        }
    }

    private synchronized void disconnectAndReconnect() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(1001, "Dead-man timeout exceeded");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        transitionState(State.DISCONNECTED);
        if (shouldReconnect) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) {
            return;
        }

        reconnectAttempts++;
        long baseBackoff = Math.min(30_000L, 1000L * (1L << Math.min(reconnectAttempts - 1, 5)));
        double jitter = 0.8 + (random.nextDouble() * 0.4); // 0.8 to 1.2 (+/- 20%)
        long backoff = Math.max(500L, (long) (baseBackoff * jitter));

        backgroundExecutor.schedule(() -> {
            if (shouldReconnect && state == State.DISCONNECTED) {
                initiateConnection();
            }
        }, backoff, TimeUnit.MILLISECONDS);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            String fullMessage = messageBuffer.toString();
            messageBuffer.setLength(0);
            processMessage(fullMessage);
        }
        if (webSocket != null) {
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
        return null;
    }

    void processMessage(String text) {
        lastReceivedTimestamp = System.currentTimeMillis();
        try {
            JsonNode root = MAPPER.readTree(text);
            String type = root.path("type").asText();
            JsonNode payload = root.path("payload");

            switch (type) {
                case "STUDIO_SESSION_ACK" -> {
                    transitionState(State.STREAMING);
                    SessionAckPayload ack = MAPPER.treeToValue(payload, SessionAckPayload.class);
                    for (var l : sessionAckListeners) {
                        try { l.accept(ack); } catch (Exception ignored) {}
                    }
                    sendHeartbeatNow();
                }
                case "STUDIO_HEARTBEAT_ACK" -> {
                    if (lastHeartbeatSentTimestamp > 0) {
                        double rtt = (double) (System.currentTimeMillis() - lastHeartbeatSentTimestamp);
                        double finalRtt = Math.max(0.1, rtt);
                        for (var l : rttListeners) {
                            try { l.accept(finalRtt); } catch (Exception ignored) {}
                        }
                    }
                }
                case "EVENT_CLUSTER_METRIC_TICK" -> {
                    ClusterMetricTickPayload tick = MAPPER.treeToValue(payload, ClusterMetricTickPayload.class);
                    for (var l : metricTickListeners) {
                        try { l.accept(tick); } catch (Exception ignored) {}
                    }
                }
                case "EVENT_WORKER_STATE_CHANGED" -> {
                    WorkerStateChangedPayload worker = MAPPER.treeToValue(payload, WorkerStateChangedPayload.class);
                    for (var l : workerStateListeners) {
                        try { l.accept(worker); } catch (Exception ignored) {}
                    }
                }
                case "EVENT_ENROLLMENT_REQUESTED" -> {
                    EnrollmentRequestedPayload enr = MAPPER.treeToValue(payload, EnrollmentRequestedPayload.class);
                    for (var l : enrollmentListeners) {
                        try { l.accept(enr); } catch (Exception ignored) {}
                    }
                }
                case "EVENT_TASK_LIFECYCLE" -> {
                    TaskLifecyclePayload task = MAPPER.treeToValue(payload, TaskLifecyclePayload.class);
                    for (var l : taskLifecycleListeners) {
                        try { l.accept(task); } catch (Exception ignored) {}
                    }
                }
                case "EVENT_TASK_LOG_CHUNK" -> {
                    TaskLogChunkPayload chunk = MAPPER.treeToValue(payload, TaskLogChunkPayload.class);
                    for (var l : taskLogChunkListeners) {
                        try { l.accept(chunk); } catch (Exception ignored) {}
                    }
                }
                case "EVENT_TASK_FAILED_ALERT" -> {
                    TaskFailedAlertPayload alert = MAPPER.treeToValue(payload, TaskFailedAlertPayload.class);
                    for (var l : taskFailedAlertListeners) {
                        try { l.accept(alert); } catch (Exception ignored) {}
                    }
                }
                case "EVENT_USER_STATE_CHANGED" -> {
                    RestDto.UserStateChangedPayload user = MAPPER.treeToValue(payload, RestDto.UserStateChangedPayload.class);
                    for (var l : userStateListeners) {
                        try { l.accept(user); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed frames
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        transitionState(State.DISCONNECTED);
        if (shouldReconnect) {
            scheduleReconnect();
        }
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        transitionState(State.DISCONNECTED);
        if (shouldReconnect) {
            scheduleReconnect();
        }
    }

    public synchronized void disconnect() {
        this.shouldReconnect = false;
        if (webSocket != null) {
            try {
                webSocket.sendClose(1000, "User initiated disconnect");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        transitionState(State.DISCONNECTED);
    }

    public State getState() {
        return state;
    }

    public void addStateListener(Consumer<State> listener) { if (listener != null) this.stateListeners.add(listener); }
    public void removeStateListener(Consumer<State> listener) { if (listener != null) this.stateListeners.remove(listener); }
    public void setStateListener(Consumer<State> listener) { addStateListener(listener); }

    public void addSessionAckListener(Consumer<SessionAckPayload> listener) { if (listener != null) this.sessionAckListeners.add(listener); }
    public void removeSessionAckListener(Consumer<SessionAckPayload> listener) { if (listener != null) this.sessionAckListeners.remove(listener); }
    public void setSessionAckListener(Consumer<SessionAckPayload> listener) { addSessionAckListener(listener); }

    public void addRttListener(Consumer<Double> listener) { if (listener != null) this.rttListeners.add(listener); }
    public void removeRttListener(Consumer<Double> listener) { if (listener != null) this.rttListeners.remove(listener); }
    public void setRttListener(Consumer<Double> listener) { addRttListener(listener); }

    public void addMetricTickListener(Consumer<ClusterMetricTickPayload> listener) { if (listener != null) this.metricTickListeners.add(listener); }
    public void removeMetricTickListener(Consumer<ClusterMetricTickPayload> listener) { if (listener != null) this.metricTickListeners.remove(listener); }
    public void setMetricTickListener(Consumer<ClusterMetricTickPayload> listener) { addMetricTickListener(listener); }

    public void addWorkerStateListener(Consumer<WorkerStateChangedPayload> listener) { if (listener != null) this.workerStateListeners.add(listener); }
    public void removeWorkerStateListener(Consumer<WorkerStateChangedPayload> listener) { if (listener != null) this.workerStateListeners.remove(listener); }
    public void setWorkerStateListener(Consumer<WorkerStateChangedPayload> listener) { addWorkerStateListener(listener); }

    public void addEnrollmentListener(Consumer<EnrollmentRequestedPayload> listener) { if (listener != null) this.enrollmentListeners.add(listener); }
    public void removeEnrollmentListener(Consumer<EnrollmentRequestedPayload> listener) { if (listener != null) this.enrollmentListeners.remove(listener); }
    public void setEnrollmentListener(Consumer<EnrollmentRequestedPayload> listener) { addEnrollmentListener(listener); }

    public void addTaskLifecycleListener(Consumer<TaskLifecyclePayload> listener) { if (listener != null) this.taskLifecycleListeners.add(listener); }
    public void removeTaskLifecycleListener(Consumer<TaskLifecyclePayload> listener) { if (listener != null) this.taskLifecycleListeners.remove(listener); }
    public void setTaskLifecycleListener(Consumer<TaskLifecyclePayload> listener) { addTaskLifecycleListener(listener); }

    public void addTaskLogChunkListener(Consumer<TaskLogChunkPayload> listener) { if (listener != null) this.taskLogChunkListeners.add(listener); }
    public void removeTaskLogChunkListener(Consumer<TaskLogChunkPayload> listener) { if (listener != null) this.taskLogChunkListeners.remove(listener); }
    public void setTaskLogChunkListener(Consumer<TaskLogChunkPayload> listener) { addTaskLogChunkListener(listener); }

    public void addTaskFailedAlertListener(Consumer<TaskFailedAlertPayload> listener) { if (listener != null) this.taskFailedAlertListeners.add(listener); }
    public void removeTaskFailedAlertListener(Consumer<TaskFailedAlertPayload> listener) { if (listener != null) this.taskFailedAlertListeners.remove(listener); }
    public void setTaskFailedAlertListener(Consumer<TaskFailedAlertPayload> listener) { addTaskFailedAlertListener(listener); }

    public void addUserStateListener(Consumer<RestDto.UserStateChangedPayload> listener) { if (listener != null) this.userStateListeners.add(listener); }
    public void removeUserStateListener(Consumer<RestDto.UserStateChangedPayload> listener) { if (listener != null) this.userStateListeners.remove(listener); }
    public void setUserStateListener(Consumer<RestDto.UserStateChangedPayload> listener) { addUserStateListener(listener); }

    private static class InsecureTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }
}
