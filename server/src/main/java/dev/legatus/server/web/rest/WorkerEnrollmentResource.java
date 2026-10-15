package dev.legatus.server.web.rest;

import dev.legatus.server.service.WorkerEnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for worker node quarantine enrollment, polling, and ticket authentication.
 * <p>
 * Complies with {@code api-endpoint.md} Section 2 &amp; 3.1:
 * <ul>
 *   <li>{@code POST /api/v1/workers/register} (202 Accepted)</li>
 *   <li>{@code GET /api/v1/workers/register/{enrollment_id}/status} (200 OK)</li>
 *   <li>{@code POST /api/v1/auth/worker} (200 OK)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class WorkerEnrollmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerEnrollmentResource.class);

    private final WorkerEnrollmentService workerEnrollmentService;

    public WorkerEnrollmentResource(WorkerEnrollmentService workerEnrollmentService) {
        this.workerEnrollmentService = workerEnrollmentService;
    }

    /**
     * {@code POST /api/v1/workers/register} : Submit quarantine worker registration.
     */
    @PostMapping("/workers/register")
    public ResponseEntity<WorkerEnrollmentService.RegisterResponse> register(
        @Valid @RequestBody WorkerEnrollmentService.RegisterRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request to register worker {} from IP {}", request.workerId(), clientIp);
        WorkerEnrollmentService.RegisterResponse response = workerEnrollmentService.registerWorker(request, clientIp);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * {@code GET /api/v1/workers/register/:enrollmentId/status} : Poll enrollment status with single-read token delivery.
     */
    @GetMapping("/workers/register/{enrollmentId}/status")
    public ResponseEntity<WorkerEnrollmentService.StatusResponse> getStatus(
        @PathVariable("enrollmentId") String enrollmentId
    ) {
        LOG.debug("REST request to check enrollment status for {}", enrollmentId);
        WorkerEnrollmentService.StatusResponse response = workerEnrollmentService.getEnrollmentStatus(enrollmentId);
        return ResponseEntity.ok(response);
    }

    /**
     * {@code POST /api/v1/auth/worker} : Exchange static worker token for a 30-second WebSocket ticket.
     */
    @PostMapping("/auth/worker")
    public ResponseEntity<WorkerEnrollmentService.AuthResponse> authenticateWorker(
        @Valid @RequestBody WorkerEnrollmentService.AuthRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request to authenticate worker {} from IP {}", request.workerId(), clientIp);
        WorkerEnrollmentService.AuthResponse response = workerEnrollmentService.authenticateWorker(request, clientIp);
        return ResponseEntity.ok(response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
