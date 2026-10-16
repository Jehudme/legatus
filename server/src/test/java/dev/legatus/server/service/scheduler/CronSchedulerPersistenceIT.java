package dev.legatus.server.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.CronJobTrigger;
import dev.legatus.server.repository.CronJobTriggerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test verifying persistence of dynamic cron triggers across application restarts.
 * Complies with MS-012 Section 4.1.
 */
@IntegrationTest
@Transactional
class CronSchedulerPersistenceIT {

    @Autowired
    private CronSchedulerService cronSchedulerService;

    @Autowired
    private CronJobTriggerRepository cronJobTriggerRepository;

    @Autowired
    private LegatusProperties legatusProperties;

    @Test
    @DisplayName("Dynamic Cron Persistence: Dynamically registered cron job persists to DB and reloads on startup")
    void testDynamicCronJobPersistenceAndReload() {
        String jobName = "dynamic-cleanup-" + UUID.randomUUID().toString().substring(0, 8);
        String cronExpr = "0 */10 * * * *"; // every 10 minutes
        String functionName = "log-purger";

        LegatusProperties.CronJobConfig config = new LegatusProperties.CronJobConfig();
        config.setName(jobName);
        config.setCronExpression(cronExpr);
        config.setFunctionName(functionName);

        // 1. Register dynamic cron job
        boolean registered = cronSchedulerService.registerJob(config);
        assertThat(registered).isTrue();
        assertThat(cronSchedulerService.getScheduledJobNames()).contains(jobName);

        // 2. Verify persisted in database
        Optional<CronJobTrigger> inDb = cronJobTriggerRepository.findById(jobName);
        assertThat(inDb).isPresent();
        assertThat(inDb.get().getCronExpression()).isEqualTo(cronExpr);
        assertThat(inDb.get().getFunctionId()).isEqualTo(functionName);
        assertThat(inDb.get().getActive()).isTrue();

        // 3. Simulate server shutdown/restart by unregistering from memory only without deleting DB row
        cronSchedulerService.unregisterJob(jobName, false);
        assertThat(cronSchedulerService.getScheduledJobNames()).doesNotContain(jobName);
        assertThat(cronJobTriggerRepository.findById(jobName)).isPresent();

        // 4. Trigger application ready initialization
        legatusProperties.getFeatures().setCronScheduler(true);
        legatusProperties.getCron().setEnabled(true);
        cronSchedulerService.initializeScheduledJobs();

        // 5. Verify reloaded into active scheduled jobs
        assertThat(cronSchedulerService.getScheduledJobNames()).contains(jobName);
        assertThat(cronSchedulerService.getAllJobs()).containsKey(jobName);
        assertThat(cronSchedulerService.getAllJobs().get(jobName).getCronExpression()).isEqualTo(cronExpr);
    }
}
