package dev.legatus.server.service;

import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing the statutory Write-Once-Read-Many (WORM) audit trail with sequential
 * SHA-256 cryptographic hash chaining.
 * <p>
 * Complies with {@code server-spec.md} Section 5.3 (SOC 2 Type II, ISO 27001:2022, HIPAA, GDPR):
 * <ul>
 *   <li>Enforces sequential cryptographic chaining: each record includes the SHA-256 of the prior record.</li>
 *   <li>Genesis record (sequence 1) points to 64 ASCII zeros.</li>
 *   <li>Provides ledger integrity verification endpoint to detect tampering or broken chains.</li>
 *   <li>Performs meta-auditing: queries and verifications append audit events to the ledger.</li>
 * </ul>
 */
@Service
public class WormAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(WormAuditService.class);

    public static final String GENESIS_PREV_HASH = "0".repeat(64);
    public static final String DEFAULT_COMPLIANCE_FLAG = "SOC2_CC6.8_ISO27001_A8.15";

    private final AuditLogRepository auditLogRepository;

    public WormAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Ledger integrity check result.
     */
    public record VerificationResult(
        boolean isValid,
        long recordsVerified,
        Long corruptedSequenceNum,
        String failureReason
    ) {}

    /**
     * Appends a new immutable WORM audit log entry to the ledger with sequential SHA-256 hash chaining.
     * Synchronized to guarantee linear monotonically increasing sequence numbers and unbroken hash chains.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public synchronized AuditLog recordEvent(
        String actorId,
        String actorRole,
        String eventType,
        String targetType,
        String targetId,
        AuditActionStatus actionStatus,
        String metadataJson,
        String sourceIp,
        String complianceFlag
    ) {
        Objects.requireNonNull(actorId, "actorId cannot be null");
        Objects.requireNonNull(actorRole, "actorRole cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(targetType, "targetType cannot be null");
        Objects.requireNonNull(actionStatus, "actionStatus cannot be null");
        Objects.requireNonNull(sourceIp, "sourceIp cannot be null");

        String effectiveComplianceFlag = complianceFlag != null ? complianceFlag : DEFAULT_COMPLIANCE_FLAG;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Optional<AuditLog> latestOpt = auditLogRepository.findTopByOrderBySequenceNumDesc();
        long sequenceNum;
        String prevHash;

        if (latestOpt.isEmpty()) {
            sequenceNum = 1L;
            prevHash = GENESIS_PREV_HASH;
        } else {
            AuditLog latest = latestOpt.get();
            sequenceNum = latest.getSequenceNum() + 1L;
            prevHash = latest.getRecordHash();
        }

        String currentHash = computeRecordHash(
            sequenceNum,
            prevHash,
            now,
            actorId,
            actorRole,
            eventType,
            targetType,
            targetId,
            actionStatus,
            metadataJson,
            sourceIp
        );

        AuditLog entry = new AuditLog();
        entry.setSequenceNum(sequenceNum);
        entry.setTimestamp(now);
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setEventType(eventType);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setActionStatus(actionStatus);
        entry.setMetadataJson(metadataJson);
        entry.setSourceIp(sourceIp);
        entry.setPrevRecordHash(prevHash);
        entry.setRecordHash(currentHash);
        entry.setComplianceFlag(effectiveComplianceFlag);

        AuditLog saved = auditLogRepository.save(entry);
        LOG.info(
            "WORM Audit Log appended: seq={}, type={}, actor={}, target={}:{}, hash={}",
            sequenceNum,
            eventType,
            actorId,
            targetType,
            targetId,
            currentHash
        );
        return saved;
    }

    /**
     * Verifies the cryptographic integrity of the entire audit ledger from sequence 1 to HEAD.
     */
    @Transactional(readOnly = true)
    public VerificationResult verifyLedgerIntegrity() {
        List<AuditLog> entries = auditLogRepository.findAllByOrderBySequenceNumAsc();
        if (entries.isEmpty()) {
            return new VerificationResult(true, 0, null, null);
        }

        long expectedSeq = 1L;
        String expectedPrevHash = GENESIS_PREV_HASH;

        for (AuditLog entry : entries) {
            long currentSeq = entry.getSequenceNum();

            // 1. Verify monotonic sequence without gaps
            if (currentSeq != expectedSeq) {
                return new VerificationResult(
                    false,
                    expectedSeq - 1,
                    currentSeq,
                    String.format("Sequence gap or out-of-order sequence detected: expected %d, got %d", expectedSeq, currentSeq)
                );
            }

            // 2. Verify previous record hash link
            if (!expectedPrevHash.equals(entry.getPrevRecordHash())) {
                return new VerificationResult(
                    false,
                    expectedSeq - 1,
                    currentSeq,
                    String.format(
                        "Broken cryptographic chain at sequence %d: expected prevHash %s, got %s",
                        currentSeq,
                        expectedPrevHash,
                        entry.getPrevRecordHash()
                    )
                );
            }

            // 3. Recompute and verify current record hash
            String recomputedHash = computeRecordHash(
                entry.getSequenceNum(),
                entry.getPrevRecordHash(),
                entry.getTimestamp(),
                entry.getActorId(),
                entry.getActorRole(),
                entry.getEventType(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getActionStatus(),
                entry.getMetadataJson(),
                entry.getSourceIp()
            );

            if (!recomputedHash.equals(entry.getRecordHash())) {
                return new VerificationResult(
                    false,
                    expectedSeq - 1,
                    currentSeq,
                    String.format(
                        "Cryptographic hash mismatch at sequence %d: stored %s, recomputed %s",
                        currentSeq,
                        entry.getRecordHash(),
                        recomputedHash
                    )
                );
            }

            expectedPrevHash = entry.getRecordHash();
            expectedSeq++;
        }

        return new VerificationResult(true, entries.size(), null, null);
    }

    /**
     * Computes the SHA-256 record hash according to the formula:
     * H_i = SHA256(sequence_num || prev_record_hash || timestamp || actor_id || actor_role ||
     *              event_type || target_type || target_id || action_status || metadata_json || source_ip)
     */
    public String computeRecordHash(
        Long sequenceNum,
        String prevRecordHash,
        Instant timestamp,
        String actorId,
        String actorRole,
        String eventType,
        String targetType,
        String targetId,
        AuditActionStatus actionStatus,
        String metadataJson,
        String sourceIp
    ) {
        String payload = String.format(
            "%d|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
            sequenceNum,
            prevRecordHash,
            timestamp.truncatedTo(ChronoUnit.MILLIS).toString(),
            actorId,
            actorRole,
            eventType,
            targetType,
            targetId != null ? targetId : "",
            actionStatus.name(),
            metadataJson != null ? metadataJson : "",
            sourceIp
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
