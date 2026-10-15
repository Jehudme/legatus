package com.legatus.studio.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;

/**
 * Service managing non-blocking toast notifications in Legatus Studio.
 */
public class NotificationService {

    public enum Type {
        INFO, SUCCESS, WARNING, ERROR
    }

    public record NotificationItem(
            String id,
            Type type,
            String title,
            String message,
            Instant timestamp
    ) {}

    private static final ObservableList<NotificationItem> NOTIFICATIONS = FXCollections.observableArrayList();

    public static ObservableList<NotificationItem> getNotifications() {
        return NOTIFICATIONS;
    }

    public static void show(Type type, String title, String message) {
        NotificationItem item = new NotificationItem(
                java.util.UUID.randomUUID().toString(),
                type,
                title,
                message,
                Instant.now()
        );
        try {
            javafx.application.Platform.runLater(() -> NOTIFICATIONS.add(item));
        } catch (IllegalStateException ignored) {
            NOTIFICATIONS.add(item);
        }
    }

    public static void info(String title, String message) { show(Type.INFO, title, message); }
    public static void success(String title, String message) { show(Type.SUCCESS, title, message); }
    public static void warning(String title, String message) { show(Type.WARNING, title, message); }
    public static void error(String title, String message) { show(Type.ERROR, title, message); }

    public static void dismiss(NotificationItem item) {
        try {
            javafx.application.Platform.runLater(() -> NOTIFICATIONS.remove(item));
        } catch (IllegalStateException ignored) {
            NOTIFICATIONS.remove(item);
        }
    }
}
