package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TicketBrokerService}.
 */
class TicketBrokerServiceTest {

    @Test
    @DisplayName("Should issue and atomically burn worker ticket on read")
    void testWorkerTicketBurnOnRead() {
        TicketBrokerService broker = new TicketBrokerService();

        String ticket = broker.issueWorkerTicket("worker-node-42", 8, 16_000_000_000L, 50_000_000L);
        assertThat(ticket).startsWith("ws_tkt_");

        // First read: must succeed
        Optional<TicketBrokerService.WorkerTicket> payload1 = broker.consumeWorkerTicket(ticket);
        assertThat(payload1).isPresent();
        assertThat(payload1.get().workerId()).isEqualTo("worker-node-42");
        assertThat(payload1.get().allocatableCores()).isEqualTo(8);
        assertThat(payload1.get().maxMemoryBytes()).isEqualTo(16_000_000_000L);
        assertThat(payload1.get().maxInstructionBudgetPerTask()).isEqualTo(50_000_000L);

        // Second read: must be empty (burned on first read)
        Optional<TicketBrokerService.WorkerTicket> payload2 = broker.consumeWorkerTicket(ticket);
        assertThat(payload2).isEmpty();
    }

    @Test
    @DisplayName("Should reject invalid or null ticket tokens")
    void testRejectInvalidWorkerTicket() {
        TicketBrokerService broker = new TicketBrokerService();

        assertThat(broker.consumeWorkerTicket(null)).isEmpty();
        assertThat(broker.consumeWorkerTicket("")).isEmpty();
        assertThat(broker.consumeWorkerTicket("invalid-prefix-ticket")).isEmpty();
        assertThat(broker.consumeWorkerTicket("ws_tkt_non-existent-uuid")).isEmpty();
    }

    @Test
    @DisplayName("Should issue and atomically burn Studio ticket on read")
    void testStudioTicketBurnOnRead() {
        TicketBrokerService broker = new TicketBrokerService();

        String ticket = broker.issueStudioTicket("admin_user", "CLUSTER_ADMIN");
        assertThat(ticket).startsWith("st_tkt_");

        // First read: must succeed
        Optional<TicketBrokerService.StudioTicket> payload1 = broker.consumeStudioTicket(ticket);
        assertThat(payload1).isPresent();
        assertThat(payload1.get().operatorUsername()).isEqualTo("admin_user");
        assertThat(payload1.get().role()).isEqualTo("CLUSTER_ADMIN");

        // Second read: must be empty (burned)
        Optional<TicketBrokerService.StudioTicket> payload2 = broker.consumeStudioTicket(ticket);
        assertThat(payload2).isEmpty();
    }

    @Test
    @DisplayName("Should expire ticket after TTL")
    void testTicketExpiration() throws InterruptedException {
        // Create broker with 50ms TTL
        TicketBrokerService shortTtlBroker = new TicketBrokerService(Duration.ofMillis(50));

        String ticket = shortTtlBroker.issueWorkerTicket("worker-node-exp", 4, 8_000_000_000L, 10_000_000L);
        Thread.sleep(80); // Wait for TTL expiry

        Optional<TicketBrokerService.WorkerTicket> result = shortTtlBroker.consumeWorkerTicket(ticket);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should guarantee exactly one consumer succeeds under concurrent race")
    void testConcurrentBurnRace() throws InterruptedException {
        TicketBrokerService broker = new TicketBrokerService();
        String ticket = broker.issueWorkerTicket("race-worker", 4, 4_000_000_000L, 1_000_000L);

        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (broker.consumeWorkerTicket(ticket).isPresent()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // trigger all threads simultaneously
        doneLatch.await();
        executor.shutdown();

        // Exactly one consumer MUST win the atomic burn-on-read race
        assertThat(successCount.get()).isEqualTo(1);
    }
}
