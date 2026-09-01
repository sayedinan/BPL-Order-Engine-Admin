package com.BPL_Order_Engine_Admin.manager.engine.impl;

import com.BPL_Order_Engine_Admin.manager.engine.EngineActionResult;
import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.engine.LogLine;
import com.BPL_Order_Engine_Admin.manager.engine.OrderEngineOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.3 mock engine (SPEC §6.1). In-memory state machine, no network.
 *
 * <p>Per SPEC §6.1: a {@code @Scheduled(fixedDelay = 2000)} heartbeat
 * appends a synthetic log line ONLY while {@code status == RUNNING}.
 * On STOPPED, the scheduler is a no-op.
 *
 * <p>One bean, one buffer per engine row. The {@code OrderEngineFactory}
 * calls {@code new MockEngineOperations(engine)} for each
 * {@code MOCK}-mode row (no per-instance Spring beans — the
 * {@code @Component} is a singleton, the constructor wraps a row).
 */
@Component
public class MockEngineOperations implements OrderEngineOperations {

    private final String code;
    private final String name;
    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private volatile Instant lastTransitionAt;
    private final ReentrantLock transitionLock = new ReentrantLock();
    private final Deque<LogLine> buffer = new ArrayDeque<>(512);

    public MockEngineOperations(com.BPL_Order_Engine_Admin.manager.engine.EngineEntity engine) {
        this.code = engine.getCode();
        this.name = engine.getName();
        this.status.set(engine.getStatus());
        this.lastTransitionAt = engine.getLastTransitionAt();
        // Seed with three canned lines so the dashboard isn't empty.
        for (int i = 0; i < 3; i++) {
            buffer.addLast(new LogLine(
                Instant.now().minusSeconds(3 - i),
                "INFO",
                this.name + " ready (canned line #" + (i + 1) + ")"
            ));
        }
    }

    @Override public String engineId() { return code; }
    @Override public String displayName() { return name; }
    @Override public EngineStatus status() { return status.get(); }
    @Override public Instant lastTransitionAt() { return lastTransitionAt; }
    @Override public EngineMode currentMode() { return EngineMode.MOCK; }

    @Override
    public EngineActionResult start() {
        transitionLock.lock();
        try {
            if (status.get() == EngineStatus.RUNNING) {
                throw new IllegalStateException("Already running");
            }
            status.set(EngineStatus.RUNNING);
            lastTransitionAt = Instant.now();
            push(new LogLine(lastTransitionAt, "INFO", name + " started (mock)"));
            return new EngineActionResult(code, name, status.get(), name + " started (mock).", lastTransitionAt);
        } finally {
            transitionLock.unlock();
        }
    }

    @Override
    public EngineActionResult stop() {
        transitionLock.lock();
        try {
            if (status.get() == EngineStatus.STOPPED) {
                throw new IllegalStateException("Already stopped");
            }
            status.set(EngineStatus.STOPPED);
            lastTransitionAt = Instant.now();
            push(new LogLine(lastTransitionAt, "INFO", name + " stopped (mock)"));
            return new EngineActionResult(code, name, status.get(), name + " stopped (mock).", lastTransitionAt);
        } finally {
            transitionLock.unlock();
        }
    }

    @Override
    public List<LogLine> getLogs(int limit) {
        synchronized (buffer) {
            // last N lines, oldest first
            int total = buffer.size();
            int skip = Math.max(0, total - limit);
            List<LogLine> out = new ArrayList<>(Math.min(limit, total));
            int i = 0;
            for (LogLine l : buffer) {
                if (i++ >= skip) out.add(l);
            }
            return out;
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void heartbeat() {
        if (status.get() != EngineStatus.RUNNING) {
            return; // no-op while stopped (SPEC §6.1)
        }
        push(new LogLine(Instant.now(), "INFO", name + " heartbeat"));
    }

    private void push(LogLine line) {
        synchronized (buffer) {
            buffer.addLast(line);
            while (buffer.size() > 500) {
                buffer.removeFirst();
            }
        }
    }
}
