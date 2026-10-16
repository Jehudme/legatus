package dev.legatus.server.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.WormAuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link TaskHistoryRetentionSweeper} on H2 disk database.
 * Verifies that expired terminal tasks are purged while active tasks and statutory
 * WORM audit logs remain strictly non-deletable and intact.
 */
@IntegrationTest
@Transactional
class TaskHistoryRetentionSweeperIT {

    @Autowired
    private ComputeTaskRepository computeTaskRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private WormAuditService wormAuditService;

    @Autowired
    private LegatusProperties legatusProperties;

    private TaskHistoryRetentionSweeper retentionSweeper;

    private String oldCompletedTaskId;
    private String recentCompletedTaskId;
    private String executingTaskId;
    private UUID statutoryAuditId;

    @BeforeEach
    void setUp() {
        retentionSweeper = new TaskHistoryRetentionSweeper(
            computeTaskRepository,
            wormAuditService,
            legatusProperties
        );

        legatusProperties.getRetention().setAutoPurge(true);
        legatusProperties.getRetention().setCompletedTaskTtlHours(168); // 7 days

        Instant now = Instant.now();
        Instant completed200HoursAgo = now.minus(200, ChronoUnit.HOURS);
        Instant completed1HourAgo = now.minus(1, ChronoUnit.HOURS);

        // 1. Old completed task (> 168h ago) -> SHOULD be purged
        oldCompletedTaskId = "task-old-completed-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeTask oldTask = new ComputeTask()
            .taskId(oldCompletedTaskId)
            .name("old-completed-task")
            .status(TaskStatus.COMPLETED)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(10_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(2)
            .createdAt(completed200HoursAgo.minus(10, ChronoUnit.SECONDS))
            .completedAt(completed200HoursAgo);
        computeTaskRepository.saveAndFlush(oldTask);

        // 2. Recent completed task (< 168h ago) -> MUST NOT be purged
        recentCompletedTaskId = "task-recent-completed-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeTask recentTask = new ComputeTask()
            .taskId(recentCompletedTaskId)
            .name("recent-completed-task")
            .status(TaskStatus.COMPLETED)
            .priority(TaskPriority.HIGH)
            .priorityWeight(75)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(10_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(2)
            .createdAt(completed1HourAgo.minus(10, ChronoUnit.SECONDS))
            .completedAt(completed1HourAgo);
        computeTaskRepository.saveAndFlush(recentTask);

        // 3. In-flight EXECUTING task -> MUST NOT be purged regardless of creation timestamp
        executingTaskId = "task-in-flight-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeTask executingTask = new ComputeTask()
            .taskId(executingTaskId)
            .name("in-flight-task")
            .status(TaskStatus.EXECUTING)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(10_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(2)
            .createdAt(completed200HoursAgo)
            .completedAt(null);
        computeTaskRepository.saveAndFlush(executingTask);

        // 4. Record statutory audit log entry -> MUST NEVER be purged
        AuditLog audit = wormAuditService.recordEvent(
            "operator-admin",
            "CLUSTER_ADMIN",
            "COMPLIANCE_CHECKPOINT",
            "SYSTEM",
            "statutory-audit-record",
            AuditActionStatus.SUCCESS,
            "{\"action\":\"test_checkpoint\"}",
            "127.0.0.1",
            "SOC2_CC6.5"
        );
        statutoryAuditId = audit.getEventId();
    }

    @Test
    @DisplayName("Should purge old completed task, keep recent task and executing task, and leave statutory WORM audit log intact")
    void testRetentionSweepDatabaseBehavior() {
        long initialAuditCount = auditLogRepository.count();

        int purgedCount = retentionSweeper.purgeOldCompletedTasks();

        assertThat(purgedCount).isGreaterThanOrEqualTo(1);

        // Old completed task was purged
        assertThat(computeTaskRepository.findById(oldCompletedTaskId)).isEmpty();

        // Recent completed task remains intact
        assertThat(computeTaskRepository.findById(recentCompletedTaskId)).isPresent();

        // In-flight task remains intact
        assertThat(computeTaskRepository.findById(executingTaskId)).isPresent();

        // Statutory audit log remains intact (and incremented by TASK_HISTORY_PURGED event)
        assertThat(auditLogRepository.findById(statutoryAuditId)).isPresent();
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(initialAuditCount);
    }

    @Test
    @DisplayName("Should purge terminal tasks older than cutoff even when completedAt is null")
    void testPurgeTerminalTasksWithNullCompletedAt() {
        Instant completed200HoursAgo = Instant.now().minus(200, ChronoUnit.HOURS);
        String nullCompletedTaskId = "task-aborted-null-completed-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeTask nullCompletedTask = new ComputeTask()
            .taskId(nullCompletedTaskId)
            .name("null-completed-aborted-task")
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
            .maxRetries(2)
            .createdAt(completed200HoursAgo)
            .completedAt(null);
        computeTaskRepository.saveAndFlush(nullCompletedTask);

        int purged = retentionSweeper.purgeOldCompletedTasks();
        assertThat(purged).isGreaterThanOrEqualTo(1);
        assertThat(computeTaskRepository.findById(nullCompletedTaskId)).isEmpty();
    }
}
