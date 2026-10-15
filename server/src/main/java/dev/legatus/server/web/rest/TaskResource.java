package dev.legatus.server.web.rest;

import dev.legatus.server.service.TaskGatewayService;
import dev.legatus.server.service.dto.task.AbortTaskRequest;
import dev.legatus.server.service.dto.task.AbortTaskResponse;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import dev.legatus.server.service.dto.task.TaskStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for submitting, inspecting, and aborting compute tasks (api-endpoint.md Section 4).
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(TaskResource.class);

    private final TaskGatewayService taskGatewayService;

    public TaskResource(TaskGatewayService taskGatewayService) {
        this.taskGatewayService = taskGatewayService;
    }

    /**
     * {@code POST /api/v1/tasks} : Submits a compiled Solix workload for execution.
     *
     * @param request task submission specification
     * @param servletRequest HTTP servlet request for client IP
     * @return 202 Accepted with task ID and status
     */
    @PostMapping
    public ResponseEntity<SubmitTaskResponse> submitTask(
        @Valid @RequestBody SubmitTaskRequest request,
        HttpServletRequest servletRequest
    ) {
        LOG.debug("REST request to submit task: {}", request.name());
        String sourceIp = servletRequest.getRemoteAddr();
        SubmitTaskResponse response = taskGatewayService.submitTask(request, sourceIp);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * {@code GET /api/v1/tasks/{taskId}} : Queries task status, telemetry, and terminal output.
     *
     * @param taskId task identifier
     * @return 200 OK with detailed task status
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable("taskId") String taskId) {
        LOG.debug("REST request to query task status: {}", taskId);
        TaskStatusResponse response = taskGatewayService.getTaskStatus(taskId);
        return ResponseEntity.ok(response);
    }

    /**
     * {@code POST /api/v1/tasks/{taskId}/abort} : Requests termination of an in-flight or queued task.
     *
     * @param taskId task identifier
     * @param request optional abort parameters with reason and grace period
     * @param servletRequest HTTP servlet request for client IP
     * @return 202 Accepted with abort confirmation
     */
    @PostMapping("/{taskId}/abort")
    public ResponseEntity<AbortTaskResponse> abortTask(
        @PathVariable("taskId") String taskId,
        @RequestBody(required = false) AbortTaskRequest request,
        HttpServletRequest servletRequest
    ) {
        LOG.debug("REST request to abort task: {}", taskId);
        String sourceIp = servletRequest.getRemoteAddr();
        AbortTaskResponse response = taskGatewayService.abortTask(taskId, request, sourceIp);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
