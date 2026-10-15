package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

/**
 * Sealed base interface for all Legatus Compute Transport Protocol (LCTP) wire frames.
 * <p>
 * Complies with {@code worker-wire.md} Section 3 &amp; {@code GL-0011}:
 * <ul>
 *   <li>Mandates canonical UTF-8 JSON envelope: type, id (UUIDv4), timestamp (epoch ms), payload.</li>
 *   <li>Exhaustive pattern matching via Java 21 sealed interface hierarchy.</li>
 *   <li>Polymorphic Jackson serialization/deserialization on "type" discriminator.</li>
 * </ul>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionInitFrame.class, name = "SESSION_INIT"),
    @JsonSubTypes.Type(value = SessionAckFrame.class, name = "SESSION_ACK"),
    @JsonSubTypes.Type(value = HeartbeatFrame.class, name = "HEARTBEAT"),
    @JsonSubTypes.Type(value = HeartbeatAckFrame.class, name = "HEARTBEAT_ACK"),
    @JsonSubTypes.Type(value = DispatchTaskFrame.class, name = "DISPATCH_TASK"),
    @JsonSubTypes.Type(value = TaskProgressFrame.class, name = "TASK_PROGRESS"),
    @JsonSubTypes.Type(value = TaskResultFrame.class, name = "TASK_RESULT"),
    @JsonSubTypes.Type(value = TaskResultAckFrame.class, name = "TASK_RESULT_ACK"),
    @JsonSubTypes.Type(value = DrainNoticeFrame.class, name = "DRAIN_NOTICE"),
    @JsonSubTypes.Type(value = AbortTaskFrame.class, name = "ABORT_TASK"),
    @JsonSubTypes.Type(value = ProtocolErrorFrame.class, name = "PROTOCOL_ERROR")
})
public sealed interface LctpFrame permits
    SessionInitFrame,
    SessionAckFrame,
    HeartbeatFrame,
    HeartbeatAckFrame,
    DispatchTaskFrame,
    TaskProgressFrame,
    TaskResultFrame,
    TaskResultAckFrame,
    DrainNoticeFrame,
    AbortTaskFrame,
    ProtocolErrorFrame {

    String type();
    UUID id();
    long timestamp();
}
