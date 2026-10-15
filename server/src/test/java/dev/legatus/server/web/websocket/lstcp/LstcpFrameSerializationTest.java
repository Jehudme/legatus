package dev.legatus.server.web.websocket.lstcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.service.dto.lstcp.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for polymorphic Jackson serialization/deserialization of LSTCP wire frames.
 */
class LstcpFrameSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should serialize and deserialize STUDIO_SESSION_INIT with viewport filter")
    void testStudioSessionInitRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        StudioSessionInitFrame frame = new StudioSessionInitFrame(
            id,
            1728907200100L,
            new StudioSessionInitFrame.Payload(
                "1.0.0",
                List.of("CLUSTER_METRICS", "WORKER_TOPOLOGY", "QUARANTINE_ALERTS", "TASK_LIFECYCLE"),
                new StudioSessionInitFrame.ViewportFilter("WORKER_LIST", "production-core", 50)
            )
        );

        String json = objectMapper.writeValueAsString(frame);
        assertThat(json).contains("\"type\":\"STUDIO_SESSION_INIT\"");
        assertThat(json).contains("\"studio_version\":\"1.0.0\"");
        assertThat(json).contains("\"cluster_pool\":\"production-core\"");

        LstcpFrame deserialized = objectMapper.readValue(json, LstcpFrame.class);
        assertThat(deserialized).isInstanceOf(StudioSessionInitFrame.class);
        StudioSessionInitFrame result = (StudioSessionInitFrame) deserialized;
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.payload().subscriptions()).contains("CLUSTER_METRICS", "TASK_LIFECYCLE");
        assertThat(result.payload().viewportFilter().limit()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should serialize and deserialize STUDIO_SESSION_ACK")
    void testStudioSessionAckRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        StudioSessionAckFrame frame = new StudioSessionAckFrame(
            id,
            1728907200150L,
            new StudioSessionAckFrame.Payload(
                "st-sess-9012-ab34",
                "production-core",
                10000L,
                2,
                14,
                3,
                28
            )
        );

        String json = objectMapper.writeValueAsString(frame);
        assertThat(json).contains("\"type\":\"STUDIO_SESSION_ACK\"");
        assertThat(json).contains("\"session_id\":\"st-sess-9012-ab34\"");

        LstcpFrame deserialized = objectMapper.readValue(json, LstcpFrame.class);
        assertThat(deserialized).isInstanceOf(StudioSessionAckFrame.class);
        StudioSessionAckFrame result = (StudioSessionAckFrame) deserialized;
        assertThat(result.payload().onlineWorkersCount()).isEqualTo(14);
        assertThat(result.payload().executingTasksCount()).isEqualTo(28);
    }

    @Test
    @DisplayName("Should serialize and deserialize SUBSCRIBE_TASK_LOGS and UNSUBSCRIBE_TASK_LOGS")
    void testLogSubscriptionFramesRoundTrip() throws Exception {
        UUID id1 = UUID.randomUUID();
        SubscribeTaskLogsFrame sub = new SubscribeTaskLogsFrame(
            id1,
            1728907201000L,
            new SubscribeTaskLogsFrame.Payload("task-20261013-0091")
        );
        String subJson = objectMapper.writeValueAsString(sub);
        LstcpFrame subResult = objectMapper.readValue(subJson, LstcpFrame.class);
        assertThat(subResult).isInstanceOf(SubscribeTaskLogsFrame.class);
        assertThat(((SubscribeTaskLogsFrame) subResult).payload().taskId()).isEqualTo("task-20261013-0091");

        UUID id2 = UUID.randomUUID();
        UnsubscribeTaskLogsFrame unsub = new UnsubscribeTaskLogsFrame(
            id2,
            1728907202000L,
            new UnsubscribeTaskLogsFrame.Payload("task-20261013-0091")
        );
        String unsubJson = objectMapper.writeValueAsString(unsub);
        LstcpFrame unsubResult = objectMapper.readValue(unsubJson, LstcpFrame.class);
        assertThat(unsubResult).isInstanceOf(UnsubscribeTaskLogsFrame.class);
        assertThat(((UnsubscribeTaskLogsFrame) unsubResult).payload().taskId()).isEqualTo("task-20261013-0091");
    }

    @Test
    @DisplayName("Should serialize and deserialize EVENT_CLUSTER_METRIC_TICK")
    void testClusterMetricTickRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        EventClusterMetricTickFrame frame = new EventClusterMetricTickFrame(
            id,
            1728907203000L,
            new EventClusterMetricTickFrame.Payload(
                14, 1, 0,
                224, 96, 42.8,
                240518168576L, 103079215104L,
                4, 96, 1420, 2
            )
        );

        String json = objectMapper.writeValueAsString(frame);
        LstcpFrame deserialized = objectMapper.readValue(json, LstcpFrame.class);
        assertThat(deserialized).isInstanceOf(EventClusterMetricTickFrame.class);
        EventClusterMetricTickFrame result = (EventClusterMetricTickFrame) deserialized;
        assertThat(result.payload().nodesOnline()).isEqualTo(14);
        assertThat(result.payload().clusterCpuLoadPct()).isEqualTo(42.8);
        assertThat(result.payload().totalAllocatableCores()).isEqualTo(224);
    }

    @Test
    @DisplayName("Should serialize and deserialize EVENT_TASK_LOG_CHUNK")
    void testTaskLogChunkRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        EventTaskLogChunkFrame frame = new EventTaskLogChunkFrame(
            id,
            1728907206000L,
            new EventTaskLogChunkFrame.Payload(
                "task-20261013-0091",
                52.0,
                24102900L,
                "Processed chunk 520 of 1000 items\n"
            )
        );

        String json = objectMapper.writeValueAsString(frame);
        LstcpFrame deserialized = objectMapper.readValue(json, LstcpFrame.class);
        assertThat(deserialized).isInstanceOf(EventTaskLogChunkFrame.class);
        EventTaskLogChunkFrame result = (EventTaskLogChunkFrame) deserialized;
        assertThat(result.payload().taskId()).isEqualTo("task-20261013-0091");
        assertThat(result.payload().progressPct()).isEqualTo(52.0);
        assertThat(result.payload().logChunk()).isEqualTo("Processed chunk 520 of 1000 items\n");
    }

    @Test
    @DisplayName("Should serialize and deserialize EVENT_ENROLLMENT_REQUESTED")
    void testEnrollmentRequestedRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        EventEnrollmentRequestedFrame frame = new EventEnrollmentRequestedFrame(
            id,
            1728907202000L,
            new EventEnrollmentRequestedFrame.Payload(
                "enr-8821-4920-a1b2",
                "compute-linux-east-042",
                "wk-east-042.internal.corp",
                "10.24.18.42",
                "linux",
                "x86_64",
                "4E:8A:BC:31:09:FF:7D:12",
                "Tiger-Galaxy-77",
                12,
                17179869184L,
                1728908100000L
            )
        );

        String json = objectMapper.writeValueAsString(frame);
        LstcpFrame deserialized = objectMapper.readValue(json, LstcpFrame.class);
        assertThat(deserialized).isInstanceOf(EventEnrollmentRequestedFrame.class);
        EventEnrollmentRequestedFrame result = (EventEnrollmentRequestedFrame) deserialized;
        assertThat(result.payload().visualCode()).isEqualTo("Tiger-Galaxy-77");
        assertThat(result.payload().fingerprintHex()).isEqualTo("4E:8A:BC:31:09:FF:7D:12");
    }
}
