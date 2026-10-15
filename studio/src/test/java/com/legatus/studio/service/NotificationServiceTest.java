package com.legatus.studio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationServiceTest {

    @BeforeEach
    void clearNotifications() {
        NotificationService.getNotifications().clear();
    }

    @Test
    void testNotificationCreationAndDismissal() {
        assertEquals(0, NotificationService.getNotifications().size());

        NotificationService.info("Info Title", "Info message");
        NotificationService.success("Success Title", "Success message");
        NotificationService.warning("Warning Title", "Warning message");
        NotificationService.error("Error Title", "Error message");

        assertEquals(4, NotificationService.getNotifications().size());

        var first = NotificationService.getNotifications().get(0);
        assertEquals(NotificationService.Type.INFO, first.type());
        assertEquals("Info Title", first.title());

        NotificationService.dismiss(first);
        assertEquals(3, NotificationService.getNotifications().size());
    }
}
