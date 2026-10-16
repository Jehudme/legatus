package dev.legatus.server.features.workflows;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.TaskGatewayService;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskRequest.BytecodePayload;
import dev.legatus.server.service.dto.task.SubmitTaskRequest.ConstraintsPayload;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkflowEngineService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEngineService.class);

    private final Map<String, WorkflowPipeline> pipelines = new ConcurrentHashMap<>();

    private final TaskGatewayService taskGatewayService;
    private final ComputeTaskRepository computeTaskRepository;
    private final SolixFunctionRepository solixFunctionRepository;
    private final WormAuditService wormAuditService;
    private final LegatusProperties legatusProperties;
    private final ObjectMapper objectMapper;

    public WorkflowEngineService(
        TaskGatewayService taskGatewayService,
        ComputeTaskRepository computeTaskRepository,
        SolixFunctionRepository solixFunctionRepository,
        WormAuditService wormAuditService,
        LegatusProperties legatusProperties,
        ObjectMapper objectMapper
    ) {
        this.taskGatewayService = taskGatewayService;
        this.computeTaskRepository = computeTaskRepository;
        this.solixFunctionRepository = solixFunctionRepository;
        this.wormAuditService = wormAuditService;
        this.legatusProperties = legatusProperties;
        this.objectMapper = objectMapper;
    }

    public WorkflowPipeline createAndLaunchPipeline(
        String name,
        FailurePolicy failurePolicy,
        List<WorkflowStep> stepList,
        String submittedBy
    ) {
        if (!legatusProperties.getFeatures().isWorkflows()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workflow engine is disabled (legatus.features.workflows=false)");
        }

        if (stepList == null || stepList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pipeline must contain at least one step");
        }

        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        for (WorkflowStep step : stepList) {
            if (step.getStepId() == null || step.getStepId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every step must have a unique step_id");
            }
            if (stepMap.containsKey(step.getStepId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate step_id: " + step.getStepId());
            }
            stepMap.put(step.getStepId(), step);
        }

        // Validate DAG and detect cycles
        try {
            WorkflowValidator.validateAndSort(stepMap);
        } catch (WorkflowValidator.WorkflowCycleException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        String pipelineId = "pipe-" + UUID.randomUUID();
        WorkflowPipeline pipeline = new WorkflowPipeline(pipelineId, name != null ? name : pipelineId, failurePolicy, stepMap);
        pipeline.setStatus(PipelineStatus.RUNNING);
        pipelines.put(pipelineId, pipeline);

        wormAuditService.recordEvent(
            submittedBy,
            "OPERATOR",
            "WORKFLOW_PIPELINE_CREATED",
            "PIPELINE",
            pipelineId,
            AuditActionStatus.SUCCESS,
            String.format("{\"name\":\"%s\",\"steps\":%d,\"policy\":\"%s\"}", pipeline.getName(), stepList.size(), pipeline.getFailurePolicy()),
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        // Launch initial root steps (dependsOn is empty)
        launchEligibleSteps(pipeline);

        return pipeline;
    }

    public synchronized WorkflowPipeline advancePipeline(String pipelineId) {
        WorkflowPipeline pipeline = pipelines.get(pipelineId);
        if (pipeline == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow pipeline not found: " + pipelineId);
        }

        if (pipeline.getStatus() == PipelineStatus.COMPLETED ||
            pipeline.getStatus() == PipelineStatus.FAILED ||
            pipeline.getStatus() == PipelineStatus.ABORTED) {
            return pipeline;
        }

        boolean anyFailed = false;

        // Check running steps
        for (WorkflowStep step : pipeline.getSteps().values()) {
            if (step.getStatus() == StepStatus.RUNNING && step.getTaskId() != null) {
                Optional<ComputeTask> taskOpt = computeTaskRepository.findById(step.getTaskId());
                if (taskOpt.isPresent()) {
                    ComputeTask task = taskOpt.get();
                    if (task.getStatus() == TaskStatus.COMPLETED) {
                        step.setStatus(StepStatus.COMPLETED);
                        if (task.getOutputJson() != null) {
                            try {
                                step.setOutput(objectMapper.readValue(task.getOutputJson(), Object.class));
                            } catch (Exception e) {
                                step.setOutput(task.getOutputJson());
                            }
                        }
                    } else if (task.getStatus() == TaskStatus.FAILED ||
                               task.getStatus() == TaskStatus.ABORTED ||
                               task.getStatus() == TaskStatus.ERR_TIMEOUT ||
                               task.getStatus() == TaskStatus.ERR_OUT_OF_MEMORY ||
                               task.getStatus() == TaskStatus.ERR_INSTRUCTION_BUDGET_EXCEEDED) {
                        step.setStatus(StepStatus.FAILED);
                        step.setError(task.getErrorJson() != null ? task.getErrorJson() : "Task terminated with " + task.getStatus());
                        anyFailed = true;
                    }
                }
            }
        }

        if (anyFailed && pipeline.getFailurePolicy() == FailurePolicy.ABORT_PIPELINE) {
            // Abort pipeline: skip remaining waiting steps
            for (WorkflowStep step : pipeline.getSteps().values()) {
                if (step.getStatus() == StepStatus.WAITING || step.getStatus() == StepStatus.QUEUED) {
                    step.setStatus(StepStatus.SKIPPED);
                }
            }
            pipeline.setStatus(PipelineStatus.FAILED);
            pipeline.setCompletedAt(Instant.now());
            return pipeline;
        }

        // Launch newly unblocked steps
        launchEligibleSteps(pipeline);

        // Check overall completion
        boolean allTerminal = true;
        boolean hasFailures = false;
        for (WorkflowStep step : pipeline.getSteps().values()) {
            if (step.getStatus() == StepStatus.WAITING || step.getStatus() == StepStatus.QUEUED || step.getStatus() == StepStatus.RUNNING) {
                allTerminal = false;
                break;
            }
            if (step.getStatus() == StepStatus.FAILED) {
                hasFailures = true;
            }
        }

        if (allTerminal) {
            pipeline.setStatus(hasFailures ? PipelineStatus.FAILED : PipelineStatus.COMPLETED);
            pipeline.setCompletedAt(Instant.now());
        }

        return pipeline;
    }

    private void launchEligibleSteps(WorkflowPipeline pipeline) {
        for (WorkflowStep step : pipeline.getSteps().values()) {
            if (step.getStatus() != StepStatus.WAITING) {
                continue;
            }

            boolean dependenciesMet = true;
            boolean anyParentFailed = false;
            Map<String, Object> chainedOutputs = new HashMap<>();

            for (String parentId : step.getDependsOn()) {
                WorkflowStep parent = pipeline.getSteps().get(parentId);
                if (parent == null || parent.getStatus() != StepStatus.COMPLETED) {
                    dependenciesMet = false;
                    if (parent != null && parent.getStatus() == StepStatus.FAILED) {
                        anyParentFailed = true;
                    }
                    break;
                }
                if (parent.getOutput() != null) {
                    chainedOutputs.put(parentId, parent.getOutput());
                }
            }

            if (anyParentFailed) {
                step.setStatus(StepStatus.SKIPPED);
                continue;
            }

            if (dependenciesMet) {
                dispatchStep(pipeline, step, chainedOutputs);
            }
        }
    }

    private void dispatchStep(WorkflowPipeline pipeline, WorkflowStep step, Map<String, Object> chainedOutputs) {
        Optional<SolixFunction> functionOpt = solixFunctionRepository.findFirstByNameOrderByCreatedAtDesc(step.getFunctionName());
        if (functionOpt.isEmpty()) {
            step.setStatus(StepStatus.FAILED);
            step.setError("Function not found: " + step.getFunctionName());
            return;
        }

        SolixFunction function = functionOpt.get();
        String payloadBase64 = null;
        if (function.getBytecodeBlob() != null && function.getBytecodeBlob().length > 0) {
            payloadBase64 = Base64.getEncoder().encodeToString(function.getBytecodeBlob());
        }

        Map<String, Object> effectiveArguments = new HashMap<>(step.getArguments());
        if (!chainedOutputs.isEmpty()) {
            effectiveArguments.put("parent_outputs", chainedOutputs);
        }

        SubmitTaskRequest request = new SubmitTaskRequest(
            "step-" + pipeline.getPipelineId() + "-" + step.getStepId(),
            TaskPriority.NORMAL,
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
            effectiveArguments,
            step.getEnv(),
            List.of()
        );

        try {
            SubmitTaskResponse response = taskGatewayService.submitTask(request, "workflow-engine");
            step.setTaskId(response.taskId());
            step.setStatus(StepStatus.RUNNING);
        } catch (Exception e) {
            LOG.error("Failed to submit step {}: {}", step.getStepId(), e.getMessage());
            step.setStatus(StepStatus.FAILED);
            step.setError("Failed to dispatch task: " + e.getMessage());
        }
    }

    public WorkflowPipeline getPipeline(String pipelineId) {
        WorkflowPipeline pipeline = pipelines.get(pipelineId);
        if (pipeline == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow pipeline not found: " + pipelineId);
        }
        return advancePipeline(pipelineId);
    }

    public List<WorkflowPipeline> listPipelines() {
        for (String id : pipelines.keySet()) {
            advancePipeline(id);
        }
        return new ArrayList<>(pipelines.values());
    }
}
