package com.BPL_Order_Engine_Admin.manager.engine;

import jakarta.annotation.PreDestroy;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * v0.3 per-engine SSH client cache (SPEC §6.2).
 *
 * <p>One {@link SshClient} per engine code. The client is opened
 * lazily on the first call and closed after {@code app.ssh.idle-eviction}
 * of inactivity (default 5m). The cached client amortizes the
 * connect cost across multiple operations (status, start, stop,
 * tail).
 *
 * <p>Apache MINA SSHD's {@link SshClient} is thread-safe — multiple
 * {@link ClientSession}s can be created from it. We don't bother
 * with a per-session pool.
 */
@Component
public class SshClientProvider {

    private static final Logger log = LoggerFactory.getLogger(SshClientProvider.class);

    private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Duration idleEviction;

    public SshClientProvider(
            @Value("${app.ssh.idle-eviction:5m}") Duration idleEviction) {
        this.idleEviction = idleEviction;
        // Daemon thread; single thread is fine — we only sweep once
        // per idle-eviction interval.
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "ssh-client-evictor");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(this::evictIdle, idleEviction.toMillis(),
            idleEviction.toMillis(), TimeUnit.MILLISECONDS);
        this.scheduler = exec;
    }

    /** Get (or open) a cached SshClient for the engine. */
    public SshClient get(String engineCode) {
        CachedClient cached = cache.computeIfAbsent(engineCode, k -> {
            log.info("Opening new SshClient for engine {}", k);
            SshClient client = SshClient.setUpDefaultClient();
            client.start();
            return new CachedClient(client, System.nanoTime());
        });
        cached.lastUsedNanos = System.nanoTime();
        return cached.client;
    }

    /** Invalidate the cached client (e.g. on PATCH ssh). */
    public void invalidate(String engineCode) {
        CachedClient cached = cache.remove(engineCode);
        if (cached != null) {
            try {
                cached.client.stop();
            } catch (Exception e) {
                log.warn("Error stopping SshClient for engine {}", engineCode, e);
            }
        }
    }

    private void evictIdle() {
        long now = System.nanoTime();
        long idleNanos = idleEviction.toNanos();
        for (var entry : cache.entrySet()) {
            if (now - entry.getValue().lastUsedNanos > idleNanos) {
                if (cache.remove(entry.getKey(), entry.getValue())) {
                    try {
                        entry.getValue().client.stop();
                    } catch (Exception e) {
                        log.warn("Error stopping idle SshClient", e);
                    }
                    log.info("Evicted idle SshClient for engine {}", entry.getKey());
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        for (var entry : cache.values()) {
            try {
                entry.client.stop();
            } catch (Exception e) {
                log.warn("Error stopping SshClient on shutdown", e);
            }
        }
        cache.clear();
    }

    private static final class CachedClient {
        final SshClient client;
        volatile long lastUsedNanos;
        CachedClient(SshClient client, long lastUsedNanos) {
            this.client = client;
            this.lastUsedNanos = lastUsedNanos;
        }
    }
}
