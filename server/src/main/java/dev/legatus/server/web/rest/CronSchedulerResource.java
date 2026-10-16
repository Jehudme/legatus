package dev.legatus.server.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import dev.legatus.server.service.scheduler.CronSchedulerService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for dynamic runtime management of cron triggers.
 * <p>
 * Complies with {@code server-spec.md} Section 6.1 and {@code MS-005}:
 * <ul>
 *   <li>{@code GET /api/v1/admin/cron-triggers} (List all cron jobs)</li>
 *   <li>{@code POST /api/v1/admin/cron-triggers} (Register new cron job)</li>
 *   <li>{@code POST /api/v1/admin/cron-triggers/{name}/pause} (Pause cron job)</li>
 *   <li>{@code POST /api/v1/admin/cron-triggers/{name}/resume} (Resume cron job)</li>
 *   <li>{@code POST /api/v1/admin/cron-triggers/{name}/trigger} (Trigger job now)</li>
 *   <li>{@code DELETE /api/v1/admin/cron-triggers/{name}} (Unregister cron job)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/cron-triggers")
public class CronSchedulerResource {

    private static final Logger LOG = LoggerFactory.getLogger(CronSchedulerResource.class);

    private final CronSchedulerService cronSchedulerService;
    private final WormAuditService wormAuditService;

    public CronSchedulerResource(CronSchedulerService cronSchedulerService, WormAuditService wormAuditService) {
        this.cronSchedulerService = cronSchedulerService;
        this.wormAuditService = wormAuditService;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CronTriggerDto(
        @JsonProperty("name") String name,
        @JsonProperty("function_name") String functionName,
        @JsonProperty("cron_expression") String cronExpression,
        @JsonProperty("priority") TaskPriority priority,
        @JsonProperty("status") String status,
        @JsonProperty("arguments") Map<String, Object> arguments,
        @JsonProperty("env") Map<String, String> env
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterCronTriggerRequest(
        @JsonProperty("name") String name,
        @JsonProperty("function_name") String functionName,
        @JsonProperty("cron_expression") String cronExpression,
        @JsonProperty("priority") TaskPriority priority,
        @JsonProperty("arguments") Map<String, Object> arguments,
        @JsonProperty("env") Map<String, String> env
    ) {}

    @GetMapping
    public ResponseEntity<List<CronTriggerDto>> listCronTriggers() {
        Map<String, LegatusProperties.CronJobConfig> jobs = cronSchedulerService.getAllJobs();
        List<CronTriggerDto> list = new ArrayList<>();

        for (Map.Entry<String, LegatusProperties.CronJobConfig> entry : jobs.entrySet()) {
            String name = entry.getKey();
            LegatusProperties.CronJobConfig cfg = entry.getValue();
            String status = cronSchedulerService.isJobPaused(name) ? "PAUSED" : "ACTIVE";
            list.add(new CronTriggerDto(
                name,
                cfg.getFunctionName(),
                cfg.getCronExpression(),
                cfg.getPriority(),
                status,
                cfg.getArguments(),
                cfg.getEnv()
            ));
        }

        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<CronTriggerDto> registerCronTrigger(
        @RequestBody RegisterCronTriggerRequest req,
        Authentication auth,
        HttpServletRequest httpReq
    ) {
        if (req == null || req.name() == null || req.name().isBlank() || req.cronExpression() == null || req.functionName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields: name, function_name, cron_expression");
        }

        LegatusProperties.CronJobConfig config = new LegatusProperties.CronJobConfig();
        config.setName(req.name());
        config.setFunctionName(req.functionName());
        config.setCronExpression(req.cronExpression());
        if (req.priority() != null) config.setPriority(req.priority());
        if (req.arguments() != null) config.setArguments(req.arguments());
        if (req.env() != null) config.setEnv(req.env());

        boolean ok = cronSchedulerService.registerJob(config);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron job definition or syntax: " + req.cronExpression());
        }

        String principal = auth != null ? auth.getName() : "anonymous";
        wormAuditService.recordEvent(
            principal,
            "OPERATOR",
            "CRON_TRIGGER_REGISTERED",
            "CRON_TRIGGER",
            req.name(),
            AuditActionStatus.SUCCESS,
            String.format("{\"cron_expression\":\"%s\",\"function\":\"%s\"}", req.cronExpression(), req.functionName()),
            httpReq.getRemoteAddr(),
            "SOC2_CC6.8"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new CronTriggerDto(
            req.name(),
            req.functionName(),
            req.cronExpression(),
            req.priority() != null ? req.priority() : TaskPriority.NORMAL,
            "ACTIVE",
            req.arguments(),
            req.env()
        ));
    }

    @PostMapping("/{name}/pause")
    public ResponseEntity<Map<String, Object>> pauseCronTrigger(
        @PathVariable("name") String name,
        Authentication auth,
        HttpServletRequest httpReq
    ) {
        boolean paused = cronSchedulerService.pauseJob(name);
        if (!paused) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cron job not found: " + name);
        }

        String principal = auth != null ? auth.getName() : "anonymous";
        wormAuditService.recordEvent(
            principal,
            "OPERATOR",
            "CRON_TRIGGER_PAUSED",
            "CRON_TRIGGER",
            name,
            AuditActionStatus.SUCCESS,
            "{\"action\":\"PAUSE\"}",
            httpReq.getRemoteAddr(),
            "SOC2_CC6.8"
        );

        return ResponseEntity.ok(Map.of("name", name, "status", "PAUSED"));
    }

    @PostMapping("/{name}/resume")
    public ResponseEntity<Map<String, Object>> resumeCronTrigger(
        @PathVariable("name") String name,
        Authentication auth,
        HttpServletRequest httpReq
    ) {
        boolean resumed = cronSchedulerService.resumeJob(name);
        if (!resumed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cron job not found or not paused: " + name);
        }

        String principal = auth != null ? auth.getName() : "anonymous";
        wormAuditService.recordEvent(
            principal,
            "OPERATOR",
            "CRON_TRIGGER_RESUMED",
            "CRON_TRIGGER",
            name,
            AuditActionStatus.SUCCESS,
            "{\"action\":\"RESUME\"}",
            httpReq.getRemoteAddr(),
            "SOC2_CC6.8"
        );

        return ResponseEntity.ok(Map.of("name", name, "status", "ACTIVE"));
    }

    @PostMapping("/{name}/trigger")
    public ResponseEntity<SubmitTaskResponse> triggerCronTriggerNow(
        @PathVariable("name") String name,
        Authentication auth,
        HttpServletRequest httpReq
    ) {
        try {
            SubmitTaskResponse resp = cronSchedulerService.triggerJobNow(name);
            String principal = auth != null ? auth.getName() : "anonymous";
            wormAuditService.recordEvent(
                principal,
                "OPERATOR",
                "CRON_TRIGGER_INVOKED_MANUAL",
                "CRON_TRIGGER",
                name,
                AuditActionStatus.SUCCESS,
                String.format("{\"task_id\":\"%s\"}", resp != null ? resp.taskId() : null),
                httpReq.getRemoteAddr(),
                "SOC2_CC6.8"
            );
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteCronTrigger(
        @PathVariable("name") String name,
        Authentication auth,
        HttpServletRequest httpReq
    ) {
        boolean removed = cronSchedulerService.unregisterJob(name);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cron job not found: " + name);
        }

        String principal = auth != null ? auth.getName() : "anonymous";
        wormAuditService.recordEvent(
            principal,
            "OPERATOR",
            "CRON_TRIGGER_DELETED",
            "CRON_TRIGGER",
            name,
            AuditActionStatus.SUCCESS,
            "{\"action\":\"DELETE\"}",
            httpReq.getRemoteAddr(),
            "SOC2_CC6.8"
        );

        return ResponseEntity.noContent().build();
    }
}
