package com.legatus.studio.transport;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LogStreamBufferTest {

    @Test
    void testBasicAppendAndLineCount() {
        LogStreamBuffer buffer = new LogStreamBuffer(100, 1024 * 1024);
        buffer.appendChunk("Line 1\nLine 2\r\nLine 3\n");

        assertEquals(3, buffer.getLineCount());
        List<String> lines = buffer.getAllLines();
        assertEquals("Line 1", lines.get(0));
        assertEquals("Line 2", lines.get(1));
        assertEquals("Line 3", lines.get(2));
    }

    @Test
    void testMaxLinesCeilingEviction() {
        LogStreamBuffer buffer = new LogStreamBuffer(5, 1024 * 1024);
        for (int i = 1; i <= 8; i++) {
            buffer.appendLine("Log row " + i);
        }

        assertEquals(5, buffer.getLineCount());
        List<String> lines = buffer.getAllLines();
        assertEquals("Log row 4", lines.get(0));
        assertEquals("Log row 5", lines.get(1));
        assertEquals("Log row 6", lines.get(2));
        assertEquals("Log row 7", lines.get(3));
        assertEquals("Log row 8", lines.get(4));
    }

    @Test
    void testMaxByteLimitEviction() {
        // 50 bytes ceiling
        LogStreamBuffer buffer = new LogStreamBuffer(100, 50);

        // Each line is 15 bytes
        buffer.appendLine("0123456789abcde"); // 15
        buffer.appendLine("0123456789abcde"); // 30
        buffer.appendLine("0123456789abcde"); // 45
        buffer.appendLine("0123456789abcde"); // 60 -> exceeds 50, oldest evicted

        assertTrue(buffer.getByteCount() <= 50);
        assertTrue(buffer.getLineCount() <= 3);
    }

    @Test
    void testLineListenerNotification() {
        LogStreamBuffer buffer = new LogStreamBuffer(10, 1024);
        List<String> notified = new ArrayList<>();
        buffer.addLineListener(notified::add);

        buffer.appendChunk("chunk1\nchunk2\n");
        assertEquals(2, notified.size());
        assertEquals("chunk1", notified.get(0));
        assertEquals("chunk2", notified.get(1));
    }

    @Test
    void testClearBuffer() {
        LogStreamBuffer buffer = new LogStreamBuffer(10, 1024);
        buffer.appendLine("Test Line");
        assertEquals(1, buffer.getLineCount());

        buffer.clear();
        assertEquals(0, buffer.getLineCount());
        assertEquals(0, buffer.getByteCount());
    }
}
