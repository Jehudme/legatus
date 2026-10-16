package com.legatus.studio.transport;

import com.legatus.studio.protocol.ClusterMetricTickPayload;
import com.legatus.studio.protocol.TaskLifecyclePayload;
import com.legatus.studio.protocol.WorkerStateChangedPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class LstcpClientMulticastTest {

    @Test
    void testTaskLifecycleMulticastDelivery() {
        LstcpClient client = new LstcpClient("ws://localhost:9999/ws", false);

        AtomicInteger c1Count = new AtomicInteger();
        AtomicInteger c2Count = new AtomicInteger();
        AtomicInteger c3Count = new AtomicInteger();

        Consumer<TaskLifecyclePayload> listener1 = payload -> c1Count.incrementAndGet();
        Consumer<TaskLifecyclePayload> listener2 = payload -> c2Count.incrementAndGet();
        Consumer<TaskLifecyclePayload> listener3 = payload -> c3Count.incrementAndGet();

        client.addTaskLifecycleListener(listener1);
        client.addTaskLifecycleListener(listener2);
        client.addTaskLifecycleListener(listener3);

        // Simulate incoming task lifecycle event
        TaskLifecyclePayload testPayload = new TaskLifecyclePayload(
                "task-123", "testFn", "EXECUTING", null, "worker-1", 100L, 1024L, 50L
        );

        // Calling setTaskLifecycleListener acts as add (non-destructive multicast)
        Consumer<TaskLifecyclePayload> listener4 = payload -> {};
        client.setTaskLifecycleListener(listener4);

        // Verify listeners list has all listeners registered
        // Trigger via message parsing by injecting a synthetic frame
        String frameJson = """
                {
                    "type": "EVENT_TASK_LIFECYCLE",
                    "id": "11111111-1111-1111-1111-111111111111",
                    "timestamp": 123456789,
                    "payload": {
                        "task_id": "task-123",
                        "name": "testFn",
                        "status": "EXECUTING",
                        "assigned_worker_id": "worker-1",
                        "instructions_consumed": 100,
                        "peak_memory_bytes": 1024,
                        "execution_wall_time_ms": 50
                    }
                }
                """;

        client.onText(null, frameJson, true);

        assertEquals(1, c1Count.get(), "Listener 1 should receive event");
        assertEquals(1, c2Count.get(), "Listener 2 should receive event");
        assertEquals(1, c3Count.get(), "Listener 3 should receive event");

        // Remove listener 2 and fire again
        client.removeTaskLifecycleListener(listener2);
        client.onText(null, frameJson, true);

        assertEquals(2, c1Count.get(), "Listener 1 should receive second event");
        assertEquals(1, c2Count.get(), "Listener 2 should not receive second event after removal");
        assertEquals(2, c3Count.get(), "Listener 3 should receive second event");
    }

    @Test
    void testWorkerStateAndMetricTickMulticast() {
        LstcpClient client = new LstcpClient("ws://localhost:9999/ws", false);

        List<String> workerStates = new ArrayList<>();
        client.addWorkerStateListener(w -> workerStates.add(w.worker_id() + ":" + w.current_state()));
        client.addWorkerStateListener(w -> workerStates.add("mirror:" + w.worker_id()));

        String workerFrameJson = """
                {
                    "type": "EVENT_WORKER_STATE_CHANGED",
                    "id": "22222222-2222-2222-2222-222222222222",
                    "timestamp": 123456789,
                    "payload": {
                        "worker_id": "worker-99",
                        "current_state": "ONLINE",
                        "cpu_load_pct": 25.0,
                        "memory_used_bytes": 1048576,
                        "active_tasks_count": 2
                    }
                }
                """;

        client.onText(null, workerFrameJson, true);

        assertEquals(2, workerStates.size());
        assertTrue(workerStates.contains("worker-99:ONLINE"));
        assertTrue(workerStates.contains("mirror:worker-99"));
    }
}
