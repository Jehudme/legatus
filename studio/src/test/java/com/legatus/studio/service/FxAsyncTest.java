package com.legatus.studio.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class FxAsyncTest {

    @Test
    void testRunOnFxThreadExecutesAction() {
        AtomicBoolean executed = new AtomicBoolean(false);
        FxAsync.runOnFxThread(() -> executed.set(true));
        assertTrue(executed.get(), "Action should execute synchronously or via FX thread");
    }

    @Test
    void testRunOnFxThreadWithNull() {
        assertDoesNotThrow(() -> FxAsync.runOnFxThread(null));
    }
}
