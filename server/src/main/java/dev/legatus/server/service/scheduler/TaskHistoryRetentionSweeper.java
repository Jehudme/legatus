package dev.legatus.server.service.scheduler;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.WormAuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background auto-purge sweeper that cleans up historical completed task records
 * older than the configured TTL (default: 168 hours / 7 days).
 * <p>
 * Complies with {@code server-spec.md} Section 6.1 and 6.2 (Issue #211):
 * <ul>
 *   <li>Toggled via {@code legatus.retention.auto-purge} (default: {@code true}).</li>
 *   <li>Deletes only terminal task states (COMPLETED, FAILED, TIMEOUT, ABORTED, etc.) past cutoff.</li>
 *   <li>Under statutory compliance mandates, {@code audit_logs} is strictly exempted and never purged.</li>
 *   <li>Records a statutory WORM audit log entry whenever task records are purged.</li>
 * </ul>
 */
@Service
public class TaskHistoryRetentionSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(TaskHistoryRetentionSweeper.class);

    public static final Set<TaskStatus> TERMINAL_STATUSES = Set.of(
        TaskStatus.COMPLETED,
        TaskStatus.FAILED,
        TaskStatus.ERR_INSTRUCTION_BUDGET_EXCEEDED,
        TaskStatus.ERR_OUT_OF_MEMORY,
        TaskStatus.ERR_TIMEOUT,
        TaskStatus.ERR_BYTECODE_INVALID,
        TaskStatus.ERR_WORKER_LOST,
        TaskStatus.ABORTED,
        TaskStatus.KILLED_FORCIBLY,
        TaskStatus.CRASHED
    );

    private final ComputeTaskRepository computeTaskRepository;
    private final WormAuditService wormAuditService;
    private final LegatusProperties legatusProperties;

    public TaskHistoryRetentionSweeper(
        ComputeTaskRepository computeTaskRepository,
        WormAuditService wormAuditService,
        LegatusProperties legatusProperties
    ) {
        this.computeTaskRepository = computeTaskRepository;
        this.wormAuditService = wormAuditService;
        this.legatusProperties = legatusProperties;
    }

    /**
     * Scheduled background execution running on a recurring interval (default: every 60 minutes).
     *
     * @return count of deleted task records
     */
    @Scheduled(
        fixedDelayString = "${legatus.retention.interval-ms:3600000}",
        initialDelayString = "${legatus.retention.initial-delay-ms:60000}"
    )
    @Transactional
    public int purgeOldCompletedTasks() {
        if (!legatusProperties.getRetention().isAutoPurge()) {
            LOG.debug("Task history retention auto-purge is disabled via legatus.retention.auto-purge=false");
            return 0;
        }

        int ttlHours = legatusProperties.getRetention().getCompletedTaskTtlHours();
        if (ttlHours <= 0) {
            LOG.warn("Task history retention auto-purge skipped: invalid completedTaskTtlHours ({})", ttlHours);
            return 0;
        }

        Instant cutoff = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        LOG.debug("Executing task history retention sweep for terminal tasks completed before {}", cutoff);

        int deletedCount = computeTaskRepository.deleteCompletedTasksOlderThan(TERMINAL_STATUSES, cutoff);

        if (deletedCount > 0) {
            LOG.info(
                "Task history retention sweeper purged {} historical task record(s) older than {}h (cutoff: {})",
                deletedCount,
                ttlHours,
                cutoff
            );

            wormAuditService.recordEvent(
                "retention-sweeper",
                "SYSTEM",
                "TASK_HISTORY_PURGED",
                "TASKS",
                "tasks-table",
                AuditActionStatus.SUCCESS,
                String.format("{\"purged_records\":%d,\"ttl_hours\":%d,\"cutoff\":\"%s\"}", deletedCount, ttlHours, cutoff),
                "127.0.0.1",
                "SOC2_CC6.5"
            );
        } else {
            LOG.debug("Task history retention sweep completed: zero expired records to purge");
        }

        return deletedCount;
    }
}
