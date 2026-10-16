package dev.legatus.server.service.scheduler;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.TaskGatewayService;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskRequest.BytecodePayload;
import dev.legatus.server.service.dto.task.SubmitTaskRequest.ConstraintsPayload;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/**
 * Service managing recurring cron triggers for registered Solix workloads.
 * <p>
 * Complies with {@code server-spec.md} Section 6.1 (Issue #210):
 * <ul>
 *   <li>Toggled via {@code legatus.features.cron-scheduler} (default: {@code true}).</li>
 *   <li>Evaluates 6-field standard cron expressions and schedules triggers on Virtual Threads.</li>
 *   <li>Translates scheduled executions into deterministic {@link SubmitTaskRequest} payloads.</li>
 *   <li>Enforces statutory WORM audit entries for all cron trigger activations and skips.</li>
 * </ul>
 */
@Service
public class CronSchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(CronSchedulerService.class);

    private final LegatusProperties legatusProperties;
    private final SolixFunctionRepository solixFunctionRepository;
    private final TaskGatewayService taskGatewayService;
    private final WormAuditService wormAuditService;
    private final dev.legatus.server.repository.CronJobTriggerRepository cronJobTriggerRepository;
    private final TaskScheduler scheduler;
    private final boolean ownsScheduler;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, LegatusProperties.CronJobConfig> configuredJobs = new ConcurrentHashMap<>();
    private final Set<String> pausedJobs = ConcurrentHashMap.newKeySet();

    @Autowired
    public CronSchedulerService(
        LegatusProperties legatusProperties,
        SolixFunctionRepository solixFunctionRepository,
        TaskGatewayService taskGatewayService,
        WormAuditService wormAuditService,
        @Autowired(required = false) dev.legatus.server.repository.CronJobTriggerRepository cronJobTriggerRepository,
        @Autowired(required = false) TaskScheduler taskScheduler
    ) {
        this.legatusProperties = legatusProperties;
        this.solixFunctionRepository = solixFunctionRepository;
        this.taskGatewayService = taskGatewayService;
        this.wormAuditService = wormAuditService;
        this.cronJobTriggerRepository = cronJobTriggerRepository;

        if (taskScheduler != null) {
            this.scheduler = taskScheduler;
            this.ownsScheduler = false;
        } else {
            ThreadPoolTaskScheduler fallbackScheduler = new ThreadPoolTaskScheduler();
            fallbackScheduler.setPoolSize(2);
            fallbackScheduler.setThreadNamePrefix("legatus-cron-");
            fallbackScheduler.initialize();
            this.scheduler = fallbackScheduler;
            this.ownsScheduler = true;
        }
    }

    public CronSchedulerService(
        LegatusProperties legatusProperties,
        SolixFunctionRepository solixFunctionRepository,
        TaskGatewayService taskGatewayService,
        WormAuditService wormAuditService,
        TaskScheduler taskScheduler
    ) {
        this(legatusProperties, solixFunctionRepository, taskGatewayService, wormAuditService, null, taskScheduler);
    }

    /**
     * Bootstraps configured cron jobs upon application startup if the feature is enabled.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initializeScheduledJobs() {
        if (!legatusProperties.getFeatures().isCronScheduler() || !legatusProperties.getCron().isEnabled()) {
            LOG.info("Cron scheduler engine is disabled via configuration (legatus.features.cron-scheduler=false)");
            return;
        }

        List<LegatusProperties.CronJobConfig> jobs = legatusProperties.getCron().getJobs();
        if (jobs != null && !jobs.isEmpty()) {
            LOG.info("Initializing Legatus Cron Scheduler with {} statically configured recurring job(s)", jobs.size());
            for (LegatusProperties.CronJobConfig job : jobs) {
                scheduleInternal(job, false);
            }
        }

        // Reload persistent dynamic cron triggers from database
        if (cronJobTriggerRepository != null) {
            List<dev.legatus.server.domain.CronJobTrigger> persistedTriggers = cronJobTriggerRepository.findAllByActiveTrue();
            LOG.info("Reloading {} persisted active cron trigger(s) from database", persistedTriggers.size());
            for (dev.legatus.server.domain.CronJobTrigger trigger : persistedTriggers) {
                LegatusProperties.CronJobConfig cfg = new LegatusProperties.CronJobConfig();
                cfg.setName(trigger.getId());
                cfg.setFunctionName(trigger.getFunctionId());
                cfg.setCronExpression(trigger.getCronExpression());
                scheduleInternal(cfg, false);
            }
        }
    }

    /**
     * Registers and schedules a new cron job, persisting it to the database.
     *
     * @param job cron job definition
     * @return true if successfully scheduled, false otherwise
     */
    public synchronized boolean registerJob(LegatusProperties.CronJobConfig job) {
        return registerJob(job, true);
    }

    public synchronized boolean registerJob(LegatusProperties.CronJobConfig job, boolean persist) {
        if (job == null || job.getName() == null || job.getName().isBlank() || job.getCronExpression() == null) {
            LOG.warn("Rejected registration of invalid cron job: name and expression required");
            return false;
        }

        if (!CronExpression.isValidExpression(job.getCronExpression())) {
            LOG.error("Cannot register cron job '{}': invalid cron expression '{}'", job.getName(), job.getCronExpression());
            return false;
        }

        boolean scheduled = scheduleInternal(job, true);
        if (scheduled && persist && cronJobTriggerRepository != null) {
            dev.legatus.server.domain.CronJobTrigger entity = cronJobTriggerRepository.findById(job.getName())
                .orElseGet(() -> new dev.legatus.server.domain.CronJobTrigger());
            entity.setId(job.getName());
            entity.setCronExpression(job.getCronExpression());
            entity.setFunctionId(job.getFunctionName());
            entity.setActive(true);
            cronJobTriggerRepository.save(entity);
            LOG.debug("Persisted dynamic cron trigger '{}' to database", job.getName());
        }
        return scheduled;
    }

    private synchronized boolean scheduleInternal(LegatusProperties.CronJobConfig job, boolean cancelPrevious) {
        if (cancelPrevious) {
            unregisterJob(job.getName(), false);
        }

        ScheduledFuture<?> future = scheduler.schedule(
            () -> {
                try {
                    triggerCronJob(job);
                } catch (Exception e) {
                    LOG.error("Uncaught exception while executing cron job '{}': {}", job.getName(), e.getMessage(), e);
                }
            },
            new CronTrigger(job.getCronExpression())
        );

        if (future != null) {
            scheduledTasks.put(job.getName(), future);
        }
        configuredJobs.put(job.getName(), job);
        LOG.info("Successfully scheduled cron job '{}' [function: '{}', cron: '{}']", job.getName(), job.getFunctionName(), job.getCronExpression());
        return true;
    }

    /**
     * Unregisters and cancels a scheduled cron job.
     *
     * @param jobName unique name of the cron job
     * @return true if an active job was removed, false otherwise
     */
    public synchronized boolean unregisterJob(String jobName) {
        return unregisterJob(jobName, true);
    }

    public synchronized boolean unregisterJob(String jobName, boolean deleteFromDb) {
        if (jobName == null) {
            return false;
        }
        pausedJobs.remove(jobName);
        ScheduledFuture<?> future = scheduledTasks.remove(jobName);
        boolean hadConfig = configuredJobs.remove(jobName) != null;
        if (future != null) {
            future.cancel(false);
            LOG.info("Cancelled and unregistered cron job '{}'", jobName);
        }
        if (deleteFromDb && cronJobTriggerRepository != null) {
            cronJobTriggerRepository.deleteById(jobName);
        }
        return hadConfig || (future != null);
    }

    /**
     * Pauses an actively scheduled cron job without deleting its configuration.
     */
    public synchronized boolean pauseJob(String jobName) {
        if (jobName == null || !configuredJobs.containsKey(jobName)) {
            return false;
        }
        ScheduledFuture<?> future = scheduledTasks.remove(jobName);
        if (future != null) {
            future.cancel(false);
        }
        pausedJobs.add(jobName);
        if (cronJobTriggerRepository != null) {
            cronJobTriggerRepository.findById(jobName).ifPresent(t -> {
                t.setActive(false);
                cronJobTriggerRepository.save(t);
            });
        }
        LOG.info("Paused cron job '{}'", jobName);
        return true;
    }

    /**
     * Resumes a paused cron job.
     */
    public synchronized boolean resumeJob(String jobName) {
        if (jobName == null || !pausedJobs.contains(jobName)) {
            return false;
        }
        LegatusProperties.CronJobConfig job = configuredJobs.get(jobName);
        if (job == null) {
            pausedJobs.remove(jobName);
            return false;
        }
        pausedJobs.remove(jobName);
        if (cronJobTriggerRepository != null) {
            cronJobTriggerRepository.findById(jobName).ifPresent(t -> {
                t.setActive(true);
                cronJobTriggerRepository.save(t);
            });
        }
        return registerJob(job, false);
    }

    /**
     * Checks if a cron job is currently paused.
     */
    public boolean isJobPaused(String jobName) {
        return pausedJobs.contains(jobName);
    }

    /**
     * Returns an unmodifiable snapshot map of all registered cron jobs.
     */
    public Map<String, LegatusProperties.CronJobConfig> getAllJobs() {
        return Collections.unmodifiableMap(new HashMap<>(configuredJobs));
    }

    /**
     * Executes the target Solix function for a cron trigger, building task payload and submitting.
     *
     * @param job cron job configuration
     * @return task submission response, or null if skipped
     */
    public SubmitTaskResponse triggerCronJob(LegatusProperties.CronJobConfig job) {
        LOG.debug("Cron trigger activated for job: '{}' (target function: '{}')", job.getName(), job.getFunctionName());

        Optional<SolixFunction> functionOpt = solixFunctionRepository.findFirstByNameOrderByCreatedAtDesc(job.getFunctionName());
        if (functionOpt.isEmpty()) {
            LOG.warn("Cron job '{}' trigger skipped: target function '{}' not found in registry", job.getName(), job.getFunctionName());
            wormAuditService.recordEvent(
                "cron-scheduler",
                "SYSTEM",
                "CRON_TRIGGER_SKIPPED",
                "CRON_JOB",
                job.getName(),
                AuditActionStatus.FAILED,
                String.format("{\"reason\":\"FUNCTION_NOT_FOUND\",\"function\":\"%s\"}", job.getFunctionName()),
                "127.0.0.1",
                "SOC2_CC7.2"
            );
            return null;
        }

        SolixFunction function = functionOpt.get();
        String payloadBase64 = null;
        if (function.getBytecodeBlob() != null && function.getBytecodeBlob().length > 0) {
            payloadBase64 = Base64.getEncoder().encodeToString(function.getBytecodeBlob());
        }

        TaskPriority priority = job.getPriority() != null ? job.getPriority() : TaskPriority.NORMAL;

        SubmitTaskRequest request = new SubmitTaskRequest(
            "cron-" + job.getName() + "-" + System.currentTimeMillis(),
            priority,
            50,
            false,
            2,
            function.getEntryPoint(),
            new BytecodePayload(
                function.getBytecodeFormat(),
                "base64",
                function.getBytecodeSha256(),
                payloadBase64
            ),
            new ConstraintsPayload(
                function.getDefaultBudget(),
                function.getDefaultMemoryBytes(),
                function.getDefaultTimeoutMs()
            ),
            job.getArguments() != null ? job.getArguments() : Map.of(),
            job.getEnv() != null ? job.getEnv() : Map.of(),
            List.of()
        );

        SubmitTaskResponse response = taskGatewayService.submitTask(request, "127.0.0.1 (cron-scheduler)");
        LOG.info("Cron job '{}' successfully dispatched compute task: {}", job.getName(), response.taskId());

        if (cronJobTriggerRepository != null) {
            cronJobTriggerRepository.findById(job.getName()).ifPresent(t -> {
                t.setLastTriggeredAt(java.time.Instant.now());
                t.setTotalInvocations(t.getTotalInvocations() != null ? t.getTotalInvocations() + 1 : 1L);
                cronJobTriggerRepository.save(t);
            });
        }

        wormAuditService.recordEvent(
            "cron-scheduler",
            "SYSTEM",
            "CRON_TASK_DISPATCHED",
            "TASK",
            response.taskId(),
            AuditActionStatus.SUCCESS,
            String.format("{\"job\":\"%s\",\"function\":\"%s\"}", job.getName(), job.getFunctionName()),
            "127.0.0.1",
            "SOC2_CC7.2"
        );

        return response;
    }

    /**
     * Manually triggers a registered cron job immediately on-demand.
     *
     * @param jobName name of the registered job
     * @return submission confirmation response
     */
    public SubmitTaskResponse triggerJobNow(String jobName) {
        LegatusProperties.CronJobConfig job = configuredJobs.get(jobName);
        if (job == null) {
            throw new IllegalArgumentException("Cron job '" + jobName + "' is not registered");
        }
        return triggerCronJob(job);
    }

    /**
     * Returns a copy of currently registered job names.
     */
    public Set<String> getScheduledJobNames() {
        return Collections.unmodifiableSet(new HashSet<>(scheduledTasks.keySet()));
    }

    @PreDestroy
    public synchronized void shutdown() {
        LOG.info("Shutting down CronSchedulerService and cancelling {} active cron task(s)", scheduledTasks.size());
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
        configuredJobs.clear();

        if (ownsScheduler && scheduler instanceof ThreadPoolTaskScheduler tp) {
            tp.shutdown();
        }
    }
}
