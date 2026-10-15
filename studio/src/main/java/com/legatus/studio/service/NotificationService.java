package com.legatus.studio.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.UUID;

/**
 * Service managing non-blocking toast notifications in Legatus Studio.
 */
public class NotificationService {

    public enum Type {
        INFO, SUCCESS, WARNING, ERROR
    }

    private static final ObservableList<NotificationItem> NOTIFICATIONS = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    public static ObservableList<NotificationItem> getNotifications() {
        return NOTIFICATIONS;
    }

    public static void show(Type type, String title, String message) {
        NotificationItem item = new NotificationItem(
                UUID.randomUUID().toString(),
                type,
                title,
                message,
                Instant.now()
        );
        NOTIFICATIONS.add(item);
    }

    public static void info(String title, String message) { show(Type.INFO, title, message); }
    public static void success(String title, String message) { show(Type.SUCCESS, title, message); }
    public static void warning(String title, String message) { show(Type.WARNING, title, message); }
    public static void error(String title, String message) { show(Type.ERROR, title, message); }

    public static void dismiss(NotificationItem item) {
        NOTIFICATIONS.remove(item);
    }
}
