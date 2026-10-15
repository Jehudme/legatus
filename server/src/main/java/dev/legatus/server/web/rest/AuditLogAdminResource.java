package dev.legatus.server.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.service.WormAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Statutory WORM compliance REST controller for querying, verifying, and exporting immutable audit logs.
 * <p>
 * Complies with {@code server-spec.md} Section 5.3 and {@code api-endpoint.md} Section 7:
 * <ul>
 *   <li>{@code GET /api/v1/admin/audit-logs} (Meta-audited query)</li>
 *   <li>{@code GET /api/v1/admin/audit-logs/verify} (Sequential hash chain verifier)</li>
 *   <li>{@code DELETE /api/v1/admin/audit-logs/**} (HTTP 405 Method Not Allowed - Immutable WORM)</li>
 *   <li>{@code PUT/PATCH /api/v1/admin/audit-logs/**} (HTTP 405 Method Not Allowed)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogAdminResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogAdminResource.class);

    public static final String IMMUTABLE_VIOLATION_MESSAGE =
        "Statutory Compliance Violation: Audit logs are legally immutable under SOC 2 / ISO 27001 / HIPAA / GDPR and cannot be deleted, purged, or modified.";

    private final WormAuditService wormAuditService;
    private final AuditLogRepository auditLogRepository;

    public AuditLogAdminResource(WormAuditService wormAuditService, AuditLogRepository auditLogRepository) {
        this.wormAuditService = wormAuditService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * DTO for hash chain verification response.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VerifyResponse(
        @JsonProperty("status") String status,
        @JsonProperty("verified_records_count") long verifiedRecordsCount,
        @JsonProperty("from_sequence") Long fromSequence,
        @JsonProperty("to_sequence") Long toSequence,
        @JsonProperty("genesis_hash") String genesisHash,
        @JsonProperty("head_hash") String headHash,
        @JsonProperty("chain_integrity") boolean chainIntegrity,
        @JsonProperty("tamper_detected") boolean tamperDetected,
        @JsonProperty("discrepancy") String discrepancy
    ) {}

    /**
     * DTO for paginated query response.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageResponse<T>(
        @JsonProperty("content") List<T> content,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
    ) {}

    /**
     * {@code GET /api/v1/admin/audit-logs/verify} : Executes an integrity verification scan across the audit log hash chain.
     */
    @GetMapping("/verify")
    public ResponseEntity<VerifyResponse> verifyChain(Authentication authentication, HttpServletRequest request) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(request);

        WormAuditService.VerificationResult result = wormAuditService.verifyLedgerIntegrity();

        Optional<AuditLog> latestOpt = auditLogRepository.findTopByOrderBySequenceNumDesc();
        String headHash = latestOpt.map(AuditLog::getRecordHash).orElse(WormAuditService.GENESIS_PREV_HASH);
        Long maxSeq = latestOpt.map(AuditLog::getSequenceNum).orElse(0L);

        // Meta-auditing: append AUDIT_LOG_VERIFIED event
        wormAuditService.recordEvent(
            actorId,
            "CLUSTER_ADMIN",
            "AUDIT_LOG_VERIFIED",
            "AUDIT_LOGS",
            "ALL",
            result.isValid() ? AuditActionStatus.SUCCESS : AuditActionStatus.FAILED,
            String.format("{\"verified_count\":%d,\"is_valid\":%b}", result.recordsVerified(), result.isValid()),
            clientIp,
            "SOC2_CC6.8_VERIFY"
        );

        if (!result.isValid()) {
            LOG.error("Cryptographic audit chain corruption detected: {}", result.failureReason());
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ERR_AUDIT_CHAIN_CORRUPTED: " + result.failureReason()
            );
        }

        VerifyResponse response = new VerifyResponse(
            "VALID",
            result.recordsVerified(),
            result.recordsVerified() > 0 ? 1L : null,
            result.recordsVerified() > 0 ? maxSeq : null,
            WormAuditService.GENESIS_PREV_HASH,
            headHash,
            true,
            false,
            null
        );
        return ResponseEntity.ok(response);
    }

    /**
     * {@code GET /api/v1/admin/audit-logs} : Query immutable audit logs with meta-auditing.
     */
    @GetMapping("")
    public ResponseEntity<PageResponse<AuditLog>> queryAuditLogs(
        Pageable pageable,
        Authentication authentication,
        HttpServletRequest request
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(request);

        Page<AuditLog> page = auditLogRepository.findAll(pageable);

        // Meta-auditing: append AUDIT_LOG_ACCESSED event
        wormAuditService.recordEvent(
            actorId,
            "CLUSTER_ADMIN",
            "AUDIT_LOG_ACCESSED",
            "AUDIT_LOGS",
            "PAGE_" + pageable.getPageNumber(),
            AuditActionStatus.SUCCESS,
            String.format("{\"page\":%d,\"size\":%d}", pageable.getPageNumber(), pageable.getPageSize()),
            clientIp,
            "SOC2_CC6.8_META_AUDIT"
        );

        PageResponse<AuditLog> response = new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Absolute REST rejection of DELETE on audit logs (Statutory Zero-Deletion Invariant).
     */
    @DeleteMapping("/**")
    public void rejectDelete(Authentication authentication, HttpServletRequest request) {
        recordTamperAttempt("DELETE", authentication, request);
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, IMMUTABLE_VIOLATION_MESSAGE);
    }

    /**
     * Absolute REST rejection of PUT on audit logs (Statutory Zero-Modification Invariant).
     */
    @PutMapping("/**")
    public void rejectPut(Authentication authentication, HttpServletRequest request) {
        recordTamperAttempt("PUT", authentication, request);
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, IMMUTABLE_VIOLATION_MESSAGE);
    }

    /**
     * Absolute REST rejection of PATCH on audit logs (Statutory Zero-Modification Invariant).
     */
    @PatchMapping("/**")
    public void rejectPatch(Authentication authentication, HttpServletRequest request) {
        recordTamperAttempt("PATCH", authentication, request);
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, IMMUTABLE_VIOLATION_MESSAGE);
    }

    private void recordTamperAttempt(String method, Authentication authentication, HttpServletRequest request) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(request);
        LOG.warn("SECURITY ALERT: Rejected unauthorized attempt to mutate/delete audit logs via HTTP {} from IP {}", method, clientIp);

        wormAuditService.recordEvent(
            actorId,
            "UNKNOWN",
            "AUDIT_LOG_TAMPER_ATTEMPT",
            "AUDIT_LOGS",
            request.getRequestURI(),
            AuditActionStatus.DENIED,
            String.format("{\"method\":\"%s\",\"uri\":\"%s\"}", method, request.getRequestURI()),
            clientIp,
            "SOC2_CC6.8_ALERT"
        );
    }

    private String resolveActorId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system-internal";
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
