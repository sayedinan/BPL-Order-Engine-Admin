package com.BPL_Order_Engine_Admin.manager.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.3 per-engine log buffer (SPEC §6.2 / §2.8 / §4.3 logs/stream).
 *
 * <p>Cap 500 lines per engine (the front-end expects the same
 * server-side cap). One buffer per engine code, created lazily on
 * first access.
 *
 * <p>The tailer (in #19) calls {@link #append} for every line it
 * reads from {@code tail -F}; the WebSocket handler calls
 * {@link #snapshot} on connect. Both are thread-safe.
 */
@Component
public class LogBuffer {

    private static final int CAP = 500;
    private final Map<String, Deque<LogLine>> buffers = new ConcurrentHashMap<>();

    public void append(String engineCode, LogLine line) {
        Deque<LogLine> deque = buffers.computeIfAbsent(engineCode,
            k -> new ArrayDeque<>(CAP));
        synchronized (deque) {
            deque.addLast(line);
            while (deque.size() > CAP) {
                deque.removeFirst();
            }
        }
    }

    public List<LogLine> snapshot(String engineCode, int limit) {
        Deque<LogLine> deque = buffers.get(engineCode);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            int total = deque.size();
            int skip = Math.max(0, total - limit);
            List<LogLine> out = new ArrayList<>(Math.min(limit, total));
            int i = 0;
            for (LogLine l : deque) {
                if (i++ >= skip) out.add(l);
            }
            return out;
        }
    }

    public void clear(String engineCode) {
        Deque<LogLine> deque = buffers.get(engineCode);
        if (deque != null) {
            synchronized (deque) {
                deque.clear();
            }
        }
    }
}
