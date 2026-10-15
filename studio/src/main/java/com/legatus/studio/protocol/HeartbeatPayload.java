package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for STUDIO_HEARTBEAT and STUDIO_HEARTBEAT_ACK frames.
 */
public final class HeartbeatPayload {

    private HeartbeatPayload() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(long client_timestamp) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(long client_timestamp, long server_timestamp) {}
}
