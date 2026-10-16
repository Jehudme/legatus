package com.legatus.studio.service;

import com.legatus.studio.model.QuarantineEnrollment;
import com.legatus.studio.model.WorkerNode;
import com.legatus.studio.security.OperatorSession;
import com.legatus.studio.transport.LogStreamBuffer;
import com.legatus.studio.transport.LstcpClient;
import com.legatus.studio.transport.RestClient;
import com.legatus.studio.viewmodel.MainViewModel;
import javafx.application.Platform;

/**
 * Singleton managing runtime application services, active connections, and telemetry orchestration.
 */
public class StudioContext {

    private static final StudioContext INSTANCE = new StudioContext();

    private RestClient restClient;
    private AuthService authService;
    private WorkerService workerService;
    private TaskService taskService;
    private BytecodeService bytecodeService;
    private SecretsService secretsService;
    private UserManagementService userManagementService;
    private AuditLogService auditLogService;
    private LogStreamBuffer logStreamBuffer;
    private LstcpClient lstcpClient;
    private TelemetryService telemetryService;
    private com.legatus.studio.viewmodel.FunctionsViewModel functionsViewModel;
    private final MainViewModel mainViewModel = new MainViewModel();

    private StudioContext() {
        this.logStreamBuffer = new LogStreamBuffer();
    }

    public static StudioContext getInstance() {
        return INSTANCE;
    }

    public synchronized void initSession(String serverUrl, boolean trustAllCerts) {
        this.restClient = new RestClient(serverUrl, trustAllCerts);
        this.authService = new AuthService(restClient);
        this.workerService = new WorkerService(restClient);
        this.taskService = new TaskService(restClient);
        this.bytecodeService = new BytecodeService(restClient);
        this.secretsService = new SecretsService(restClient);
        this.userManagementService = new UserManagementService(restClient);
        this.auditLogService = new AuditLogService(restClient);

        this.functionsViewModel = new com.legatus.studio.viewmodel.FunctionsViewModel(this.bytecodeService, this.taskService);

        // Derive ws/wss URL
        String trimmed = serverUrl.trim();
        String wsBase;
        if (trimmed.startsWith("https://")) {
            wsBase = "wss://" + trimmed.substring(8);
        } else if (trimmed.startsWith("http://")) {
            wsBase = "ws://" + trimmed.substring(7);
        } else {
            wsBase = "ws://" + trimmed;
        }
        if (wsBase.endsWith("/")) {
            wsBase = wsBase.substring(0, wsBase.length() - 1);
        }
        String wsUrl = wsBase + "/ws/studio";

        this.logStreamBuffer.appendLine(String.format("[%s] [SYSTEM] Initializing Legatus Studio session with %s",
                java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), serverUrl));

        this.lstcpClient = new LstcpClient(wsUrl, trustAllCerts);
        this.telemetryService = new TelemetryService(lstcpClient, true, logStreamBuffer);

        this.lstcpClient.setStateListener(state -> {
            FxAsync.runOnFxThread(() -> mainViewModel.updateConnectionState(state));
            logStreamBuffer.appendLine(String.format("[%s] [SYSTEM] Telemetry streaming state: %s",
                    java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), state));
        });

        this.lstcpClient.setSessionAckListener(ack -> {
            logStreamBuffer.appendLine(String.format("[%s] [SYSTEM] Connected to Legatus cluster (session=%s, heartbeat=%dms)",
                    java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                    ack.session_id(), ack.heartbeat_interval_ms()));
        });

        this.lstcpClient.setTaskLogChunkListener(chunk -> {
            if (chunk != null && chunk.log_chunk() != null) {
                logStreamBuffer.appendChunk(chunk.log_chunk());
            }
        });

        // If studio WebSocket ticket is present from login, connect immediately
        String ticket = OperatorSession.getInstance().getStudioWsTicket();
        if (ticket != null && !ticket.isBlank()) {
            this.lstcpClient.connect(ticket);
        }

        // Fetch initial data snapshot via REST to populate observable models
        refreshInitialData();
    }

    public void refreshInitialData() {
        if (functionsViewModel != null) {
            functionsViewModel.loadCatalogedFunctions();
        }
        if (workerService == null || telemetryService == null) return;

        // Fetch workers snapshot
        workerService.getWorkers().thenAccept(workers -> {
            FxAsync.runOnFxThread(() -> {
                for (var w : workers) {
                    WorkerNode node = new WorkerNode(
                        w.worker_id(),
                        w.friendly_name(),
                        w.hostname(),
                        w.source_ip(),
                        w.os(),
                        w.architecture(),
                        w.state(),
                        w.active() != null ? w.active() : true,
                        w.allocatable_cores(),
                        w.max_memory_bytes(),
                        w.target_cpu_pct() != null ? w.target_cpu_pct() : 75.0,
                        w.target_ram_pct() != null ? w.target_ram_pct() : 80.0,
                        w.max_concurrent_tasks() != null ? w.max_concurrent_tasks() : 16,
                        w.auto_renew_lease() != null ? w.auto_renew_lease() : true,
                        w.remaining_lease_sec() != null ? w.remaining_lease_sec() : 86400L,
                        w.uptime_seconds() != null ? w.uptime_seconds() : 0L,
                        w.cpu_load_pct(),
                        w.memory_used_bytes(),
                        w.active_tasks_count(),
                        w.last_heartbeat(),
                        w.created_at()
                    );
                    telemetryService.getWorkersMap().put(node.getWorkerId(), node);
                    if (telemetryService.getWorkersList().stream().noneMatch(x -> x.getWorkerId().equals(node.getWorkerId()))) {
                        telemetryService.getWorkersList().add(node);
                    }
                }
            });
        }).exceptionally(ex -> null);

        // Fetch quarantined enrollments snapshot
        workerService.getEnrollments().thenAccept(enrollments -> {
            FxAsync.runOnFxThread(() -> {
                for (var enr : enrollments) {
                    boolean exists = telemetryService.getEnrollmentsList().stream()
                        .anyMatch(e -> e.getEnrollmentId().equals(enr.enrollment_id()));
                    if (!exists) {
                        QuarantineEnrollment model = new QuarantineEnrollment(
                            enr.enrollment_id(),
                            enr.worker_id(),
                            enr.hostname(),
                            enr.source_ip(),
                            enr.os(),
                            enr.architecture(),
                            enr.fingerprint_hex(),
                            enr.visual_code(),
                            enr.allocatable_cores(),
                            enr.max_memory_bytes(),
                            enr.submitted_at()
                        );
                        telemetryService.getEnrollmentsList().add(model);
                    }
                }
            });
        }).exceptionally(ex -> null);

        // Fetch tasks snapshot
        if (taskService != null) {
            taskService.getTasks().thenAccept(tasks -> {
                FxAsync.runOnFxThread(() -> {
                    for (var t : tasks) {
                        boolean exists = false;
                        for (var existing : telemetryService.getTasksList()) {
                            if (existing.getTaskId().equals(t.task_id())) {
                                existing.setStatus(t.status());
                                if (t.exit_code() != null) existing.setExitCode(t.exit_code());
                                existing.setInstructionsConsumed(t.effectiveInstructions());
                                existing.setPeakMemoryBytes(t.effectiveMemory());
                                existing.setExecutionWallTimeMs(t.effectiveWallTime());
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            var record = new com.legatus.studio.model.TaskRecord(
                                t.task_id(),
                                t.name(),
                                t.status(),
                                t.exit_code(),
                                t.assigned_worker_id(),
                                t.effectiveInstructions(),
                                t.effectiveMemory(),
                                t.effectiveWallTime(),
                                t.created_at() != null ? t.created_at().toString() : "",
                                t.finished_at() != null ? t.finished_at().toString() : ""
                            );
                            telemetryService.getTasksList().add(record);
                        }
                    }
                });
            }).exceptionally(ex -> null);
        }
    }

    public RestClient getRestClient() { return restClient; }
    public AuthService getAuthService() { return authService; }
    public WorkerService getWorkerService() { return workerService; }
    public TaskService getTaskService() { return taskService; }
    public BytecodeService getBytecodeService() { return bytecodeService; }
    public SecretsService getSecretsService() { return secretsService; }
    public UserManagementService getUserManagementService() { return userManagementService; }
    public AuditLogService getAuditLogService() { return auditLogService; }
    public LogStreamBuffer getLogStreamBuffer() { return logStreamBuffer; }
    public LstcpClient getLstcpClient() { return lstcpClient; }
    public TelemetryService getTelemetryService() { return telemetryService; }
    public MainViewModel getMainViewModel() { return mainViewModel; }
    public com.legatus.studio.viewmodel.FunctionsViewModel getFunctionsViewModel() { return functionsViewModel; }
    public OperatorSession getOperatorSession() { return OperatorSession.getInstance(); }
}
