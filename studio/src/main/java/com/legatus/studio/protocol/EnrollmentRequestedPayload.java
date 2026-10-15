package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for EVENT_ENROLLMENT_REQUESTED frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentRequestedPayload(
        String enrollment_id,
        String worker_id,
        String hostname,
        String source_ip,
        String os,
        String architecture,
        String fingerprint_hex,
        String visual_code,
        int allocatable_cores,
        long max_memory_bytes,
        long expires_at
) {}
