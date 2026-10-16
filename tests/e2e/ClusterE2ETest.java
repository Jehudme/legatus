package dev.legatus.server.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.CronJobTrigger;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.orchestration.balancer.ThresholdScheduler;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.CronJobTriggerRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.ClusterMetricsService;
import dev.legatus.server.service.TaskGatewayService;
import dev.legatus.server.service.WorkerEnrollmentService;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.lstcp.EventClusterMetricTickFrame;
import dev.legatus.server.service.dto.lstcp.EventTaskLifecycleFrame;
import dev.legatus.server.service.dto.task.AbortTaskRequest;
import dev.legatus.server.service.dto.task.AbortTaskResponse;
import dev.legatus.server.service.scheduler.CronSchedulerService;
import dev.legatus.server.service.scheduler.TaskHistoryRetentionSweeper;
import dev.legatus.server.service.scheduler.WorkerEligibilityFilter;
import dev.legatus.server.web.websocket.lctp.LctpWorkerSession;
import dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry;
import dev.legatus.server.web.websocket.lstcp.LstcpStudioSession;
import dev.legatus.server.web.websocket.lstcp.StudioSessionRegistry;
import dev.legatus.server.web.websocket.lstcp.StudioStreamBroadcaster;
import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * Military-Grade End-to-End Cluster Integration Suite (MS-016).
 * Verifies all 9 reported defect scenarios in an integrated cluster environment:
 * <ul>
 *   <li>DEF-01: Worker Deletion Cascade & Revocation Socket Severance</li>
 *   <li>DEF-02: RAM Load Saturation & Hysteresis Normalization</li>
 *   <li>DEF-03: 16 Concurrent Tasks on 4 Cores (No Artificial Core Cap)</li>
 *   <li>DEF-04: Task Abort State Transition & Non-Zero Elapsed Wall Time</li>
 *   <li>DEF-05/DEF-08: Multicast Telemetry Streaming & Dynamic Cron Persistence</li>
 *   <li>DEF-06/DEF-09: Task Retention Purge with Null CompletedAt & Binary Execution</li>
 * </ul>
 */
@IntegrationTest
@Transactional
public class ClusterE2ETest {

    @Autowired
    private WorkerEnrollmentService workerEnrollmentService;

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    @Autowired
    private ComputeTaskRepository computeTaskRepository;

    @Autowired
    private WorkerSessionRegistry workerSessionRegistry;

    @Autowired
    private ThresholdScheduler thresholdScheduler;

    @Autowired
    private TaskGatewayService taskGatewayService;

    @Autowired
    private CronSchedulerService cronSchedulerService;

    @Autowired
    private CronJobTriggerRepository cronJobTriggerRepository;

    @Autowired
    private WormAuditService wormAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private LegatusProperties legatusProperties;

    @Autowired
    private ClusterMetricsService clusterMetricsService;

    @Autowired
    private StudioSessionRegistry studioSessionRegistry;

    @Autowired
    private WorkerEligibilityFilter workerEligibilityFilter;

    @Autowired
    private StudioStreamBroadcaster studioStreamBroadcaster;

    @Autowired
    private ObjectMapper objectMapper;

    // Helper: create active worker entity
    private WorkerNode createWorker(String workerId, int cores, int maxTasks, long totalMemoryBytes) {
        return new WorkerNode()
            .workerId(workerId)
            .enrollmentId("enr-" + UUID.randomUUID())
            .hostname("worker-" + workerId + ".internal")
            .os(OperatingSystem.LINUX)
            .arch(CpuArchitecture.X86_64)
            .agentVersion("1.0.0")
            .ed25519Pubkey("MCowBQYDK2VwAyEA" + UUID.randomUUID().toString().replace("-", ""))
            .fingerprint("4E:8A:BC:31:09:FF:7D:12")
            .visualCode("TIGER-GALAXY-77")
            .state(WorkerState.ONLINE)
            .assignedCluster("production-us-east")
            .sourceIp("127.0.0.1")
            .physicalCores(cores)
            .logicalCores(cores)
            .allocatableCores(cores)
            .totalMemoryBytes(totalMemoryBytes)
            .maxMemoryBytes(totalMemoryBytes)
            .maxInstructionBudget(100_000_000L)
            .gpuPresent(false)
            .draining(false)
            .active(true)
            .targetRamPct(80.0)
            .targetCpuPct(75.0)
            .maxConcurrentTasks(maxTasks)
            .createdAt(Instant.now());
    }

    @Test
    @DisplayName("Scenario 1 (DEF-01): Worker Deletion with Task History unlinks tasks and deletes worker")
    void testDefect01_WorkerDeletionWithTaskHistory() {
        String workerId = "e2e-wk-del-" + UUID.randomUUID().toString().substring(0, 8);
        WorkerNode worker = createWorker(workerId, 4, 16, 16L * 1024 * 1024 * 1024);
        worker = workerNodeRepository.saveAndFlush(worker);

        List<String> taskIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String tid = "e2e-task-del-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            taskIds.add(tid);
            ComputeTask task = new ComputeTask()
                .taskId(tid)
                .name("task-" + i)
                .status(TaskStatus.COMPLETED)
                .priority(TaskPriority.NORMAL)
                .priorityWeight(50)
                .idempotent(false)
                .entryPoint("main")
                .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .instructionBudget(50_000_000L)
                .maxMemoryBytes(67_108_864L)
                .timeoutMs(30_000)
                .attemptCount(1)
                .maxRetries(1)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .assignedWorker(worker);
            computeTaskRepository.saveAndFlush(task);
        }

        // Delete worker
        workerEnrollmentService.deleteWorker(workerId, "admin-operator");

        // Asserts worker is gone from database
        assertThat(workerNodeRepository.findById(workerId)).isEmpty();

        // Asserts all 5 tasks remain in history with assignedWorker = null
        for (String tid : taskIds) {
            ComputeTask task = computeTaskRepository.findById(tid).orElseThrow();
            assertThat(task.getAssignedWorker()).isNull();
            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }
    }

    @Test
    @DisplayName("Scenario 2 (DEF-01): Worker Revocation closes active WebSocket session immediately with code 1008")
    void testDefect01_WorkerRevocationSocketSeverance() throws Exception {
        String workerId = "e2e-wk-rev-" + UUID.randomUUID().toString().substring(0, 8);
        WorkerNode worker = createWorker(workerId, 4, 16, 16L * 1024 * 1024 * 1024);
        worker = workerNodeRepository.saveAndFlush(worker);

        WebSocketSession mockWsSession = mock(WebSocketSession.class);
        when(mockWsSession.isOpen()).thenReturn(true);

        TicketBrokerService.WorkerTicket ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_" + UUID.randomUUID(), workerId, 4, 16L * 1024 * 1024 * 1024, 50_000_000L, Instant.now()
        );
        String sessionId = "sess-" + UUID.randomUUID();
        LctpWorkerSession lctpSession = new LctpWorkerSession(sessionId, workerId, mockWsSession, ticket);
        workerSessionRegistry.register(lctpSession);

        assertThat(workerSessionRegistry.getByWorkerId(workerId)).isPresent();

        // Perform revocation
        workerEnrollmentService.revokeWorker(workerId, "security-admin");

        // Verify WebSocket closed with 1008
        ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(mockWsSession, times(1)).close(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(1008);

        // Verify DB state
        WorkerNode updated = workerNodeRepository.findById(workerId).orElseThrow();
        assertThat(updated.getState()).isEqualTo(WorkerState.REVOKED);
        assertThat(updated.getActive()).isFalse();

        workerSessionRegistry.unregister(sessionId);
    }

    @Test
    @DisplayName("Scenario 3 (DEF-02): 16 GB worker with 10.5 GB RAM used computes 65.6% load without false breach")
    void testDefect02_RamSaturationAndHysteresis() {
        String workerId = "e2e-wk-ram-" + UUID.randomUUID().toString().substring(0, 8);
        long sixteenGb = 16L * 1024 * 1024 * 1024;
        long tenAndHalfGb = (long) (10.5 * 1024 * 1024 * 1024);

        WorkerNode worker = createWorker(workerId, 4, 16, sixteenGb);
        workerNodeRepository.saveAndFlush(worker);

        double ramPct = ((double) tenAndHalfGb / sixteenGb) * 100.0;
        assertThat(ramPct).isBetween(65.0, 66.0);

        thresholdScheduler.recordTelemetry(workerId, 40.0, ramPct, 4, 75.0, 80.0, 16);
        assertThat(thresholdScheduler.isBreaching(workerId)).isFalse();

        // Saturation breach above target + hysteresis (80 + 5 = 85%) on a new or saturated node
        String breachWorkerId = "e2e-wk-breach-" + UUID.randomUUID().toString().substring(0, 8);
        thresholdScheduler.recordTelemetry(breachWorkerId, 50.0, 88.0, 5, 75.0, 80.0, 16);
        assertThat(thresholdScheduler.isBreaching(breachWorkerId)).isTrue();

        // Recovers to 78% -> hysteresis keeps it breaching
        for (int i = 0; i < 5; i++) {
            thresholdScheduler.recordTelemetry(breachWorkerId, 50.0, 78.0, 5, 75.0, 80.0, 16);
        }
        assertThat(thresholdScheduler.isBreaching(breachWorkerId)).isTrue();

        // Recovers to 65% (below target - hysteresis = 75%) -> clears breach
        for (int i = 0; i < 15; i++) {
            thresholdScheduler.recordTelemetry(breachWorkerId, 50.0, 65.0, 5, 75.0, 80.0, 16);
        }
        assertThat(thresholdScheduler.isBreaching(breachWorkerId)).isFalse();
    }

    @Test
    @DisplayName("Scenario 4 (DEF-03): 16 concurrent tasks accepted on 4-core worker configured for 16 max tasks")
    void testDefect03_ConcurrentTasksHonorMaxConcurrentTasksWithoutCoreCap() {
        String workerId = "e2e-wk-cap-" + UUID.randomUUID().toString().substring(0, 8);
        // 4 cores, maxConcurrentTasks = 16
        WorkerNode worker = createWorker(workerId, 4, 16, 16L * 1024 * 1024 * 1024);
        workerNodeRepository.saveAndFlush(worker);

        ComputeTask sampleTask = new ComputeTask()
            .taskId("sample-task")
            .priority(TaskPriority.NORMAL)
            .instructionBudget(10_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000);

        // When worker has 4 active tasks (matching core count), it MUST still be eligible for task 5!
        boolean eligibleAt4 = workerEligibilityFilter.isEligible(worker, sampleTask, 4, 4L * 67_108_864L, 8L * 1024 * 1024 * 1024L);
        assertThat(eligibleAt4).isTrue();

        // When worker has 15 active tasks, it MUST still be eligible for task 16!
        boolean eligibleAt15 = workerEligibilityFilter.isEligible(worker, sampleTask, 15, 15L * 67_108_864L, 8L * 1024 * 1024 * 1024L);
        assertThat(eligibleAt15).isTrue();

        // When worker reaches 16 active tasks, it is fully saturated
        boolean eligibleAt16 = workerEligibilityFilter.isEligible(worker, sampleTask, 16, 16L * 67_108_864L, 8L * 1024 * 1024 * 1024L);
        assertThat(eligibleAt16).isFalse();
    }

    @Test
    @DisplayName("Scenario 5 (DEF-04): Task Abort transitions status to ABORTED with non-zero elapsed wall time")
    void testDefect04_TaskAbortStateTransitionAndDuration() {
        String taskId = "e2e-task-abort-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now().minus(3, ChronoUnit.SECONDS);

        ComputeTask task = new ComputeTask()
            .taskId(taskId)
            .name("looping-task")
            .status(TaskStatus.EXECUTING)
            .priority(TaskPriority.HIGH)
            .priorityWeight(75)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(1_000_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(60_000)
            .attemptCount(1)
            .maxRetries(1)
            .createdAt(startedAt.minus(1, ChronoUnit.SECONDS))
            .startedAt(startedAt);
        computeTaskRepository.saveAndFlush(task);

        // Issue task abort
        AbortTaskRequest req = new AbortTaskRequest("Infinite loop termination", 2000);
        AbortTaskResponse resp = taskGatewayService.abortTask(taskId, req, "127.0.0.1");

        assertThat(resp.taskId()).isEqualTo(taskId);
        assertThat(resp.status()).isEqualTo("ABORTING");

        // Assert DB state transitions to ABORTED with non-zero wall time
        ComputeTask aborted = computeTaskRepository.findById(taskId).orElseThrow();
        assertThat(aborted.getStatus()).isEqualTo(TaskStatus.ABORTED);
        assertThat(aborted.getCompletedAt()).isNotNull();
        // Wall time must be non-zero and reflect actual elapsed time (~3000ms), NOT 0ms!
        assertThat(aborted.getWallTimeMs()).isGreaterThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("Scenario 6 (DEF-07): Multicast LSTCP Telemetry Streams to Multiple Studio Sessions")
    void testDefect07_MulticastLstcpTelemetryStreaming() throws Exception {
        WebSocketSession ws1 = mock(WebSocketSession.class);
        when(ws1.isOpen()).thenReturn(true);
        WebSocketSession ws2 = mock(WebSocketSession.class);
        when(ws2.isOpen()).thenReturn(true);

        TicketBrokerService.StudioTicket tkt1 = new TicketBrokerService.StudioTicket("t1", "op1", "ROLE_OPERATOR", Instant.now());
        TicketBrokerService.StudioTicket tkt2 = new TicketBrokerService.StudioTicket("t2", "op2", "ROLE_OPERATOR", Instant.now());

        LstcpStudioSession s1 = new LstcpStudioSession("sess-s1", ws1, tkt1);
        LstcpStudioSession s2 = new LstcpStudioSession("sess-s2", ws2, tkt2);

        s1.getSubscriptions().add("TASK_LIFECYCLE");
        s1.getSubscriptions().add("CLUSTER_METRICS");
        s2.getSubscriptions().add("TASK_LIFECYCLE");
        s2.getSubscriptions().add("CLUSTER_METRICS");

        studioSessionRegistry.register(s1);
        studioSessionRegistry.register(s2);

        try {
            EventTaskLifecycleFrame.Payload payload = new EventTaskLifecycleFrame.Payload(
                "task-mc-1", "multicast-test", "COMPLETED", 0, null, 1500L, 1024L * 1024, 250L, 5000L, 30000,
                System.currentTimeMillis() - 250, System.currentTimeMillis() - 300
            );

            studioStreamBroadcaster.publishTaskLifecycle(payload);

            // Verify both client sockets received message
            verify(ws1, atLeastOnce()).sendMessage(any(TextMessage.class));
            verify(ws2, atLeastOnce()).sendMessage(any(TextMessage.class));
        } finally {
            studioSessionRegistry.unregister("sess-s1");
            studioSessionRegistry.unregister("sess-s2");
        }
    }

    @Test
    @DisplayName("Scenario 7 (DEF-08): Dynamic Cron Trigger persists to DB and reloads across restart")
    void testDefect08_DynamicCronPersistenceAcrossRestart() {
        String jobName = "e2e-cron-" + UUID.randomUUID().toString().substring(0, 8);
        String cronExpr = "0 */15 * * * *";

        LegatusProperties.CronJobConfig config = new LegatusProperties.CronJobConfig();
        config.setName(jobName);
        config.setCronExpression(cronExpr);
        config.setFunctionName("db-vacuum-fn");

        // 1. Register dynamic job
        boolean registered = cronSchedulerService.registerJob(config);
        assertThat(registered).isTrue();

        // 2. Assert persisted in DB
        Optional<CronJobTrigger> inDb = cronJobTriggerRepository.findById(jobName);
        assertThat(inDb).isPresent();
        assertThat(inDb.get().getCronExpression()).isEqualTo(cronExpr);

        // 3. Unload from memory
        cronSchedulerService.unregisterJob(jobName, false);
        assertThat(cronSchedulerService.getScheduledJobNames()).doesNotContain(jobName);

        // 4. Reload from DB
        legatusProperties.getFeatures().setCronScheduler(true);
        legatusProperties.getCron().setEnabled(true);
        cronSchedulerService.initializeScheduledJobs();

        // 5. Assert reloaded
        assertThat(cronSchedulerService.getScheduledJobNames()).contains(jobName);
    }

    @Test
    @DisplayName("Scenario 8 (DEF-09): Task Retention Sweeper purges expired terminal tasks with null completedAt")
    void testDefect09_TaskRetentionSweeperNullCompletedAt() {
        TaskHistoryRetentionSweeper sweeper = new TaskHistoryRetentionSweeper(
            computeTaskRepository, wormAuditService, legatusProperties
        );
        legatusProperties.getRetention().setAutoPurge(true);
        legatusProperties.getRetention().setCompletedTaskTtlHours(168);

        Instant oldTime = Instant.now().minus(200, ChronoUnit.HOURS);
        String tid = "e2e-task-null-comp-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeTask oldTask = new ComputeTask()
            .taskId(tid)
            .name("old-aborted-task")
            .status(TaskStatus.ABORTED)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(10_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(1)
            .createdAt(oldTime)
            .completedAt(null); // NULL completedAt
        computeTaskRepository.saveAndFlush(oldTask);

        int purged = sweeper.purgeOldCompletedTasks();
        assertThat(purged).isGreaterThanOrEqualTo(1);
        assertThat(computeTaskRepository.findById(tid)).isEmpty();
    }

    @Test
    @DisplayName("Scenario 9: Cluster Overview Memory reflects fluctuating real node RAM")
    void testClusterOverviewMemoryFluctuatesWithRealWorkerNodes() {
        long sixteenGb = 16L * 1024 * 1024 * 1024;
        String w1 = "e2e-node-ram-1-" + UUID.randomUUID().toString().substring(0, 6);
        String w2 = "e2e-node-ram-2-" + UUID.randomUUID().toString().substring(0, 6);

        WorkerNode node1 = createWorker(w1, 8, 16, sixteenGb);
        WorkerNode node2 = createWorker(w2, 8, 16, sixteenGb);
        workerNodeRepository.saveAndFlush(node1);
        workerNodeRepository.saveAndFlush(node2);

        var stats = clusterMetricsService.computeClusterStats();
        // Total allocatable memory must reflect sum of real online nodes (at least 32 GB for these 2 nodes)
        assertThat(stats.totalAllocatableMemoryBytes()).isGreaterThanOrEqualTo(32L * 1024 * 1024 * 1024);
        assertThat(stats.nodesOnline()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Scenario 10: Real Native Worker Subprocess Verification (if compiled on host)")
    void testRealNativeRunnerSubprocessExecution() throws Exception {
        File daemonBin = new File("../worker/build/legatusd");
        if (!daemonBin.exists()) {
            daemonBin = new File("worker/build/legatusd");
        }
        if (daemonBin.exists() && daemonBin.canExecute()) {
            Process process = new ProcessBuilder(daemonBin.getAbsolutePath(), "--version")
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(finished).isTrue();
            String output = new String(process.getInputStream().readAllBytes());
            assertThat(output).containsIgnoringCase("legatusd");
        }
    }
}
