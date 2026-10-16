package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.web.websocket.lctp.LctpWorkerSession;
import dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Integration test verifying that revoking a worker lease severs its active WebSocket session.
 * Complies with MS-012 Section 4.1.
 */
@IntegrationTest
@Transactional
class WorkerRevocationSessionIT {

    @Autowired
    private WorkerEnrollmentService workerEnrollmentService;

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    @Autowired
    private WorkerSessionRegistry workerSessionRegistry;

    @Test
    @DisplayName("Worker Revocation Socket Severance: Revoking worker lease closes active WebSocket session with code 1008")
    void testRevokeWorkerClosesWebSocketSession() throws Exception {
        String workerId = "wk-revocation-" + UUID.randomUUID().toString().substring(0, 8);
        WorkerNode worker = new WorkerNode()
            .workerId(workerId)
            .enrollmentId("enr-" + UUID.randomUUID())
            .hostname("revocation-worker.internal")
            .os(OperatingSystem.LINUX)
            .arch(CpuArchitecture.X86_64)
            .agentVersion("1.0.0")
            .ed25519Pubkey("MCowBQYDK2VwAyEA" + UUID.randomUUID().toString().replace("-", ""))
            .fingerprint("4E:8A:BC:31:09:FF:7D:12")
            .visualCode("TIGER-GALAXY-77")
            .state(WorkerState.ONLINE)
            .assignedCluster("production-us-east")
            .sourceIp("127.0.0.1")
            .physicalCores(4)
            .logicalCores(4)
            .allocatableCores(4)
            .totalMemoryBytes(8L * 1024 * 1024 * 1024)
            .maxMemoryBytes(8L * 1024 * 1024 * 1024)
            .maxInstructionBudget(100_000_000L)
            .gpuPresent(false)
            .draining(false)
            .active(true)
            .createdAt(Instant.now());
        worker = workerNodeRepository.saveAndFlush(worker);

        // Mock WebSocket session
        WebSocketSession mockWsSession = mock(WebSocketSession.class);
        when(mockWsSession.isOpen()).thenReturn(true);

        TicketBrokerService.WorkerTicket ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_" + UUID.randomUUID(),
            workerId,
            4,
            8L * 1024 * 1024 * 1024,
            50_000_000L,
            Instant.now()
        );

        String sessionId = "sess-" + UUID.randomUUID();
        LctpWorkerSession lctpSession = new LctpWorkerSession(sessionId, workerId, mockWsSession, ticket);
        workerSessionRegistry.register(lctpSession);

        assertThat(workerSessionRegistry.getByWorkerId(workerId)).isPresent();

        // Perform revocation
        workerEnrollmentService.revokeWorker(workerId, "security-admin");

        // Verify WebSocket session was closed with policy violation / lease revoked code 1008
        ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(mockWsSession, times(1)).close(statusCaptor.capture());

        CloseStatus capturedStatus = statusCaptor.getValue();
        assertThat(capturedStatus.getCode()).isEqualTo(1008);
        assertThat(capturedStatus.getReason()).containsIgnoringCase("Lease Revoked");

        // Verify database state transitioned to REVOKED and inactive
        WorkerNode updated = workerNodeRepository.findById(workerId).orElseThrow();
        assertThat(updated.getState()).isEqualTo(WorkerState.REVOKED);
        assertThat(updated.getActive()).isFalse();

        // Clean up registry
        workerSessionRegistry.unregister(sessionId);
    }
}
