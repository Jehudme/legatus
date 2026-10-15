package dev.legatus.server.web.rest;

import dev.legatus.server.service.OperatorAccountService;
import dev.legatus.server.service.dto.OperatorLoginRequest;
import dev.legatus.server.service.dto.OperatorLoginResponse;
import dev.legatus.server.service.dto.OperatorPasswordChangeRequest;
import dev.legatus.server.service.dto.OperatorPasswordChangeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for operator authentication and self-service password updates.
 * <p>
 * Complies with {@code api-endpoint.md} Sections 3.2 &amp; 3.3:
 * <ul>
 *   <li>{@code POST /api/v1/auth/operator/login} (Argon2id verification, lockout protection, JWT + Studio ticket)</li>
 *   <li>{@code PUT /api/v1/auth/operator/password} (Self-service password update)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth/operator")
public class OperatorAuthResource {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorAuthResource.class);

    private final OperatorAccountService operatorAccountService;

    public OperatorAuthResource(OperatorAccountService operatorAccountService) {
        this.operatorAccountService = operatorAccountService;
    }

    /**
     * {@code POST /api/v1/auth/operator/login} : Authenticate an operator from Legatus Studio or CLI.
     */
    @PostMapping("/login")
    public ResponseEntity<OperatorLoginResponse> login(
        @Valid @RequestBody OperatorLoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request to authenticate operator {} from IP {}", request.username(), clientIp);
        OperatorLoginResponse response = operatorAccountService.login(request, clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * {@code PUT /api/v1/auth/operator/password} : Enables an authenticated operator or auditor to update their own password.
     */
    @PutMapping("/password")
    public ResponseEntity<OperatorPasswordChangeResponse> changePassword(
        @Valid @RequestBody OperatorPasswordChangeRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request by operator {} to change password from IP {}", username, clientIp);
        OperatorPasswordChangeResponse response = operatorAccountService.changePassword(username, request, clientIp);
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
