package dev.legatus.server.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.features.workflows.FailurePolicy;
import dev.legatus.server.features.workflows.WorkflowEngineService;
import dev.legatus.server.features.workflows.WorkflowPipeline;
import dev.legatus.server.features.workflows.WorkflowStep;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowResource {

    private final WorkflowEngineService workflowEngineService;

    public WorkflowResource(WorkflowEngineService workflowEngineService) {
        this.workflowEngineService = workflowEngineService;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateWorkflowRequest(
        @JsonProperty("name") String name,
        @JsonProperty("failure_policy") FailurePolicy failurePolicy,
        @JsonProperty("steps") List<WorkflowStep> steps
    ) {}

    @PostMapping
    public ResponseEntity<WorkflowPipeline> createWorkflow(
        @RequestBody CreateWorkflowRequest request,
        Authentication authentication,
        HttpServletRequest httpServletRequest
    ) {
        String user = authentication != null ? authentication.getName() : "anonymous";
        WorkflowPipeline pipeline = workflowEngineService.createAndLaunchPipeline(
            request.name(),
            request.failurePolicy(),
            request.steps(),
            user
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(pipeline);
    }

    @GetMapping
    public ResponseEntity<List<WorkflowPipeline>> listWorkflows() {
        return ResponseEntity.ok(workflowEngineService.listPipelines());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowPipeline> getWorkflow(@PathVariable("id") String id) {
        return ResponseEntity.ok(workflowEngineService.getPipeline(id));
    }

    @PostMapping("/{id}/advance")
    public ResponseEntity<WorkflowPipeline> advanceWorkflow(@PathVariable("id") String id) {
        return ResponseEntity.ok(workflowEngineService.advancePipeline(id));
    }
}
