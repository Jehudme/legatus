package dev.legatus.server.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.WormAuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TaskHistoryRetentionSweeper}.
 */
@ExtendWith(MockitoExtension.class)
class TaskHistoryRetentionSweeperTest {

    @Mock
    private ComputeTaskRepository computeTaskRepository;

    @Mock
    private WormAuditService wormAuditService;

    private LegatusProperties legatusProperties;
    private TaskHistoryRetentionSweeper retentionSweeper;

    @BeforeEach
    void setUp() {
        legatusProperties = new LegatusProperties();
        legatusProperties.getRetention().setAutoPurge(true);
        legatusProperties.getRetention().setCompletedTaskTtlHours(168);

        retentionSweeper = new TaskHistoryRetentionSweeper(
            computeTaskRepository,
            wormAuditService,
            legatusProperties
        );
    }

    @Test
    @DisplayName("Should purge expired completed tasks and record statutory audit entry")
    void testPurgeExpiredCompletedTasksSuccess() {
        when(computeTaskRepository.deleteCompletedTasksOlderThan(eq(TaskHistoryRetentionSweeper.TERMINAL_STATUSES), any(Instant.class)))
            .thenReturn(42);

        int deleted = retentionSweeper.purgeOldCompletedTasks();

        assertThat(deleted).isEqualTo(42);

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(computeTaskRepository).deleteCompletedTasksOlderThan(eq(TaskHistoryRetentionSweeper.TERMINAL_STATUSES), cutoffCaptor.capture());

        Instant expectedCutoffUpperBound = Instant.now().minus(168, ChronoUnit.HOURS);
        assertThat(cutoffCaptor.getValue()).isBeforeOrEqualTo(expectedCutoffUpperBound);

        verify(wormAuditService).recordEvent(
            eq("retention-sweeper"),
            eq("SYSTEM"),
            eq("TASK_HISTORY_PURGED"),
            eq("TASKS"),
            eq("tasks-table"),
            eq(AuditActionStatus.SUCCESS),
            contains("\"purged_records\":42"),
            eq("127.0.0.1"),
            eq("SOC2_CC6.5")
        );
    }

    @Test
    @DisplayName("Should return 0 and not record audit event when zero records purged")
    void testPurgeZeroRecords() {
        when(computeTaskRepository.deleteCompletedTasksOlderThan(any(), any()))
            .thenReturn(0);

        int deleted = retentionSweeper.purgeOldCompletedTasks();

        assertThat(deleted).isEqualTo(0);
        verifyNoInteractions(wormAuditService);
    }

    @Test
    @DisplayName("Should skip purge when auto-purge is disabled in configuration")
    void testPurgeWhenDisabled() {
        legatusProperties.getRetention().setAutoPurge(false);

        int deleted = retentionSweeper.purgeOldCompletedTasks();

        assertThat(deleted).isEqualTo(0);
        verifyNoInteractions(computeTaskRepository);
        verifyNoInteractions(wormAuditService);
    }

    @Test
    @DisplayName("Should skip purge when completedTaskTtlHours is non-positive")
    void testPurgeWhenInvalidTtl() {
        legatusProperties.getRetention().setCompletedTaskTtlHours(0);

        int deleted = retentionSweeper.purgeOldCompletedTasks();

        assertThat(deleted).isEqualTo(0);
        verifyNoInteractions(computeTaskRepository);
        verifyNoInteractions(wormAuditService);
    }
}
