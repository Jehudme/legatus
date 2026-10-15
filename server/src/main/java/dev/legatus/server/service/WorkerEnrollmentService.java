package dev.legatus.server.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.Argon2idService;
import dev.legatus.server.security.Ed25519FingerprintService;
import dev.legatus.server.security.RateLimiterService;
import dev.legatus.server.security.TicketBrokerService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service managing worker node quarantine enrollment, attestation approval,
 * single-read token delivery, and ephemeral WebSocket ticket exchange.
 * <p>
 * Complies with {@code ADR-0005}, {@code server-spec.md} Section 4.1 &amp; 4.2, and {@code api-endpoint.md} Section 2:
 * <ul>
 *   <li>Enforces 5 req/min/IP rate limiting and 50-node pending queue cap (HTTP 429).</li>
 *   <li>Computes SHA-256 hex fingerprints and deterministic 3-word visual codes.</li>
 *   <li>Manages single-read secret token delivery upon operator approval.</li>
 *   <li>Verifies Argon2id credentials on bounded carrier pool and issues 30s tickets.</li>
 *   <li>Records statutory WORM audit trail events for all state transitions.</li>
 * </ul>
 */
@Service
public class WorkerEnrollmentService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerEnrollmentService.class);

    public static final int MAX_PENDING_QUEUE_CAP = 50;
    public static final Duration ENROLLMENT_TTL = Duration.ofMinutes(15);
    public static final long DEFAULT_POLL_INTERVAL_MS = 3000L;
    public static final String DEFAULT_CLUSTER_NAME = "production-us-east";

    private final WorkerNodeRepository workerNodeRepository;
    private final Ed25519FingerprintService fingerprintService;
    private final Argon2idService argon2idService;
    private final TicketBrokerService ticketBrokerService;
    private final RateLimiterService rateLimiterService;
    private final WormAuditService wormAuditService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * In-memory cache for single-read secret token delivery upon operator approval.
     */
    private final Cache<String, String> singleReadTokenCache = Caffeine.newBuilder()
        .expireAfterWrite(ENROLLMENT_TTL)
        .maximumSize(10_000)
        .build();

    private final org.springframework.beans.factory.ObjectProvider<dev.legatus.server.service.studio.StudioEventPublisher> studioEventPublisherProvider;

    @org.springframework.beans.factory.annotation.Autowired
    public WorkerEnrollmentService(
        WorkerNodeRepository workerNodeRepository,
        Ed25519FingerprintService fingerprintService,
        Argon2idService argon2idService,
        TicketBrokerService ticketBrokerService,
        RateLimiterService rateLimiterService,
        WormAuditService wormAuditService,
        org.springframework.beans.factory.ObjectProvider<dev.legatus.server.service.studio.StudioEventPublisher> studioEventPublisherProvider
    ) {
        this.workerNodeRepository = workerNodeRepository;
        this.fingerprintService = fingerprintService;
        this.argon2idService = argon2idService;
        this.ticketBrokerService = ticketBrokerService;
        this.rateLimiterService = rateLimiterService;
        this.wormAuditService = wormAuditService;
        this.studioEventPublisherProvider = studioEventPublisherProvider;
    }

    public WorkerEnrollmentService(
        WorkerNodeRepository workerNodeRepository,
        Ed25519FingerprintService fingerprintService,
        Argon2idService argon2idService,
        TicketBrokerService ticketBrokerService,
        RateLimiterService rateLimiterService,
        WormAuditService wormAuditService
    ) {
        this(workerNodeRepository, fingerprintService, argon2idService, ticketBrokerService, rateLimiterService, wormAuditService, null);
    }

    /**
     * DTO for incoming registration request.
     */
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterRequest(
        @com.fasterxml.jackson.annotation.JsonProperty("worker_id") String workerId,
        @com.fasterxml.jackson.annotation.JsonProperty("hostname") String hostname,
        @com.fasterxml.jackson.annotation.JsonProperty("os") String os,
        @com.fasterxml.jackson.annotation.JsonProperty("architecture") String architecture,
        @com.fasterxml.jackson.annotation.JsonProperty("agent_version") String agentVersion,
        @com.fasterxml.jackson.annotation.JsonProperty("ed25519_public_key") String ed25519PublicKey,
        @com.fasterxml.jackson.annotation.JsonProperty("hardware") HardwareProfile hardware,
        @com.fasterxml.jackson.annotation.JsonProperty("allocatable_capacity") AllocatableCapacity allocatableCapacity
    ) {
        @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record HardwareProfile(
            @com.fasterxml.jackson.annotation.JsonProperty("physical_cores") Integer physicalCores,
            @com.fasterxml.jackson.annotation.JsonProperty("logical_cores") Integer logicalCores,
            @com.fasterxml.jackson.annotation.JsonProperty("total_memory_bytes") Long totalMemoryBytes,
            @com.fasterxml.jackson.annotation.JsonProperty("gpu_present") Boolean gpuPresent,
            @com.fasterxml.jackson.annotation.JsonProperty("gpu_model") String gpuModel
        ) {}

        @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record AllocatableCapacity(
            @com.fasterxml.jackson.annotation.JsonProperty("allocatable_cores") Integer allocatableCores,
            @com.fasterxml.jackson.annotation.JsonProperty("max_memory_bytes") Long maxMemoryBytes,
            @com.fasterxml.jackson.annotation.JsonProperty("max_instruction_budget_per_task") Long maxInstructionBudgetPerTask
        ) {}
    }

    /**
     * DTO for registration acceptance response (HTTP 202 Accepted).
     */
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("enrollment_id") String enrollmentId,
        @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
        @com.fasterxml.jackson.annotation.JsonProperty("fingerprint_hex") String fingerprintHex,
        @com.fasterxml.jackson.annotation.JsonProperty("visual_code") String visualCode,
        @com.fasterxml.jackson.annotation.JsonProperty("poll_interval_ms") long pollIntervalMs,
        @com.fasterxml.jackson.annotation.JsonProperty("expires_at") long expiresAt
    ) {}

    /**
     * DTO for registration status poll response.
     */
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StatusResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("enrollment_id") String enrollmentId,
        @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
        @com.fasterxml.jackson.annotation.JsonProperty("secret_token") String secretToken,
        @com.fasterxml.jackson.annotation.JsonProperty("assigned_cluster") String assignedCluster
    ) {}

    /**
     * DTO for ticket exchange authentication request.
     */
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthRequest(
        @com.fasterxml.jackson.annotation.JsonProperty("worker_id") String workerId,
        @com.fasterxml.jackson.annotation.JsonProperty("secret_token") String secretToken,
        @com.fasterxml.jackson.annotation.JsonProperty("hostname") String hostname,
        @com.fasterxml.jackson.annotation.JsonProperty("os") String os,
        @com.fasterxml.jackson.annotation.JsonProperty("architecture") String architecture,
        @com.fasterxml.jackson.annotation.JsonProperty("agent_version") String agentVersion,
        @com.fasterxml.jackson.annotation.JsonProperty("allocatable_capacity") RegisterRequest.AllocatableCapacity allocatableCapacity
    ) {}

    /**
     * DTO for ticket exchange authentication response (HTTP 200 OK).
     */
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("ticket") String ticket,
        @com.fasterxml.jackson.annotation.JsonProperty("ticket_ttl_seconds") int ticketTtlSeconds,
        @com.fasterxml.jackson.annotation.JsonProperty("websocket_url") String websocketUrl,
        @com.fasterxml.jackson.annotation.JsonProperty("heartbeat_interval_ms") long heartbeatIntervalMs,
        @com.fasterxml.jackson.annotation.JsonProperty("heartbeat_timeout_ms") long heartbeatTimeoutMs,
        @com.fasterxml.jackson.annotation.JsonProperty("assigned_cluster") String assignedCluster
    ) {}

    /**
     * Submits quarantine worker registration (POST /api/v1/workers/register).
     */
    @Transactional
    public RegisterResponse registerWorker(RegisterRequest request, String clientIp) {
        Objects.requireNonNull(request, "request cannot be null");

        // 1. Enforce 5 req/min rate limit per IP
        if (!rateLimiterService.tryAcquire(clientIp)) {
            LOG.warn("Worker registration rate limit exceeded for IP: {}", clientIp);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded (max 5 req/min per IP)");
        }

        // 2. Enforce 50-node pending quarantine queue cap
        long pendingCount = workerNodeRepository.countByState(WorkerState.PENDING_APPROVAL);
        if (pendingCount >= MAX_PENDING_QUEUE_CAP) {
            LOG.warn("Quarantine pending queue full ({} nodes)", pendingCount);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quarantine pending queue full (max 50 nodes)");
        }

        // 3. Derive cryptographic fingerprint and visual code
        Ed25519FingerprintService.FingerprintResult fpResult;
        try {
            fpResult = fingerprintService.deriveFromBase64(request.ed25519PublicKey());
        } catch (Exception e) {
            LOG.error("Failed to parse Ed25519 public key: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Ed25519 public key: " + e.getMessage());
        }

        String enrollmentId = "enr-" + UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ENROLLMENT_TTL);

        // 4. Create or update WorkerNode in PENDING_APPROVAL
        WorkerNode worker = workerNodeRepository.findById(request.workerId())
            .orElseGet(WorkerNode::new);

        worker.setWorkerId(request.workerId());
        worker.setEnrollmentId(enrollmentId);
        worker.setHostname(request.hostname());
        worker.setOs(parseOperatingSystem(request.os()));
        worker.setArch(parseCpuArchitecture(request.architecture()));
        worker.setAgentVersion(request.agentVersion() != null ? request.agentVersion() : "1.0.0");
        worker.setEd25519Pubkey(request.ed25519PublicKey());
        worker.setFingerprint(fpResult.fingerprintHex());
        worker.setVisualCode(fpResult.visualCode());
        worker.setState(WorkerState.PENDING_APPROVAL);
        worker.setSourceIp(clientIp);
        worker.setDraining(false);
        worker.setCreatedAt(now);
        worker.setEnrollmentExpiresAt(expiresAt);

        if (request.hardware() != null) {
            worker.setPhysicalCores(request.hardware().physicalCores() != null ? request.hardware().physicalCores() : 1);
            worker.setLogicalCores(request.hardware().logicalCores() != null ? request.hardware().logicalCores() : 1);
            worker.setTotalMemoryBytes(request.hardware().totalMemoryBytes() != null ? request.hardware().totalMemoryBytes() : 1073741824L);
            worker.setGpuPresent(request.hardware().gpuPresent() != null ? request.hardware().gpuPresent() : false);
            worker.setGpuModel(request.hardware().gpuModel());
        } else {
            worker.setPhysicalCores(1);
            worker.setLogicalCores(1);
            worker.setTotalMemoryBytes(1073741824L);
            worker.setGpuPresent(false);
        }

        if (request.allocatableCapacity() != null) {
            worker.setAllocatableCores(request.allocatableCapacity().allocatableCores() != null ? request.allocatableCapacity().allocatableCores() : 1);
            worker.setMaxMemoryBytes(request.allocatableCapacity().maxMemoryBytes() != null ? request.allocatableCapacity().maxMemoryBytes() : 1073741824L);
            worker.setMaxInstructionBudget(request.allocatableCapacity().maxInstructionBudgetPerTask() != null ? request.allocatableCapacity().maxInstructionBudgetPerTask() : 50_000_000L);
        } else {
            worker.setAllocatableCores(1);
            worker.setMaxMemoryBytes(1073741824L);
            worker.setMaxInstructionBudget(50_000_000L);
        }

        workerNodeRepository.save(worker);

        // 5. Append WORM audit event
        wormAuditService.recordEvent(
            request.workerId(),
            "WORKER_NODE",
            "WORKER_ENROLLMENT_REQUESTED",
            "WORKER",
            request.workerId(),
            AuditActionStatus.SUCCESS,
            String.format("{\"fingerprint\":\"%s\",\"visual_code\":\"%s\",\"enrollment_id\":\"%s\"}",
                fpResult.fingerprintHex(), fpResult.visualCode(), enrollmentId),
            clientIp,
            "SOC2_CC6.8"
        );

        LOG.info("Quarantined worker enrolled: id={}, fp={}, visual_code={}",
            request.workerId(), fpResult.fingerprintHex(), fpResult.visualCode());

        if (studioEventPublisherProvider != null) {
            studioEventPublisherProvider.ifAvailable(pub -> {
                pub.publishEnrollmentRequested(new dev.legatus.server.service.dto.lstcp.EventEnrollmentRequestedFrame.Payload(
                    enrollmentId,
                    request.workerId(),
                    request.hostname(),
                    clientIp,
                    worker.getOs().name().toLowerCase(),
                    worker.getArch().name().toLowerCase(),
                    fpResult.fingerprintHex(),
                    fpResult.visualCode(),
                    worker.getAllocatableCores(),
                    worker.getMaxMemoryBytes(),
                    expiresAt.toEpochMilli()
                ));
            });
        }

        return new RegisterResponse(
            enrollmentId,
            WorkerState.PENDING_APPROVAL.name(),
            fpResult.fingerprintHex(),
            fpResult.visualCode(),
            DEFAULT_POLL_INTERVAL_MS,
            expiresAt.toEpochMilli()
        );
    }

    /**
     * Polls enrollment status with single-read secret token delivery (GET /api/v1/workers/register/{enrollment_id}/status).
     */
    @Transactional
    public StatusResponse getEnrollmentStatus(String enrollmentId) {
        WorkerNode worker = workerNodeRepository.findByEnrollmentId(enrollmentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment ID not found: " + enrollmentId));

        Instant now = Instant.now();

        // Check 15-minute expiration for pending nodes
        if (worker.getState() == WorkerState.PENDING_APPROVAL) {
            if (worker.getEnrollmentExpiresAt() != null && now.isAfter(worker.getEnrollmentExpiresAt())) {
                worker.setState(WorkerState.EXPIRED);
                workerNodeRepository.save(worker);
                return new StatusResponse(enrollmentId, WorkerState.EXPIRED.name(), null, null);
            }
            return new StatusResponse(enrollmentId, WorkerState.PENDING_APPROVAL.name(), null, null);
        }

        if (worker.getState() == WorkerState.APPROVED) {
            // Single-read delivery of plaintext token
            String secretToken = singleReadTokenCache.asMap().remove(enrollmentId);
            return new StatusResponse(
                enrollmentId,
                WorkerState.APPROVED.name(),
                secretToken,
                worker.getAssignedCluster() != null ? worker.getAssignedCluster() : DEFAULT_CLUSTER_NAME
            );
        }

        return new StatusResponse(enrollmentId, worker.getState().name(), null, null);
    }

    /**
     * Approves quarantined worker, generating 256-bit token and Argon2id hash.
     */
    @Transactional
    public String approveWorker(String idOrEnrollmentId, String operatorUsername, String assignedCluster) {
        WorkerNode worker = workerNodeRepository.findById(idOrEnrollmentId)
            .or(() -> workerNodeRepository.findByEnrollmentId(idOrEnrollmentId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found: " + idOrEnrollmentId));

        // Generate 256-bit secret token (64 hex characters)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String secretToken = "wk_sec_" + HexFormat.of().formatHex(tokenBytes);

        // Hash token via Argon2id
        String secretHash = argon2idService.hash(secretToken);
        String previousState = worker.getState() != null ? worker.getState().name() : "PENDING_APPROVAL";
        worker.setSecretHash(secretHash);
        worker.setState(WorkerState.APPROVED);
        worker.setAssignedCluster(assignedCluster != null ? assignedCluster : DEFAULT_CLUSTER_NAME);

        workerNodeRepository.save(worker);

        // Put in single-read cache for the worker's next status poll
        if (worker.getEnrollmentId() != null) {
            singleReadTokenCache.put(worker.getEnrollmentId(), secretToken);
        }

        wormAuditService.recordEvent(
            operatorUsername,
            "OPERATOR",
            "WORKER_APPROVED",
            "WORKER",
            worker.getWorkerId(),
            AuditActionStatus.SUCCESS,
            String.format("{\"assigned_cluster\":\"%s\"}", worker.getAssignedCluster()),
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        if (studioEventPublisherProvider != null) {
            studioEventPublisherProvider.ifAvailable(pub -> {
                pub.publishWorkerStateChanged(new dev.legatus.server.service.dto.lstcp.EventWorkerStateChangedFrame.Payload(
                    worker.getWorkerId(),
                    previousState,
                    worker.getState().name(),
                    0.0,
                    0L,
                    0
                ));
            });
        }

        LOG.info("Worker {} approved by operator {}", worker.getWorkerId(), operatorUsername);
        return secretToken;
    }

    /**
     * Rejects quarantined worker and marks state REVOKED.
     */
    @Transactional
    public void rejectWorker(String idOrEnrollmentId, String operatorUsername, String reason, Boolean blacklistIp) {
        WorkerNode worker = workerNodeRepository.findById(idOrEnrollmentId)
            .or(() -> workerNodeRepository.findByEnrollmentId(idOrEnrollmentId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found: " + idOrEnrollmentId));

        String previousState = worker.getState() != null ? worker.getState().name() : "PENDING_APPROVAL";
        worker.setState(WorkerState.REVOKED);
        workerNodeRepository.save(worker);

        wormAuditService.recordEvent(
            operatorUsername,
            "OPERATOR",
            "WORKER_REJECTED",
            "WORKER",
            worker.getWorkerId(),
            AuditActionStatus.SUCCESS,
            String.format("{\"reason\":\"%s\",\"blacklist_ip\":%b}", reason != null ? reason : "Operator rejected", Boolean.TRUE.equals(blacklistIp)),
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        if (studioEventPublisherProvider != null) {
            studioEventPublisherProvider.ifAvailable(pub -> {
                pub.publishWorkerStateChanged(new dev.legatus.server.service.dto.lstcp.EventWorkerStateChangedFrame.Payload(
                    worker.getWorkerId(),
                    previousState,
                    worker.getState().name(),
                    0.0,
                    0L,
                    0
                ));
            });
        }

        LOG.info("Worker {} rejected by operator {}: reason={}", worker.getWorkerId(), operatorUsername, reason);
    }

    /**
     * Toggles drain mode on an active worker.
     */
    @Transactional
    public boolean drainWorker(String workerId, String operatorUsername) {
        WorkerNode worker = workerNodeRepository.findById(workerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found: " + workerId));

        String previousState = worker.getState() != null ? worker.getState().name() : "ONLINE";
        boolean newDraining = !Boolean.TRUE.equals(worker.getDraining());
        worker.setDraining(newDraining);
        worker.setState(newDraining ? WorkerState.DRAINING : WorkerState.ONLINE);
        workerNodeRepository.save(worker);

        wormAuditService.recordEvent(
            operatorUsername,
            "OPERATOR",
            newDraining ? "WORKER_DRAINED" : "WORKER_UNDRAINED",
            "WORKER",
            workerId,
            AuditActionStatus.SUCCESS,
            String.format("{\"draining\":%b}", newDraining),
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        if (studioEventPublisherProvider != null) {
            studioEventPublisherProvider.ifAvailable(pub -> {
                pub.publishWorkerStateChanged(new dev.legatus.server.service.dto.lstcp.EventWorkerStateChangedFrame.Payload(
                    worker.getWorkerId(),
                    previousState,
                    worker.getState().name(),
                    0.0,
                    0L,
                    0
                ));
            });
        }

        LOG.info("Worker {} draining toggled to {} by {}", workerId, newDraining, operatorUsername);
        return newDraining;
    }

    /**
     * Revokes a worker's credentials and marks state REVOKED.
     */
    @Transactional
    public void revokeWorker(String workerId, String operatorUsername) {
        WorkerNode worker = workerNodeRepository.findById(workerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found: " + workerId));

        String previousState = worker.getState() != null ? worker.getState().name() : "ONLINE";
        worker.setState(WorkerState.REVOKED);
        workerNodeRepository.save(worker);

        wormAuditService.recordEvent(
            operatorUsername,
            "OPERATOR",
            "WORKER_REVOKED",
            "WORKER",
            workerId,
            AuditActionStatus.SUCCESS,
            "{\"action\":\"REVOKE\"}",
            "127.0.0.1",
            "SOC2_CC6.8"
        );

        if (studioEventPublisherProvider != null) {
            studioEventPublisherProvider.ifAvailable(pub -> {
                pub.publishWorkerStateChanged(new dev.legatus.server.service.dto.lstcp.EventWorkerStateChangedFrame.Payload(
                    worker.getWorkerId(),
                    previousState,
                    worker.getState().name(),
                    0.0,
                    0L,
                    0
                ));
            });
        }

        LOG.info("Worker {} revoked by operator {}", workerId, operatorUsername);
    }

    /**
     * Authenticates worker and issues 30-second single-use ticket (POST /api/v1/auth/worker).
     */
    @Transactional
    public AuthResponse authenticateWorker(AuthRequest request, String clientIp) {
        Objects.requireNonNull(request, "request cannot be null");

        WorkerNode worker = workerNodeRepository.findById(request.workerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid worker credentials"));

        if (worker.getState() == WorkerState.REVOKED || worker.getState() == WorkerState.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Worker node is " + worker.getState());
        }
        if (worker.getState() == WorkerState.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Worker node is still quarantined (PENDING_APPROVAL)");
        }
        if (worker.getSecretHash() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Worker has no secret hash configured");
        }

        // Verify Argon2id hash on bounded carrier pool
        boolean verified = argon2idService.verify(request.secretToken(), worker.getSecretHash());
        if (!verified) {
            LOG.warn("Failed authentication attempt for worker {}", request.workerId());
            wormAuditService.recordEvent(
                request.workerId(),
                "WORKER_NODE",
                "WORKER_AUTH_FAILED",
                "WORKER",
                request.workerId(),
                AuditActionStatus.FAILED,
                "{\"reason\":\"Invalid secret_token\"}",
                clientIp,
                "SOC2_CC6.8"
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid secret token");
        }

        // Update allocatable capacity and lastSeenAt
        int cores = worker.getAllocatableCores() != null ? worker.getAllocatableCores() : 1;
        long memory = worker.getMaxMemoryBytes() != null ? worker.getMaxMemoryBytes() : 1073741824L;
        long budget = worker.getMaxInstructionBudget() != null ? worker.getMaxInstructionBudget() : 50_000_000L;

        if (request.allocatableCapacity() != null) {
            if (request.allocatableCapacity().allocatableCores() != null) {
                cores = request.allocatableCapacity().allocatableCores();
                worker.setAllocatableCores(cores);
            }
            if (request.allocatableCapacity().maxMemoryBytes() != null) {
                memory = request.allocatableCapacity().maxMemoryBytes();
                worker.setMaxMemoryBytes(memory);
            }
            if (request.allocatableCapacity().maxInstructionBudgetPerTask() != null) {
                budget = request.allocatableCapacity().maxInstructionBudgetPerTask();
                worker.setMaxInstructionBudget(budget);
            }
        }

        worker.setLastSeenAt(Instant.now());
        workerNodeRepository.save(worker);

        // Issue 30s ticket in Caffeine
        String ticket = ticketBrokerService.issueWorkerTicket(request.workerId(), cores, memory, budget);

        wormAuditService.recordEvent(
            request.workerId(),
            "WORKER_NODE",
            "WORKER_AUTHENTICATED",
            "WORKER",
            request.workerId(),
            AuditActionStatus.SUCCESS,
            String.format("{\"ticket\":\"%s\"}", ticket),
            clientIp,
            "SOC2_CC6.8"
        );

        return new AuthResponse(
            ticket,
            30,
            "/ws/worker",
            5000L,
            15000L,
            worker.getAssignedCluster() != null ? worker.getAssignedCluster() : DEFAULT_CLUSTER_NAME
        );
    }

    private OperatingSystem parseOperatingSystem(String os) {
        if (os == null) return OperatingSystem.LINUX;
        String lower = os.trim().toLowerCase();
        if (lower.contains("win")) return OperatingSystem.WINDOWS;
        if (lower.contains("mac") || lower.contains("darwin")) return OperatingSystem.MACOS;
        return OperatingSystem.LINUX;
    }

    private CpuArchitecture parseCpuArchitecture(String arch) {
        if (arch == null) return CpuArchitecture.X86_64;
        String lower = arch.trim().toLowerCase();
        if (lower.contains("arm") || lower.contains("aarch64")) return CpuArchitecture.AARCH64;
        return CpuArchitecture.X86_64;
    }
}
