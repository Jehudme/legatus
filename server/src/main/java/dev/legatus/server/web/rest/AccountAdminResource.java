package dev.legatus.server.web.rest;

import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import dev.legatus.server.service.OperatorAccountService;
import dev.legatus.server.service.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for operator account management and RBAC administration.
 * <p>
 * Complies with {@code api-endpoint.md} Section 6:
 * <ul>
 *   <li>{@code GET /api/v1/admin/accounts} (List operator accounts with filters)</li>
 *   <li>{@code POST /api/v1/admin/accounts} (Create operator account, min 12 char password)</li>
 *   <li>{@code PUT /api/v1/admin/accounts/{accountId}} (Update account profile &amp; role)</li>
 *   <li>{@code PATCH /api/v1/admin/accounts/{accountId}/status} (Mutate account lifecycle state)</li>
 *   <li>{@code DELETE /api/v1/admin/accounts/{accountId}} (Compliance soft-deletion to DEACTIVATED)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AccountAdminResource {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAdminResource.class);

    private final OperatorAccountService operatorAccountService;

    public AccountAdminResource(OperatorAccountService operatorAccountService) {
        this.operatorAccountService = operatorAccountService;
    }

    /**
     * {@code GET /api/v1/admin/accounts} : Returns a paginated list of registered accounts with optional role and status filtering.
     */
    @GetMapping("")
    public ResponseEntity<PageResponse<AccountResponse>> listAccounts(
        @RequestParam(name = "role", required = false) OperatorRole role,
        @RequestParam(name = "status", required = false) AccountStatus status,
        Pageable pageable
    ) {
        LOG.debug("REST request to list operator accounts (role: {}, status: {})", role, status);
        Page<AccountResponse> page = operatorAccountService.listAccounts(role, status, pageable);
        PageResponse<AccountResponse> response = new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * {@code POST /api/v1/admin/accounts} : Provisions a new operator or compliance auditor account.
     */
    @PostMapping("")
    public ResponseEntity<AccountResponse> createAccount(
        @Valid @RequestBody CreateAccountRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request by {} to create operator account {} from IP {}", actorId, request.username(), clientIp);
        AccountResponse response = operatorAccountService.createAccount(request, actorId, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * {@code PUT /api/v1/admin/accounts/{accountId}} : Modifies profile email and assigned RBAC role.
     */
    @PutMapping("/{accountId}")
    public ResponseEntity<AccountResponse> updateAccount(
        @PathVariable("accountId") UUID accountId,
        @Valid @RequestBody UpdateAccountRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request by {} to update account {} from IP {}", actorId, accountId, clientIp);
        AccountResponse response = operatorAccountService.updateAccount(accountId, request, actorId, clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * {@code PATCH /api/v1/admin/accounts/{accountId}/status} : Transitions account lifecycle between ACTIVE, SUSPENDED, or DEACTIVATED.
     */
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> mutateStatus(
        @PathVariable("accountId") UUID accountId,
        @Valid @RequestBody MutateAccountStatusRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request by {} to mutate status for account {} to {} from IP {}", actorId, accountId, request.status(), clientIp);
        AccountResponse response = operatorAccountService.mutateStatus(accountId, request, actorId, clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * {@code DELETE /api/v1/admin/accounts/{accountId}} : Soft-deletes account to DEACTIVATED preserving audit referential integrity.
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<DeactivateAccountResponse> softDelete(
        @PathVariable("accountId") UUID accountId,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.debug("REST request by {} to soft-delete account {} from IP {}", actorId, accountId, clientIp);
        DeactivateAccountResponse response = operatorAccountService.softDelete(accountId, actorId, clientIp);
        return ResponseEntity.ok(response);
    }

    private String resolveActorId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system-admin";
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
