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

        this.lstcpClient = new LstcpClient(wsUrl, trustAllCerts);
        this.telemetryService = new TelemetryService(lstcpClient);

        this.lstcpClient.setStateListener(state -> {
            Platform.runLater(() -> mainViewModel.updateConnectionState(state));
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
        if (workerService == null || telemetryService == null) return;

        // Fetch workers snapshot
        workerService.getWorkers().thenAccept(workers -> {
            Platform.runLater(() -> {
                for (var w : workers) {
                    WorkerNode node = new WorkerNode(
                        w.worker_id(),
                        w.hostname(),
                        w.source_ip(),
                        w.os(),
                        w.architecture(),
                        w.state(),
                        w.allocatable_cores(),
                        w.max_memory_bytes(),
                        w.cpu_load_pct(),
                        w.memory_used_bytes(),
                        w.active_tasks_count(),
                        w.last_heartbeat()
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
            Platform.runLater(() -> {
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
}
