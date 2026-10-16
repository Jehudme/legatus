package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.orchestration.balancer.ThresholdScheduler;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.dto.lctp.HeartbeatFrame;
import dev.legatus.server.web.websocket.lctp.LctpWorkerSession;
import dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClusterMetricsIsolationTest {

    private WorkerNodeRepository workerNodeRepository;
    private ComputeTaskRepository computeTaskRepository;
    private WorkerSessionRegistry workerSessionRegistry;
    private ThresholdScheduler thresholdScheduler;
    private ClusterMetricsService clusterMetricsService;

    @BeforeEach
    void setUp() {
        workerNodeRepository = mock(WorkerNodeRepository.class);
        computeTaskRepository = mock(ComputeTaskRepository.class);
        workerSessionRegistry = mock(WorkerSessionRegistry.class);
        thresholdScheduler = new ThresholdScheduler();

        clusterMetricsService = new ClusterMetricsService(
            workerNodeRepository,
            computeTaskRepository,
            workerSessionRegistry,
            thresholdScheduler
        );
    }

    @Test
    @DisplayName("Should strictly exclude inactive, disabled, and draining workers from global cluster CPU/RAM metrics")
    void testMetricsExcludeInactiveAndDrainingWorkers() {
        // Active worker 1: 4 cores, 50% CPU
        WorkerNode w1 = createWorker("w-1", 4, 8L * 1024 * 1024 * 1024, true, false, WorkerState.ONLINE);
        LctpWorkerSession ws1 = mock(LctpWorkerSession.class);
        when(ws1.getWorkerId()).thenReturn("w-1");
        when(ws1.getLastHostMetrics()).thenReturn(new HeartbeatFrame.HostMetrics(50.0, 0.0, 4L * 1024 * 1024 * 1024, 4L * 1024 * 1024 * 1024));

        // Inactive / Disabled worker 2: 16 cores, 99% CPU (MUST BE EXCLUDED)
        WorkerNode w2 = createWorker("w-2", 16, 32L * 1024 * 1024 * 1024, false, false, WorkerState.ONLINE);
        LctpWorkerSession ws2 = mock(LctpWorkerSession.class);
        when(ws2.getWorkerId()).thenReturn("w-2");
        when(ws2.getLastHostMetrics()).thenReturn(new HeartbeatFrame.HostMetrics(99.0, 0.0, 30L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024));

        // Draining worker 3: 8 cores, 80% CPU (MUST BE EXCLUDED)
        WorkerNode w3 = createWorker("w-3", 8, 16L * 1024 * 1024 * 1024, true, true, WorkerState.DRAINING);
        LctpWorkerSession ws3 = mock(LctpWorkerSession.class);
        when(ws3.getWorkerId()).thenReturn("w-3");
        when(ws3.getLastHostMetrics()).thenReturn(new HeartbeatFrame.HostMetrics(80.0, 0.0, 14L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024));

        // Inactive state worker 4: 8 cores, 70% CPU (MUST BE EXCLUDED)
        WorkerNode w4 = createWorker("w-4", 8, 16L * 1024 * 1024 * 1024, true, false, WorkerState.INACTIVE);
        LctpWorkerSession ws4 = mock(LctpWorkerSession.class);
        when(ws4.getWorkerId()).thenReturn("w-4");
        when(ws4.getLastHostMetrics()).thenReturn(new HeartbeatFrame.HostMetrics(70.0, 0.0, 10L * 1024 * 1024 * 1024, 6L * 1024 * 1024 * 1024));

        when(workerNodeRepository.countByStateAndActiveTrue(WorkerState.ONLINE)).thenReturn(1L);
        when(workerNodeRepository.countByState(WorkerState.DRAINING)).thenReturn(1L);
        when(workerNodeRepository.countByState(WorkerState.OFFLINE)).thenReturn(0L);
        when(workerNodeRepository.countByState(WorkerState.INACTIVE)).thenReturn(1L);
        when(workerNodeRepository.countByState(WorkerState.PENDING_APPROVAL)).thenReturn(0L);

        when(workerNodeRepository.findAllByStateAndActiveTrueAndDrainingFalse(WorkerState.ONLINE))
            .thenReturn(List.of(w1));

        when(workerSessionRegistry.getAllActiveSessions()).thenReturn(List.of(ws1, ws2, ws3, ws4));
        when(workerNodeRepository.findById("w-1")).thenReturn(Optional.of(w1));
        when(workerNodeRepository.findById("w-2")).thenReturn(Optional.of(w2));
        when(workerNodeRepository.findById("w-3")).thenReturn(Optional.of(w3));
        when(workerNodeRepository.findById("w-4")).thenReturn(Optional.of(w4));

        when(computeTaskRepository.countByStatus(any())).thenReturn(0L);

        var stats = clusterMetricsService.computeClusterStats();

        // Active online nodes count is 1!
        assertThat(stats.nodesOnline()).isEqualTo(1);
        assertThat(stats.nodesDraining()).isEqualTo(1);
        assertThat(stats.nodesOffline()).isEqualTo(1);

        // Allocatable cores must only count w1 (4 cores)
        assertThat(stats.totalAllocatableCores()).isEqualTo(4);

        // CPU load must only consider w1 (50.0%), NOT w2, w3, or w4
        assertThat(stats.clusterCpuLoadPct()).isEqualTo(50.0);
        // Active memory used must only consider w1 (4GB)
        assertThat(stats.activeUsedMemoryBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
    }

    private WorkerNode createWorker(String id, int cores, long memoryBytes, boolean active, boolean draining, WorkerState state) {
        WorkerNode node = new WorkerNode();
        node.setWorkerId(id);
        node.setHostname(id + ".local");
        node.setAllocatableCores(cores);
        node.setMaxMemoryBytes(memoryBytes);
        node.setActive(active);
        node.setDraining(draining);
        node.setState(state);
        node.setTargetCpuPct(75.0);
        node.setTargetRamPct(80.0);
        node.setMaxConcurrentTasks(16);
        return node;
    }
}
