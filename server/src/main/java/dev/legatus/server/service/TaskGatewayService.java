package dev.legatus.server.service;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.security.AuthoritiesConstants;
import dev.legatus.server.security.SecurityUtils;
import dev.legatus.server.service.dto.task.AbortTaskRequest;
import dev.legatus.server.service.dto.task.AbortTaskResponse;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import dev.legatus.server.service.dto.task.TaskStatusResponse;
import dev.legatus.server.service.scheduler.TaskDispatcher;
import dev.legatus.server.service.scheduler.TaskSchedulerService;
import dev.legatus.server.storage.BytecodeStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Service managing task ingestion, validation, lifecycle queries, and cancellation.
 * <p>
 * Complies with {@code api-endpoint.md} Section 4 and {@code server-spec.md} Section 4.3:
 * <ul>
 *   <li>Validates queue depth against {@code legatus.scheduler.max-queue-depth} (HTTP 429).</li>
 *   <li>Verifies bytecode SHA-256 integrity and ingests into CAS storage.</li>
 *   <li>Appends immutable WORM audit entries for task submissions and cancellations.</li>
 *   <li>Invokes {@link TaskSchedulerService} for immediate scheduling attempts.</li>
 *   <li>Forwards {@code ABORT_TASK} frames via {@link TaskDispatcher} to worker nodes.</li>
 * </ul>
 */
@Service
public class TaskGatewayService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskGatewayService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final ComputeTaskRepository computeTaskRepository;
    private final BytecodeStorageService bytecodeStorageService;
    private final TaskSchedulerService taskSchedulerService;
    private final TaskDispatcher taskDispatcher;
    private final WormAuditService wormAuditService;
    private final LegatusProperties legatusProperties;
    private final ObjectMapper objectMapper;
    private final dev.legatus.server.service.studio.StudioEventPublisher studioEventPublisher;
    private final dev.legatus.server.security.BytecodeSecurityValidator bytecodeSecurityValidator;
    private final dev.legatus.server.repository.SolixFunctionRepository solixFunctionRepository;

    public TaskGatewayService(
        ComputeTaskRepository computeTaskRepository,
        BytecodeStorageService bytecodeStorageService,
        TaskSchedulerService taskSchedulerService,
        TaskDispatcher taskDispatcher,
        WormAuditService wormAuditService,
        LegatusProperties legatusProperties,
        ObjectMapper objectMapper
    ) {
        this(computeTaskRepository, bytecodeStorageService, taskSchedulerService, taskDispatcher, wormAuditService, legatusProperties, objectMapper, null, new dev.legatus.server.security.BytecodeSecurityValidator(), null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TaskGatewayService(
        ComputeTaskRepository computeTaskRepository,
        BytecodeStorageService bytecodeStorageService,
        TaskSchedulerService taskSchedulerService,
        TaskDispatcher taskDispatcher,
        WormAuditService wormAuditService,
        LegatusProperties legatusProperties,
        ObjectMapper objectMapper,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.service.studio.StudioEventPublisher studioEventPublisher,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.security.BytecodeSecurityValidator bytecodeSecurityValidator,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.repository.SolixFunctionRepository solixFunctionRepository
    ) {
        this.computeTaskRepository = computeTaskRepository;
        this.bytecodeStorageService = bytecodeStorageService;
        this.taskSchedulerService = taskSchedulerService;
        this.taskDispatcher = taskDispatcher;
        this.wormAuditService = wormAuditService;
        this.legatusProperties = legatusProperties;
        this.objectMapper = objectMapper;
        this.studioEventPublisher = studioEventPublisher;
        this.bytecodeSecurityValidator = bytecodeSecurityValidator != null ? bytecodeSecurityValidator : new dev.legatus.server.security.BytecodeSecurityValidator();
        this.solixFunctionRepository = solixFunctionRepository;
    }

    /**
     * Submits a new compute task for execution.
     *
     * @param request task submission specification
     * @param sourceIp client remote IP
     * @return submission acknowledgment with task ID and status
     */
    @Transactional
    public SubmitTaskResponse submitTask(SubmitTaskRequest request, String sourceIp) {
        Objects.requireNonNull(request, "request cannot be null");

        // 1. Queue depth validation (HTTP 429)
        int maxQueueDepth = legatusProperties.getScheduler().getMaxQueueDepth();
        long queuedCount = computeTaskRepository.countByStatus(TaskStatus.QUEUED);
        if (queuedCount >= maxQueueDepth) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Cluster task queue depth exceeded (" + queuedCount + "/" + maxQueueDepth + ")"
            );
        }

        // 2. Bytecode SHA-256 and payload validation
        String expectedSha256 = request.bytecode().sha256().toLowerCase().trim();
        if (!expectedSha256.matches("^[a-f0-9]{64}$")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid bytecode SHA-256 digest format: " + request.bytecode().sha256()
            );
        }

        if (request.bytecode().payload() != null && !request.bytecode().payload().isBlank()) {
            byte[] bytecodeBytes;
            try {
                bytecodeBytes = Base64.getDecoder().decode(request.bytecode().payload().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Base64-encoded bytecode payload");
            }
            bytecodeSecurityValidator.validateBytecode(bytecodeBytes);
            try {
                bytecodeStorageService.storeWithExpectedHash(expectedSha256, new ByteArrayInputStream(bytecodeBytes));
            } catch (IOException e) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bytecode verification or storage failed: " + e.getMessage()
                );
            }
        } else {
            if (!bytecodeStorageService.exists(expectedSha256)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bytecode with SHA-256 " + expectedSha256 + " not found in storage. Upload bytecode payload first."
                );
            }
        }

        // 3. Resolve defaults for priority, weights, and constraints
        TaskPriority priority = request.priority() != null ? request.priority() : TaskPriority.NORMAL;
        int priorityWeight = request.priorityWeight() != null ? request.priorityWeight() : defaultWeightFor(priority);

        long instructionBudget = 50_000_000L;
        long maxMemoryBytes = 67_108_864L;
        int timeoutMs = 30_000;
        if (request.constraints() != null) {
            if (request.constraints().instructionBudget() != null) {
                instructionBudget = request.constraints().instructionBudget();
            }
            if (request.constraints().maxMemoryBytes() != null) {
                maxMemoryBytes = request.constraints().maxMemoryBytes();
            }
            if (request.constraints().timeoutMs() != null) {
                timeoutMs = request.constraints().timeoutMs();
            }
            if (Boolean.TRUE.equals(request.constraints().unlimitedInstructions())) {
                instructionBudget = 0L;
            }
            if (Boolean.TRUE.equals(request.constraints().unlimitedTime())) {
                timeoutMs = 0;
            }
        }
        if (Boolean.TRUE.equals(request.unlimitedInstructions())) {
            instructionBudget = 0L;
        }
        if (Boolean.TRUE.equals(request.unlimitedTime())) {
            timeoutMs = 0;
        }

        String taskId = generateTaskId();
        ComputeTask task = new ComputeTask()
            .taskId(taskId)
            .name(request.name())
            .status(TaskStatus.QUEUED)
            .priority(priority)
            .priorityWeight(priorityWeight)
            .idempotent(request.idempotent() != null ? request.idempotent() : false)
            .entryPoint(request.entryPoint() != null && !request.entryPoint().isBlank() ? request.entryPoint() : "main")
            .bytecodeSha256(expectedSha256)
            .instructionBudget(instructionBudget)
            .maxMemoryBytes(maxMemoryBytes)
            .timeoutMs(timeoutMs)
            .attemptCount(0)
            .maxRetries(request.maxRetries() != null ? request.maxRetries() : 0)
            .createdAt(Instant.now())
            .inputJson(serializeJson(request.arguments()))
            .envJson(serializeJson(request.env()))
            .secretRefsJson(serializeJson(request.secretRefs()))
            .notifyOnFailure(Boolean.TRUE.equals(request.notifyOnFailure()));

        if (solixFunctionRepository != null) {
            solixFunctionRepository.findFirstByBytecodeSha256(expectedSha256).ifPresent(task::setFunction);
        }

        task = computeTaskRepository.saveAndFlush(task);

        // 4. Record WORM audit entry
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        wormAuditService.recordEvent(
            actor,
            resolveActorRole(),
            "TASK_SUBMITTED",
            "TASK",
            taskId,
            AuditActionStatus.SUCCESS,
            "{\"name\":\"" + task.getName() + "\",\"priority\":\"" + priority + "\"}",
            sourceIp != null ? sourceIp : "127.0.0.1",
            WormAuditService.DEFAULT_COMPLIANCE_FLAG
        );

        // 5. Attempt immediate scheduling and dispatch
        Optional<WorkerNode> scheduledWorker = taskSchedulerService.assignAndDispatchTaskWithRetry(taskId);
        TaskStatus currentStatus = scheduledWorker.isPresent() ? TaskStatus.EXECUTING : TaskStatus.QUEUED;

        LOG.info("Submitted task {} (priority={}, status={})", taskId, priority, currentStatus);

        if (studioEventPublisher != null) {
            studioEventPublisher.publishTaskLifecycle(new dev.legatus.server.service.dto.lstcp.EventTaskLifecycleFrame.Payload(
                task.getTaskId(),
                task.getName(),
                currentStatus.name(),
                task.getExitCode(),
                scheduledWorker.map(WorkerNode::getWorkerId).orElse(null),
                task.getInstructionsConsumed(),
                task.getPeakMemoryBytes(),
                task.getWallTimeMs(),
                task.getInstructionBudget(),
                task.getTimeoutMs()
            ));
        }

        return new SubmitTaskResponse(taskId, currentStatus, task.getCreatedAt().toEpochMilli());
    }

    /**
     * Lists all compute tasks sorted newest first.
     *
     * @return list of detailed task status responses
     */
    @Transactional(readOnly = true)
    public List<TaskStatusResponse> listTasks() {
        return computeTaskRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
            .stream()
            .map(this::toTaskStatusResponse)
            .toList();
    }

    /**
     * Retrieves status, metrics, and output for a specific task.
     *
     * @param taskId task identifier
     * @return detailed task status response
     */
    @Transactional(readOnly = true)
    public TaskStatusResponse getTaskStatus(String taskId) {
        ComputeTask task = computeTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId));
        return toTaskStatusResponse(task);
    }

    public TaskStatusResponse toTaskStatusResponse(ComputeTask task) {
        TaskStatusResponse.TaskMetrics metrics = null;
        if (task.getInstructionsConsumed() != null || task.getPeakMemoryBytes() != null || task.getWallTimeMs() != null) {
            metrics = new TaskStatusResponse.TaskMetrics(
                task.getInstructionsConsumed(),
                task.getPeakMemoryBytes(),
                task.getWallTimeMs()
            );
        }

        Map<String, Object> output = parseJsonMap(task.getOutputJson());
        Map<String, Object> error = parseJsonMap(task.getErrorJson());

        String workerId = task.getAssignedWorker() != null ? task.getAssignedWorker().getWorkerId() : null;
        Long startedAt = task.getStartedAt() != null ? task.getStartedAt().toEpochMilli() : null;
        Long completedAt = task.getCompletedAt() != null ? task.getCompletedAt().toEpochMilli() : null;
        long submittedAt = task.getCreatedAt() != null ? task.getCreatedAt().toEpochMilli() : System.currentTimeMillis();

        return new TaskStatusResponse(
            task.getTaskId(),
            task.getName(),
            task.getStatus(),
            task.getExitCode(),
            task.getPriority(),
            workerId,
            task.getAttemptCount() != null ? task.getAttemptCount() : 0,
            submittedAt,
            startedAt,
            completedAt,
            metrics,
            output,
            error,
            task.getInstructionBudget(),
            task.getTimeoutMs()
        );
    }

    /**
     * Aborts an in-flight or queued task.
     *
     * @param taskId task identifier
     * @param request abort request with reason and grace period
     * @param sourceIp client remote IP
     * @return abort response with updated status
     */
    @Transactional
    public AbortTaskResponse abortTask(String taskId, AbortTaskRequest request, String sourceIp) {
        ComputeTask task = computeTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId));

        if (task.getStatus() == TaskStatus.COMPLETED ||
            task.getStatus() == TaskStatus.FAILED ||
            task.getStatus() == TaskStatus.ABORTED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Task is already in terminal state: " + task.getStatus()
            );
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
            ? request.reason()
            : "OPERATOR_CANCELLED";

        if (task.getStatus() == TaskStatus.QUEUED) {
            task.setStatus(TaskStatus.ABORTED);
            task.setCompletedAt(Instant.now());
            if (task.getCreatedAt() != null) {
                task.setWallTimeMs(Math.max(1L, java.time.Duration.between(task.getCreatedAt(), task.getCompletedAt()).toMillis()));
            }
            task.setErrorJson("{\"code\":\"ERR_TASK_ABORTED\",\"reason\":\"" + reason + "\"}");
            computeTaskRepository.saveAndFlush(task);

            recordAudit("TASK_ABORTED", taskId, sourceIp, reason);
            LOG.info("Aborted queued task {}", taskId);

            if (studioEventPublisher != null) {
                studioEventPublisher.publishTaskLifecycle(new dev.legatus.server.service.dto.lstcp.EventTaskLifecycleFrame.Payload(
                    task.getTaskId(),
                    task.getName(),
                    TaskStatus.ABORTED.name(),
                    task.getExitCode(),
                    null,
                    task.getInstructionsConsumed(),
                    task.getPeakMemoryBytes(),
                    task.getWallTimeMs(),
                    task.getInstructionBudget(),
                    task.getTimeoutMs()
                ));
            }

            return new AbortTaskResponse(taskId, "ABORTED", null, null);
        }

        // DISPATCHED or EXECUTING state: forward ABORT_TASK frame to assigned worker
        int gracePeriod = (request != null && request.gracePeriodMs() != null) ? request.gracePeriodMs() : 10000;
        String workerId = task.getAssignedWorker() != null ? task.getAssignedWorker().getWorkerId() : null;

        if (workerId != null) {
            taskDispatcher.abortTask(workerId, taskId, reason, gracePeriod);
        }

        task.setStatus(TaskStatus.ABORTED);
        task.setCompletedAt(Instant.now());
        if (task.getStartedAt() != null) {
            task.setWallTimeMs(Math.max(1L, java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis()));
        } else if (task.getCreatedAt() != null) {
            task.setWallTimeMs(Math.max(1L, java.time.Duration.between(task.getCreatedAt(), task.getCompletedAt()).toMillis()));
        }
        task.setErrorJson("{\"code\":\"ERR_TASK_ABORTED\",\"reason\":\"" + reason + "\"}");
        computeTaskRepository.saveAndFlush(task);

        recordAudit("TASK_ABORTED", taskId, sourceIp, reason);
        long deadline = System.currentTimeMillis() + gracePeriod;

        if (studioEventPublisher != null) {
            studioEventPublisher.publishTaskLifecycle(new dev.legatus.server.service.dto.lstcp.EventTaskLifecycleFrame.Payload(
                task.getTaskId(),
                task.getName(),
                TaskStatus.ABORTED.name(),
                task.getExitCode(),
                workerId,
                task.getInstructionsConsumed(),
                task.getPeakMemoryBytes(),
                task.getWallTimeMs(),
                task.getInstructionBudget(),
                task.getTimeoutMs()
            ));
        }

        LOG.info("Aborted executing task {} on worker {} (deadline: {})", taskId, workerId, deadline);
        return new AbortTaskResponse(taskId, "ABORTING", workerId, deadline);
    }

    private void recordAudit(String eventType, String taskId, String sourceIp, String reason) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        wormAuditService.recordEvent(
            actor,
            resolveActorRole(),
            eventType,
            "TASK",
            taskId,
            AuditActionStatus.SUCCESS,
            "{\"reason\":\"" + reason + "\"}",
            sourceIp != null ? sourceIp : "127.0.0.1",
            WormAuditService.DEFAULT_COMPLIANCE_FLAG
        );
    }

    private String generateTaskId() {
        return "task-" + DATE_FORMATTER.format(LocalDate.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int defaultWeightFor(TaskPriority priority) {
        return switch (priority) {
            case LOW -> 25;
            case NORMAL -> 50;
            case HIGH -> 75;
            case CRITICAL -> 100;
        };
    }

    private String serializeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            LOG.warn("Failed to serialize object to JSON: {}", obj, e);
            return null;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON map: {}", json, e);
            return null;
        }
    }

    private String resolveActorRole() {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.CLUSTER_ADMIN)) {
            return "CLUSTER_ADMIN";
        }
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.OPERATOR)) {
            return "OPERATOR";
        }
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.AUDITOR)) {
            return "AUDITOR";
        }
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return "ADMIN";
        }
        return "SYSTEM";
    }
}
