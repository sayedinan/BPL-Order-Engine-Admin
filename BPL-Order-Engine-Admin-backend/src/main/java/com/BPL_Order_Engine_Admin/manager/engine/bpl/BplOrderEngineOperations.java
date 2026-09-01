package com.BPL_Order_Engine_Admin.manager.engine.bpl;

import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.engine.LogLine;
import com.BPL_Order_Engine_Admin.manager.engine.OrderEngineOperations;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory mock of the BPL Order Engine. The single Phase 1
 * implementation of {@link OrderEngineOperations} for engine id
 * {@code "bpl"}. Never makes any network, Docker, or SSH call &mdash;
 * the real staging container at {@code 180.210.129.233} is
 * deliberately out of reach (see SPEC.md &sect;2.5 and the
 * {@code staging-safety} skill).
 *
 * <p>State machine and log buffer are guarded by a
 * {@link ReentrantLock} so concurrent calls to {@link #start()},
 * {@link #stop()}, {@link #status()}, and the scheduled log generator
 * don't tear the buffer or race the transition.
 */
@Service("bpl")
public class BplOrderEngineOperations implements OrderEngineOperations {

    /** Maximum number of log lines retained. */
    static final int LOG_BUFFER_CAPACITY = 500;

    /** Synthetic tick cadence when the engine is RUNNING. */
    static final long TICK_INTERVAL_MS = 2_000L;

    private static final String DISPLAY_NAME = "BPL Order Engine";
    private static final String ENGINE_ID = "bpl";

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);

    /**
     * Bounded ring buffer. The mock is single-process so we use a
     * {@link Deque} under the lock; for a real implementation this
     * would be a bounded queue.
     */
    private final Deque<LogLine> logBuffer = new ArrayDeque<>(LOG_BUFFER_CAPACITY);

    private volatile Instant lastTransitionAt;
    private long tickCounter = 0L;

    @PostConstruct
    void seedLogs() {
        // Seeded with three canned lines per SPEC &sect;2.4. Done at
        // construction (via @PostConstruct) so the very first /logs
        // call returns something rather than an empty page.
        Instant now = Instant.now();
        appendLog(new LogLine(now, "INFO", "Engine initialized (mock)."));
        appendLog(new LogLine(now, "INFO", "Awaiting start command."));
        appendLog(new LogLine(now, "INFO", "Pre-start health check passed."));
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public EngineStatus status() {
        return status.get();
    }

    /** Read-only view of the last transition timestamp. May be {@code null}. */
    public Instant lastTransitionAt() {
        return lastTransitionAt;
    }

    @Override
    public void start() {
        lock.lock();
        try {
            EngineStatus current = status.get();
            if (current != EngineStatus.STOPPED) {
                throw new IllegalStateException(
                        "Engine '" + ENGINE_ID + "' is already " + current);
            }
            status.set(EngineStatus.RUNNING);
            lastTransitionAt = Instant.now();
            appendLog(new LogLine(lastTransitionAt, "INFO",
                    "Engine started (mock). Awaiting orders."));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            EngineStatus current = status.get();
            if (current != EngineStatus.RUNNING) {
                throw new IllegalStateException(
                        "Engine '" + ENGINE_ID + "' is already " + current);
            }
            status.set(EngineStatus.STOPPED);
            lastTransitionAt = Instant.now();
            appendLog(new LogLine(lastTransitionAt, "INFO",
                    "Engine stopped (mock)."));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LogLine> getLogs(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        lock.lock();
        try {
            // logBuffer is oldest -> newest. Return the most recent
            // `limit` lines, also ordered oldest -> newest.
            int size = logBuffer.size();
            int fromIndex = Math.max(0, size - limit);
            LogLine[] snapshot = logBuffer.toArray(new LogLine[0]);
            List<LogLine> out = new ArrayList<>(size - fromIndex);
            for (int i = fromIndex; i < size; i++) {
                out.add(snapshot[i]);
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Synthetic heartbeat while the engine is RUNNING. The method is
     * cheap and self-throttling &mdash; it no-ops in any other state
     * so the scheduler can run on a fixed delay without a
     * Spring profile or runtime check elsewhere.
     */
    @Scheduled(fixedDelay = TICK_INTERVAL_MS)
    void heartbeat() {
        EngineStatus current = status.get();
        if (current != EngineStatus.RUNNING) {
            return;
        }
        long n;
        lock.lock();
        try {
            n = ++tickCounter;
            appendLog(new LogLine(Instant.now(), "INFO",
                    "Heartbeat OK (mock tick #" + n + ")"));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Append a line to the bounded buffer, evicting the oldest entry
     * when the cap is reached. Caller must hold {@link #lock}.
     */
    private void appendLog(LogLine line) {
        if (logBuffer.size() >= LOG_BUFFER_CAPACITY) {
            logBuffer.pollFirst();
        }
        logBuffer.offerLast(line);
    }
}
