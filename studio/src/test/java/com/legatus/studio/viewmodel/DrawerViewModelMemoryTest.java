package com.legatus.studio.viewmodel;

import com.legatus.studio.transport.LogStreamBuffer;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DrawerViewModelMemoryTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testConsoleLinesCeilingAndFifoEviction() throws InterruptedException {
        LogStreamBuffer buffer = new LogStreamBuffer(20_000, 50_000_000L);
        DrawerViewModel viewModel = new DrawerViewModel(buffer);

        // Stream 15,000 log lines
        for (int i = 1; i <= 15_000; i++) {
            buffer.appendLine("Log message line #" + i);
        }

        // Wait for JavaFX thread to drain all queued updates
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread should process all log lines");

        // Assert strictly capped at 10,000 lines
        assertEquals(10_000, viewModel.getConsoleLines().size(),
                "consoleLines in DrawerViewModel must be strictly bounded at 10,000 lines");

        // Verify FIFO eviction: line #1 to #5000 were evicted, oldest remaining is line #5001, newest is #15000
        assertEquals("Log message line #5001", viewModel.getConsoleLines().get(0));
        assertEquals("Log message line #15000", viewModel.getConsoleLines().get(9_999));
    }
}
