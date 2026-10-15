package dev.legatus.server.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.TaskGatewayService;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

/**
 * Unit tests for {@link CronSchedulerService}.
 */
@ExtendWith(MockitoExtension.class)
class CronSchedulerServiceTest {

    @Mock
    private SolixFunctionRepository solixFunctionRepository;

    @Mock
    private TaskGatewayService taskGatewayService;

    @Mock
    private WormAuditService wormAuditService;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private java.util.concurrent.ScheduledFuture<?> scheduledFuture;

    private LegatusProperties legatusProperties;
    private CronSchedulerService cronSchedulerService;

    private SolixFunction sampleFunction;

    @BeforeEach
    void setUp() {
        legatusProperties = new LegatusProperties();
        legatusProperties.getFeatures().setCronScheduler(true);
        legatusProperties.getCron().setEnabled(true);

        lenient().doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(org.springframework.scheduling.support.CronTrigger.class));

        cronSchedulerService = new CronSchedulerService(
            legatusProperties,
            solixFunctionRepository,
            taskGatewayService,
            wormAuditService,
            taskScheduler
        );

        sampleFunction = new SolixFunction()
            .functionId(UUID.randomUUID())
            .name("scheduled-reporter")
            .semver("1.0.0")
            .entryPoint("run_report")
            .bytecodeFormat("solix-v1")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .bytecodeBlob(new byte[] { 0x01, 0x02, 0x03 })
            .bytecodeBlobContentType("application/octet-stream")
            .defaultBudget(5_000_000L)
            .defaultMemoryBytes(33_554_432L)
            .defaultTimeoutMs(15_000)
            .createdAt(Instant.now());
    }

    @Test
    @DisplayName("Should successfully register and trigger cron job when target function exists")
    void testRegisterAndTriggerJobSuccess() {
        LegatusProperties.CronJobConfig job = new LegatusProperties.CronJobConfig();
        job.setName("hourly-report");
        job.setFunctionName("scheduled-reporter");
        job.setCronExpression("0 0 * * * *");
        job.setPriority(TaskPriority.HIGH);
        job.setArguments(Map.of("scope", "cluster"));

        when(solixFunctionRepository.findFirstByNameOrderByCreatedAtDesc("scheduled-reporter"))
            .thenReturn(Optional.of(sampleFunction));
        when(taskGatewayService.submitTask(any(SubmitTaskRequest.class), any()))
            .thenReturn(new SubmitTaskResponse("task-cron-1234", dev.legatus.server.domain.enumeration.TaskStatus.QUEUED, System.currentTimeMillis()));

        boolean registered = cronSchedulerService.registerJob(job);
        assertThat(registered).isTrue();
        assertThat(cronSchedulerService.getScheduledJobNames()).contains("hourly-report");

        SubmitTaskResponse response = cronSchedulerService.triggerJobNow("hourly-report");
        assertThat(response).isNotNull();
        assertThat(response.taskId()).isEqualTo("task-cron-1234");

        ArgumentCaptor<SubmitTaskRequest> captor = ArgumentCaptor.forClass(SubmitTaskRequest.class);
        verify(taskGatewayService).submitTask(captor.capture(), eq("127.0.0.1 (cron-scheduler)"));

        SubmitTaskRequest submitted = captor.getValue();
        assertThat(submitted.name()).startsWith("cron-hourly-report-");
        assertThat(submitted.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(submitted.entryPoint()).isEqualTo("run_report");
        assertThat(submitted.arguments()).containsEntry("scope", "cluster");
        assertThat(submitted.constraints().instructionBudget()).isEqualTo(5_000_000L);

        verify(wormAuditService).recordEvent(
            eq("cron-scheduler"),
            eq("SYSTEM"),
            eq("CRON_TASK_DISPATCHED"),
            eq("TASK"),
            eq("task-cron-1234"),
            eq(AuditActionStatus.SUCCESS),
            any(),
            eq("127.0.0.1"),
            eq("SOC2_CC7.2")
        );
    }

    @Test
    @DisplayName("Should skip trigger and record audit failure if function not found")
    void testTriggerJobFunctionNotFound() {
        LegatusProperties.CronJobConfig job = new LegatusProperties.CronJobConfig();
        job.setName("missing-fn-job");
        job.setFunctionName("non-existent-function");
        job.setCronExpression("0 0 * * * *");

        when(solixFunctionRepository.findFirstByNameOrderByCreatedAtDesc("non-existent-function"))
            .thenReturn(Optional.empty());

        cronSchedulerService.registerJob(job);
        SubmitTaskResponse response = cronSchedulerService.triggerJobNow("missing-fn-job");

        assertThat(response).isNull();
        verify(taskGatewayService, never()).submitTask(any(), any());

        verify(wormAuditService).recordEvent(
            eq("cron-scheduler"),
            eq("SYSTEM"),
            eq("CRON_TRIGGER_SKIPPED"),
            eq("CRON_JOB"),
            eq("missing-fn-job"),
            eq(AuditActionStatus.FAILED),
            any(),
            eq("127.0.0.1"),
            eq("SOC2_CC7.2")
        );
    }

    @Test
    @DisplayName("Should reject registration with invalid cron expression")
    void testRejectInvalidCronExpression() {
        LegatusProperties.CronJobConfig job = new LegatusProperties.CronJobConfig();
        job.setName("invalid-job");
        job.setFunctionName("reporter");
        job.setCronExpression("not-a-valid-cron");

        boolean registered = cronSchedulerService.registerJob(job);
        assertThat(registered).isFalse();
        assertThat(cronSchedulerService.getScheduledJobNames()).doesNotContain("invalid-job");
    }

    @Test
    @DisplayName("Should unregister existing job and cancel future")
    void testUnregisterJob() {
        LegatusProperties.CronJobConfig job = new LegatusProperties.CronJobConfig();
        job.setName("temporary-job");
        job.setFunctionName("reporter");
        job.setCronExpression("0 0 * * * *");

        cronSchedulerService.registerJob(job);
        assertThat(cronSchedulerService.getScheduledJobNames()).contains("temporary-job");

        boolean removed = cronSchedulerService.unregisterJob("temporary-job");
        assertThat(removed).isTrue();
        assertThat(cronSchedulerService.getScheduledJobNames()).doesNotContain("temporary-job");

        assertThatThrownBy(() -> cronSchedulerService.triggerJobNow("temporary-job"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should not initialize jobs if cron feature is disabled")
    void testInitializeWhenDisabled() {
        legatusProperties.getFeatures().setCronScheduler(false);
        LegatusProperties.CronJobConfig job = new LegatusProperties.CronJobConfig();
        job.setName("disabled-job");
        job.setFunctionName("reporter");
        job.setCronExpression("0 0 * * * *");
        legatusProperties.getCron().getJobs().add(job);

        cronSchedulerService.initializeScheduledJobs();

        assertThat(cronSchedulerService.getScheduledJobNames()).isEmpty();
        verifyNoInteractions(taskScheduler);
    }
}
