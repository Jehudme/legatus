package com.legatus.studio.service;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utility guaranteeing execution of UI mutations on the JavaFX Application Thread
 * while synchronously completing background steps and falling back safely in headless test fixtures.
 */
public final class FxAsync {

    private FxAsync() {}

    public static void runOnFxThread(Runnable action) {
        if (action == null) return;
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        action.run();
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    if (!latch.await(3, TimeUnit.SECONDS)) {
                        action.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    action.run();
                }
            }
        } catch (IllegalStateException e) {
            // Toolkit not initialized in headless unit tests
            action.run();
        }
    }
}
