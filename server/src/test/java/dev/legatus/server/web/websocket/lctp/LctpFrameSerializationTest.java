package dev.legatus.server.web.websocket.lctp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.service.dto.lctp.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests verifying JSON serialization and polymorphic deserialization
 * across all 11 sealed LCTP wire frame types (worker-wire.md).
 */
class LctpFrameSerializationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("Should serialize and deserialize SESSION_INIT frame polymorphically")
    void testSessionInitSerialization() throws Exception {
        UUID frameId = UUID.randomUUID();
        long now = 1728907200100L;

        SessionInitFrame frame = new SessionInitFrame(
            frameId,
            now,
            new SessionInitFrame.Payload(
                "worker-001",
                "sess-prev",
                "host.internal",
                "linux",
                "x86_64",
                "1.0.0",
                new SessionInitFrame.RuntimeInfo("solix", "1.0.0", "v1.1", List.of("arc_deterministic")),
                new SessionInitFrame.HardwareInfo(8, 16, 34359738368L, false, null),
                new SessionInitFrame.Capacity(12, 17179869184L, 50000000L),
                List.of(new SessionInitFrame.ReconcileTask("task-1", "EXECUTING", 12000L, 500L))
            )
        );

        String json = om.writeValueAsString(frame);
        assertThat(json).contains("\"type\":\"SESSION_INIT\"");
        assertThat(json).contains("\"worker_id\":\"worker-001\"");
        assertThat(json).contains("\"allocatable_cores\":12");

        LctpFrame deserialized = om.readValue(json, LctpFrame.class);
        assertThat(deserialized).isInstanceOf(SessionInitFrame.class);
        SessionInitFrame init = (SessionInitFrame) deserialized;
        assertThat(init.id()).isEqualTo(frameId);
        assertThat(init.payload().workerId()).isEqualTo("worker-001");
        assertThat(init.payload().reconcileTasks()).hasSize(1);
    }

    @Test
    @DisplayName("Should serialize and deserialize SESSION_ACK frame polymorphically")
    void testSessionAckSerialization() throws Exception {
        UUID frameId = UUID.randomUUID();
        SessionAckFrame frame = new SessionAckFrame(
            frameId,
            1728907200250L,
            new SessionAckFrame.Payload(
                "sess-1234",
                "ACCEPTED",
                "production-us-east",
                5000L,
                15000L,
                1728907200248L,
                List.of("task-1"),
                List.of("task-stale"),
                null
            )
        );

        String json = om.writeValueAsString(frame);
        assertThat(json).contains("\"type\":\"SESSION_ACK\"");
        assertThat(json).contains("\"status\":\"ACCEPTED\"");

        LctpFrame deserialized = om.readValue(json, LctpFrame.class);
        assertThat(deserialized).isInstanceOf(SessionAckFrame.class);
        SessionAckFrame ack = (SessionAckFrame) deserialized;
        assertThat(ack.payload().sessionId()).isEqualTo("sess-1234");
        assertThat(ack.payload().reconciledTaskIds()).containsExactly("task-1");
    }

    @Test
    @DisplayName("Should serialize and deserialize HEARTBEAT and HEARTBEAT_ACK frames")
    void testHeartbeatSerialization() throws Exception {
        UUID frameId = UUID.randomUUID();
        HeartbeatFrame hb = new HeartbeatFrame(
            frameId,
            1728907205000L,
            new HeartbeatFrame.Payload(
                "sess-1234",
                false,
                new HeartbeatFrame.HostMetrics(25.5, 0.0, 4294967296L, 12884901888L),
                List.of(new HeartbeatFrame.RunningTask("task-1", 12345, "EXECUTING", 1000L, 2048L, 300L))
            )
        );

        String hbJson = om.writeValueAsString(hb);
        assertThat(hbJson).contains("\"type\":\"HEARTBEAT\"");

        LctpFrame deserializedHb = om.readValue(hbJson, LctpFrame.class);
        assertThat(deserializedHb).isInstanceOf(HeartbeatFrame.class);

        HeartbeatAckFrame ack = new HeartbeatAckFrame(
            UUID.randomUUID(),
            1728907205015L,
            new HeartbeatAckFrame.Payload("sess-1234", 1728907205014L)
        );
        String ackJson = om.writeValueAsString(ack);
        LctpFrame deserializedAck = om.readValue(ackJson, LctpFrame.class);
        assertThat(deserializedAck).isInstanceOf(HeartbeatAckFrame.class);
    }

    @Test
    @DisplayName("Should serialize and deserialize DISPATCH_TASK frame")
    void testDispatchTaskSerialization() throws Exception {
        UUID frameId = UUID.randomUUID();
        DispatchTaskFrame frame = new DispatchTaskFrame(
            frameId,
            1728907210000L,
            new DispatchTaskFrame.Payload(
                "task-20261014-001",
                "entry_func",
                new DispatchTaskFrame.BytecodeSpec("solix_bc_v1", "base64", "sha256-hex", "payload-data", null),
                new DispatchTaskFrame.Constraints(50000000L, 67108864L, 30000),
                Map.of("key", "val"),
                Map.of("ENV_VAR", "prod"),
                Map.of("SECRET_KEY", "decrypted-token")
            )
        );

        String json = om.writeValueAsString(frame);
        assertThat(json).contains("\"type\":\"DISPATCH_TASK\"");
        assertThat(json).contains("\"task_id\":\"task-20261014-001\"");

        LctpFrame deserialized = om.readValue(json, LctpFrame.class);
        assertThat(deserialized).isInstanceOf(DispatchTaskFrame.class);
        DispatchTaskFrame dispatch = (DispatchTaskFrame) deserialized;
        assertThat(dispatch.payload().taskId()).isEqualTo("task-20261014-001");
        assertThat(dispatch.payload().secrets()).containsEntry("SECRET_KEY", "decrypted-token");
    }

    @Test
    @DisplayName("Should serialize and deserialize TASK_RESULT and TASK_RESULT_ACK frames")
    void testTaskResultSerialization() throws Exception {
        UUID frameId = UUID.randomUUID();
        TaskResultFrame result = new TaskResultFrame(
            frameId,
            1728907212350L,
            new TaskResultFrame.Payload(
                "task-1",
                "COMPLETED",
                0,
                new TaskResultFrame.TaskMetrics(1284500L, 14680064L, 2341L),
                Map.of("output_field", 42),
                null
            )
        );

        String json = om.writeValueAsString(result);
        assertThat(json).contains("\"type\":\"TASK_RESULT\"");
        assertThat(json).contains("\"status\":\"COMPLETED\"");

        LctpFrame deserialized = om.readValue(json, LctpFrame.class);
        assertThat(deserialized).isInstanceOf(TaskResultFrame.class);

        TaskResultAckFrame ack = new TaskResultAckFrame(
            UUID.randomUUID(),
            1728907212400L,
            new TaskResultAckFrame.Payload("task-1", "COMMITTED")
        );
        String ackJson = om.writeValueAsString(ack);
        LctpFrame deserializedAck = om.readValue(ackJson, LctpFrame.class);
        assertThat(deserializedAck).isInstanceOf(TaskResultAckFrame.class);
    }

    @Test
    @DisplayName("Should serialize and deserialize DRAIN_NOTICE, ABORT_TASK, and PROTOCOL_ERROR frames")
    void testControlFramesSerialization() throws Exception {
        DrainNoticeFrame drain = new DrainNoticeFrame(
            UUID.randomUUID(), 1000L, new DrainNoticeFrame.Payload(true, 3, "MAINTENANCE")
        );
        assertThat(om.readValue(om.writeValueAsString(drain), LctpFrame.class)).isInstanceOf(DrainNoticeFrame.class);

        AbortTaskFrame abort = new AbortTaskFrame(
            UUID.randomUUID(), 1000L, new AbortTaskFrame.Payload("task-1", "OPERATOR_ABORT", 10000L)
        );
        assertThat(om.readValue(om.writeValueAsString(abort), LctpFrame.class)).isInstanceOf(AbortTaskFrame.class);

        ProtocolErrorFrame err = new ProtocolErrorFrame(
            UUID.randomUUID(), 1000L, new ProtocolErrorFrame.Payload("frame-id", "ERR_CODE", "Error message")
        );
        assertThat(om.readValue(om.writeValueAsString(err), LctpFrame.class)).isInstanceOf(ProtocolErrorFrame.class);
    }
}
