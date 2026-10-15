package com.legatus.studio.transport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe bounded FIFO circular log buffer for live task stdout/stderr console streaming.
 * Enforces a strict ceiling of 10,000 lines or 8 Megabytes of UTF-8 text to prevent OOM
 * on verbose tasks per studio-spec.md Section 5.2 (Issue #403).
 */
public class LogStreamBuffer {

    public static final int DEFAULT_MAX_LINES = 10_000;
    public static final long DEFAULT_MAX_BYTES = 8 * 1024 * 1024; // 8 Megabytes

    private final int maxLines;
    private final long maxBytes;

    private final Deque<String> lines = new ArrayDeque<>();
    private long currentBytes = 0;

    private final List<Consumer<String>> lineListeners = new CopyOnWriteArrayList<>();

    public LogStreamBuffer() {
        this(DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES);
    }

    public LogStreamBuffer(int maxLines, long maxBytes) {
        if (maxLines <= 0) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxLines = maxLines;
        this.maxBytes = maxBytes;
    }

    /**
     * Appends a log chunk, splitting it into lines and evicting oldest lines when full.
     */
    public synchronized void appendChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        String[] split = chunk.split("\r?\n", -1);
        for (int i = 0; i < split.length; i++) {
            String line = split[i];
            // If it's the last element and empty, skip trailing empty split
            if (i == split.length - 1 && line.isEmpty()) {
                continue;
            }
            appendLine(line);
        }
    }

    /**
     * Appends a single line, maintaining capacity bounds.
     */
    public synchronized void appendLine(String line) {
        if (line == null) {
            return;
        }

        int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;

        // Evict until within bounds
        while (lines.size() >= maxLines || (currentBytes + lineBytes > maxBytes && !lines.isEmpty())) {
            String evicted = lines.pollFirst();
            if (evicted != null) {
                currentBytes -= evicted.getBytes(StandardCharsets.UTF_8).length;
            }
        }

        lines.addLast(line);
        currentBytes += lineBytes;

        // Notify listeners
        for (Consumer<String> listener : lineListeners) {
            try {
                listener.accept(line);
            } catch (Exception ignored) {}
        }
    }

    public synchronized List<String> getAllLines() {
        return new ArrayList<>(lines);
    }

    public synchronized void clear() {
        lines.clear();
        currentBytes = 0;
    }

    public synchronized int getLineCount() {
        return lines.size();
    }

    public synchronized long getByteCount() {
        return currentBytes;
    }

    public void addLineListener(Consumer<String> listener) {
        if (listener != null) {
            lineListeners.add(listener);
        }
    }

    public void removeLineListener(Consumer<String> listener) {
        if (listener != null) {
            lineListeners.remove(listener);
        }
    }
}
