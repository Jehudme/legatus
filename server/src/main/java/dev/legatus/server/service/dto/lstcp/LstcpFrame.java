package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

/**
 * Base polymorphic sealed interface for Legatus Studio Telemetry & Control Protocol (LSTCP)
 * wire frames (studio-wire.md Section 2).
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StudioSessionInitFrame.class, name = "STUDIO_SESSION_INIT"),
    @JsonSubTypes.Type(value = StudioSessionAckFrame.class, name = "STUDIO_SESSION_ACK"),
    @JsonSubTypes.Type(value = SubscribeTaskLogsFrame.class, name = "SUBSCRIBE_TASK_LOGS"),
    @JsonSubTypes.Type(value = UnsubscribeTaskLogsFrame.class, name = "UNSUBSCRIBE_TASK_LOGS"),
    @JsonSubTypes.Type(value = StudioHeartbeatFrame.class, name = "STUDIO_HEARTBEAT"),
    @JsonSubTypes.Type(value = StudioHeartbeatAckFrame.class, name = "STUDIO_HEARTBEAT_ACK"),
    @JsonSubTypes.Type(value = EventEnrollmentRequestedFrame.class, name = "EVENT_ENROLLMENT_REQUESTED"),
    @JsonSubTypes.Type(value = EventClusterMetricTickFrame.class, name = "EVENT_CLUSTER_METRIC_TICK"),
    @JsonSubTypes.Type(value = EventWorkerStateChangedFrame.class, name = "EVENT_WORKER_STATE_CHANGED"),
    @JsonSubTypes.Type(value = EventTaskLifecycleFrame.class, name = "EVENT_TASK_LIFECYCLE"),
    @JsonSubTypes.Type(value = EventTaskLogChunkFrame.class, name = "EVENT_TASK_LOG_CHUNK")
})
public sealed interface LstcpFrame permits
    StudioSessionInitFrame,
    StudioSessionAckFrame,
    SubscribeTaskLogsFrame,
    UnsubscribeTaskLogsFrame,
    StudioHeartbeatFrame,
    StudioHeartbeatAckFrame,
    EventEnrollmentRequestedFrame,
    EventClusterMetricTickFrame,
    EventWorkerStateChangedFrame,
    EventTaskLifecycleFrame,
    EventTaskLogChunkFrame {

    String type();
    UUID id();
    long timestamp();
}
