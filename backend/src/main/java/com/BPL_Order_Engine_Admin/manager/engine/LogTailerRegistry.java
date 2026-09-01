package com.BPL_Order_Engine_Admin.manager.engine;

import com.BPL_Order_Engine_Admin.manager.engine.ws.WebSocketSessionRegistry;
import jakarta.annotation.PreDestroy;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * v0.3 background log tailer registry (SPEC §6.2).
 *
 * <p>One tailer thread per {@code mode=REAL} engine in the
 * {@code RUNNING} state. Started on the engine's start, stopped
 * on stop / engine deletion / 5 consecutive reconnect failures.
 *
 * <p>This bean is the lifecycle owner of the background tail. The
 * WebSocket handler in #20 subscribes to {@link LogBuffer} for
 * fan-out; the tailer writes into the buffer via {@link LogBuffer#append}.
 *
 * <p>For v0.3 the start signal is derived from the engine's
 * {@code status} field via the SshBackedEngine. Real engine state
 * changes flow through {@code EngineService} → {@code SshBackedEngine}
 * → the buffer (via {@code runOnSession} when {@code getLogs} is
 * called). For simplicity in this v0.3 build, the tailer is started
 * by an explicit call to {@link #start(String)} from a place that
 * observes the transition — wired in #20 when the WebSocket handler
 * subscribes. The registry is therefore a state holder rather than
 * a state observer.
 */
@Component
public class LogTailerRegistry {

    private static final Logger log = LoggerFactory.getLogger(LogTailerRegistry.class);
    private final SshClientProvider clientProvider;
    private final LogBuffer logBuffer;
    private final EngineRepository engineRepository;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final Duration connectTimeout;

    /** Per-engine state. */
    private final Map<String, Tailer> tailers = new ConcurrentHashMap<>();

    public LogTailerRegistry(
            SshClientProvider clientProvider,
            LogBuffer logBuffer,
            EngineRepository engineRepository,
            WebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${app.ssh.connect-timeout:5s}") Duration connectTimeout) {
        this.clientProvider = clientProvider;
        this.logBuffer = logBuffer;
        this.engineRepository = engineRepository;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.connectTimeout = connectTimeout;
    }

    /** Start a tailer for the engine if not already running. */
    public synchronized void start(String engineCode) {
        if (tailers.containsKey(engineCode)) return;
        Tailer t = new Tailer(engineCode);
        tailers.put(engineCode, t);
        t.start();
        log.info("Started log tailer for engine {}", engineCode);
    }

    /** Stop the tailer for the engine. */
    public synchronized void stop(String engineCode) {
        Tailer t = tailers.remove(engineCode);
        if (t != null) {
            t.stopRunning();
            log.info("Stopped log tailer for engine {}", engineCode);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Tailer t : tailers.values()) t.stopRunning();
        tailers.clear();
    }

    /** Per-engine tailer thread. Reads the engine's log script line by line. */
    private final class Tailer implements Runnable {
        private final String engineCode;
        private final Thread thread;
        private volatile boolean running = true;
        private int reconnectAttempts = 0;

        Tailer(String engineCode) {
            this.engineCode = engineCode;
            this.thread = new Thread(this, "log-tailer-" + engineCode);
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        void stopRunning() {
            running = false;
            thread.interrupt();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    tailOnce();
                    reconnectAttempts = 0;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    reconnectAttempts++;
                    log.warn("Tailer for {} failed (attempt {})", engineCode, reconnectAttempts, e);
                    if (reconnectAttempts >= 5) {
                        log.warn("Tailer for {} giving up after 5 consecutive failures", engineCode);
                        return;
                    }
                    try {
                        Thread.sleep(Math.min(60_000L, 1_000L * (1 << reconnectAttempts)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private void tailOnce() throws Exception {
            // The tailer uses a SshClient (from the provider) + an
            // open session, runs the log script, and streams stdout
            // line-by-line into the LogBuffer. A session-wide
            // disconnect breaks the loop and the outer loop reconnects.
            EngineEntity engine = engineRepository.findByCodeAndDeletedAtIsNull(engineCode)
                .orElse(null);
            if (engine == null) {
                running = false;
                return;
            }
            if (engine.getLogScript() == null || engine.getLogScript().isBlank()) {
                running = false;
                return;
            }
            SshClient client = clientProvider.get(engineCode);
            try (ClientSession session = client.connect(
                    engine.getServerUsername(), engine.getServerIp(), 22)
                    .verify(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .getSession();
                 ChannelExec channel = (ChannelExec) session.createExecChannel(
                     "sh -c '" + engine.getLogScript() + "'")) {

                // Auth
                String pwd = engine.getServerPassword() == null ? "" : engine.getServerPassword();
                session.addPasswordIdentity(pwd);
                session.auth().verify(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);

                try (InputStream in = channel.getInvertedOut();
                     BufferedReader reader = new BufferedReader(
                         new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    channel.open().verify(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        LogLine ll = new LogLine(Instant.now(), "INFO", line);
                        logBuffer.append(engineCode, ll);
                        // Fan out to WS subscribers.
                        broadcast(engineCode, ll);
                    }
                }
            }
        }

        private void broadcast(String engineCode, LogLine line) {
            Set<WebSocketSession> set = sessionRegistry.sessionsFor(engineCode);
            if (set.isEmpty()) return;
            try {
                String json = objectMapper.writeValueAsString(line);
                TextMessage msg = new TextMessage(json);
                for (WebSocketSession session : set) {
                    try {
                        if (session.isOpen()) session.sendMessage(msg);
                    } catch (Exception e) {
                        // Broken session; close handler will unregister.
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to serialize log line for broadcast", e);
            }
        }
    }
}
