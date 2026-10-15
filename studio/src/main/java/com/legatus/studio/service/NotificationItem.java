package com.legatus.studio.service;

import java.time.Instant;

/**
 * Immutable record representing a toast notification item.
 */
public record NotificationItem(
        String id,
        NotificationService.Type type,
        String title,
        String message,
        Instant timestamp
) {}
