package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for SUBSCRIBE_TASK_LOGS and UNSUBSCRIBE_TASK_LOGS frames.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskLogSubscriptionPayload(
        String task_id
) {}
