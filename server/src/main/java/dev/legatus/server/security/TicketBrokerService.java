package dev.legatus.server.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-performance in-memory ticket broker enforcing 30-second single-use burn-on-read
 * WebSocket upgrade tickets for worker nodes and Studio cockpits.
 * <p>
 * Complies with {@code ADR-0005} and {@code server-spec.md} Section 4.2:
 * <ul>
 *   <li>Issues ephemeral single-use {@code ws_tkt_<uuid>} tickets for worker nodes.</li>
 *   <li>Issues ephemeral single-use {@code st_tkt_<uuid>} tickets for Studio operators.</li>
 *   <li>Stores tickets in lock-free Caffeine caches with strict 30-second TTL.</li>
 *   <li>Guarantees atomic burn-on-read during WebSocket handshake, preventing replay attacks.</li>
 * </ul>
 */
@Service
public class TicketBrokerService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketBrokerService.class);

    public static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final String WORKER_TICKET_PREFIX = "ws_tkt_";
    private static final String STUDIO_TICKET_PREFIX = "st_tkt_";

    private final Cache<String, WorkerTicket> workerTicketCache;
    private final Cache<String, StudioTicket> studioTicketCache;

    public TicketBrokerService() {
        this(TICKET_TTL);
    }

    /**
     * Visible for testing with custom TTL.
     */
    public TicketBrokerService(Duration ttl) {
        this.workerTicketCache = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(100_000)
            .build();

        this.studioTicketCache = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(50_000)
            .build();
    }

    /**
     * Immutable ticket payload holding declared worker node capacity and identity.
     */
    public record WorkerTicket(
        String ticketId,
        String workerId,
        int allocatableCores,
        long maxMemoryBytes,
        long maxInstructionBudgetPerTask,
        Instant issuedAt
    ) {}

    /**
     * Immutable ticket payload holding Studio operator identity and RBAC role.
     */
    public record StudioTicket(
        String ticketId,
        String operatorUsername,
        String role,
        Instant issuedAt
    ) {}

    /**
     * Issues a single-use 30-second ticket for worker WebSocket upgrade.
     *
     * @param workerId the authenticated worker identifier
     * @param allocatableCores cores allocatable to tasks
     * @param maxMemoryBytes maximum RAM allocatable to tasks
     * @param maxInstructionBudgetPerTask per-task Solix instruction budget ceiling
     * @return generated ticket string formatted as {@code ws_tkt_<uuid>}
     */
    public String issueWorkerTicket(
        String workerId,
        int allocatableCores,
        long maxMemoryBytes,
        long maxInstructionBudgetPerTask
    ) {
        Objects.requireNonNull(workerId, "workerId cannot be null");
        String ticketId = WORKER_TICKET_PREFIX + UUID.randomUUID();
        WorkerTicket payload = new WorkerTicket(
            ticketId,
            workerId,
            allocatableCores,
            maxMemoryBytes,
            maxInstructionBudgetPerTask,
            Instant.now()
        );
        workerTicketCache.put(ticketId, payload);
        LOG.debug("Issued worker ticket {} for node {}", ticketId, workerId);
        return ticketId;
    }

    /**
     * Atomically consumes a worker ticket (burn-on-read).
     *
     * @param ticketId the ticket string from query parameters
     * @return the ticket payload if valid and unconsumed, otherwise {@link Optional#empty()}
     */
    public Optional<WorkerTicket> consumeWorkerTicket(String ticketId) {
        if (ticketId == null || !ticketId.startsWith(WORKER_TICKET_PREFIX)) {
            return Optional.empty();
        }
        // Atomic removal via asMap().remove guarantees single-read replay prevention
        WorkerTicket payload = workerTicketCache.asMap().remove(ticketId);
        if (payload != null) {
            LOG.debug("Consumed worker ticket {} for node {}", ticketId, payload.workerId());
            return Optional.of(payload);
        }
        LOG.warn("Failed attempt to consume non-existent or already expired/burned worker ticket: {}", ticketId);
        return Optional.empty();
    }

    /**
     * Issues a single-use 30-second ticket for Studio operator WebSocket upgrade.
     *
     * @param operatorUsername the authenticated operator's username
     * @param role the RBAC role (e.g., CLUSTER_ADMIN, OPERATOR, AUDITOR)
     * @return generated ticket string formatted as {@code st_tkt_<uuid>}
     */
    public String issueStudioTicket(String operatorUsername, String role) {
        Objects.requireNonNull(operatorUsername, "operatorUsername cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        String ticketId = STUDIO_TICKET_PREFIX + UUID.randomUUID();
        StudioTicket payload = new StudioTicket(ticketId, operatorUsername, role, Instant.now());
        studioTicketCache.put(ticketId, payload);
        LOG.debug("Issued Studio ticket {} for operator {}", ticketId, operatorUsername);
        return ticketId;
    }

    /**
     * Atomically consumes a Studio operator ticket (burn-on-read).
     *
     * @param ticketId the ticket string from query parameters
     * @return the ticket payload if valid and unconsumed, otherwise {@link Optional#empty()}
     */
    public Optional<StudioTicket> consumeStudioTicket(String ticketId) {
        if (ticketId == null || !ticketId.startsWith(STUDIO_TICKET_PREFIX)) {
            return Optional.empty();
        }
        StudioTicket payload = studioTicketCache.asMap().remove(ticketId);
        if (payload != null) {
            LOG.debug("Consumed Studio ticket {} for operator {}", ticketId, payload.operatorUsername());
            return Optional.of(payload);
        }
        LOG.warn("Failed attempt to consume non-existent or already expired/burned studio ticket: {}", ticketId);
        return Optional.empty();
    }

    /**
     * Returns total active cached worker tickets (for observability and test assertions).
     */
    public long activeWorkerTicketCount() {
        return workerTicketCache.estimatedSize();
    }

    /**
     * Returns total active cached studio tickets (for observability and test assertions).
     */
    public long activeStudioTicketCount() {
        return studioTicketCache.estimatedSize();
    }
}
