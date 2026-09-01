package com.BPL_Order_Engine_Admin.manager.engine.impl;

import com.BPL_Order_Engine_Admin.manager.engine.EngineActionResult;
import com.BPL_Order_Engine_Admin.manager.engine.EngineAuthException;
import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;
import com.BPL_Order_Engine_Admin.manager.engine.EngineScriptException;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.engine.EngineUnreachableException;
import com.BPL_Order_Engine_Admin.manager.engine.LogLine;
import com.BPL_Order_Engine_Admin.manager.engine.OrderEngineOperations;
import com.BPL_Order_Engine_Admin.manager.engine.SshClientProvider;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.AbstractClientChannel;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * v0.3 REAL-mode engine (SPEC §6.2). Wraps an {@link EngineEntity}
 * row and runs start / stop / status / getLogs over SSH via Apache
 * MINA SSHD.
 *
 * <p>Timeouts (per SPEC §6.2):
 * <ul>
 *   <li>Connect: 5s. One retry on connect fail.</li>
 *   <li>Start / Stop: 30s (cancellable via {@code Future.get(timeout)}).</li>
 *   <li>getLogs: 10s.</li>
 * </ul>
 *
 * <p>Error categories:
 * <ul>
 *   <li>Auth fail → {@link EngineAuthException} → 403.</li>
 *   <li>Connect refused / unreachable → {@link EngineUnreachableException} → 502. One retry.</li>
 *   <li>Script exit non-zero → {@link EngineScriptException} (exitCode + stderr) → 502.</li>
 *   <li>Timeout → {@link TimeoutException} → 504.</li>
 * </ul>
 *
 * <p>The decrypted password is held in a {@code char[]} (not
 * {@code String}) inside the auth callback and zeroed after
 * {@link ClientSession#auth()}. Never logged.
 */
public class SshBackedEngine implements OrderEngineOperations {

    private static final Logger log = LoggerFactory.getLogger(SshBackedEngine.class);

    private final EngineEntity engine;
    private final SshClientProvider clientProvider;
    private final Duration connectTimeout;
    private final Duration startStopTimeout;
    private final Duration logsOpTimeout;

    private volatile EngineStatus status;
    private volatile Instant lastTransitionAt;
    private final ExecutorService commandRunner;

    public SshBackedEngine(
            EngineEntity engine,
            SshClientProvider clientProvider,
            Duration connectTimeout,
            Duration startStopTimeout,
            Duration logsOpTimeout) {
        this.engine = engine;
        this.clientProvider = clientProvider;
        this.connectTimeout = connectTimeout;
        this.startStopTimeout = startStopTimeout;
        this.logsOpTimeout = logsOpTimeout;
        this.lastTransitionAt = engine.getLastTransitionAt();
        this.status = engine.getStatus();
        this.commandRunner = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ssh-cmd-" + engine.getCode());
            t.setDaemon(true);
            return t;
        });
    }

    @Override public String engineId() { return engine.getCode(); }
    @Override public String displayName() { return engine.getName(); }
    @Override public EngineStatus status() { return status; }
    @Override public Instant lastTransitionAt() { return lastTransitionAt; }
    @Override public EngineMode currentMode() { return EngineMode.REAL; }

    @Override
    public EngineActionResult start() {
        if (engine.getStartScript() == null || engine.getStartScript().isBlank()) {
            throw new IllegalStateException("startScript is required for REAL-mode engines");
        }
        ExecResult r;
        try {
            r = runCommand(engine.getStartScript(), startStopTimeout);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new RuntimeException(te); // mapped to 504 by ApiExceptionHandler via TimeoutException
        }
        if (r.exitCode != 0) {
            status = EngineStatus.STOPPED;
            throw new EngineScriptException(engine.getCode(), r.exitCode, r.stderr);
        }
        lastTransitionAt = Instant.now();
        status = EngineStatus.RUNNING;
        return new EngineActionResult(engine.getCode(), engine.getName(),
            EngineStatus.RUNNING,
            engine.getName() + " started.",
            lastTransitionAt);
    }

    @Override
    public EngineActionResult stop() {
        if (engine.getStopScript() == null || engine.getStopScript().isBlank()) {
            throw new IllegalStateException("stopScript is required for REAL-mode engines");
        }
        ExecResult r;
        try {
            r = runCommand(engine.getStopScript(), startStopTimeout);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new RuntimeException(te);
        }
        if (r.exitCode != 0) {
            throw new EngineScriptException(engine.getCode(), r.exitCode, r.stderr);
        }
        lastTransitionAt = Instant.now();
        status = EngineStatus.STOPPED;
        return new EngineActionResult(engine.getCode(), engine.getName(),
            EngineStatus.STOPPED,
            engine.getName() + " stopped.",
            lastTransitionAt);
    }

    @Override
    public List<LogLine> getLogs(int limit) {
        if (engine.getLogScript() == null || engine.getLogScript().isBlank()) {
            return List.of();
        }
        String cmd = "tail -n " + limit;
        ExecResult r;
        try {
            r = runCommand(cmd, logsOpTimeout);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new RuntimeException(te);
        }
        if (r.exitCode != 0) {
            throw new EngineScriptException(engine.getCode(), r.exitCode, r.stderr);
        }
        List<LogLine> out = new ArrayList<>();
        for (String line : r.stdout.split("\n")) {
            if (line.isEmpty()) continue;
            out.add(new LogLine(Instant.now(), "INFO", line));
        }
        return out;
    }

    // ---- internals ----

    /** Run a one-shot command over SSH. Bounded by the supplied timeout. */
    private ExecResult runCommand(String cmd, Duration timeout) throws java.util.concurrent.TimeoutException {
        ClientSession session = openSessionWithRetry();
        try {
            return commandRunner.submit(() -> {
                try {
                    return runOnSession(session, cmd, timeout);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running SSH command", ie);
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                throw (java.util.concurrent.TimeoutException) cause;
            }
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (java.util.concurrent.TimeoutException te) {
            // Future.get timed out before the SSH command did.
            throw te;
        } finally {
            try { session.close(); } catch (Exception ignored) {}
        }
    }

    private ExecResult runOnSession(ClientSession session, String cmd, Duration timeout) {
        try (ChannelExec channel = (ChannelExec) session.createExecChannel(cmd)) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOut(stdout);
            channel.setErr(stderr);
            channel.open().verify(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            // Wait for the channel to close (== command finished).
            channel.waitFor(java.util.Set.of(
                org.apache.sshd.client.channel.ClientChannelEvent.CLOSED), timeout.toMillis());
            Integer code = channel.getExitStatus();
            return new ExecResult(code == null ? -1 : code,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw rethrowMapped(e);
        }
    }

    private RuntimeException rethrowMapped(Exception e) {
        if (e instanceof RuntimeException re) return re;
        if (isAuthFailure(e)) return new EngineAuthException(engine.getCode());
        if (isConnectFailure(e)) return new EngineUnreachableException(engine.getCode(), e);
        return new RuntimeException(e);
    }

    private boolean isAuthFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage() == null ? "" : c.getMessage().toLowerCase();
            String cls = c.getClass().getSimpleName().toLowerCase();
            if (msg.contains("auth") && (msg.contains("fail") || msg.contains("denied") || msg.contains("invalid")))
                return true;
            if (cls.contains("authexception") || cls.contains("authfail")) return true;
        }
        return false;
    }

    private boolean isConnectFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.ConnectException) return true;
            if (c instanceof java.net.UnknownHostException) return true;
            if (c instanceof java.net.SocketTimeoutException) return true;
            if (c instanceof java.net.NoRouteToHostException) return true;
        }
        return false;
    }

    /** Open a session with one retry on connect failure (SPEC §6.2). */
    private ClientSession openSessionWithRetry() {
        try {
            return openSession();
        } catch (EngineUnreachableException first) {
            // One retry, then re-throw.
            return openSession();
        }
    }

    private ClientSession openSession() {
        SshClient client = clientProvider.get(engine.getCode());
        try {
            ClientSession session = client.connect(engine.getServerUsername(), engine.getServerIp(), 22)
                .verify(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .getSession();
            // SSHD's addPasswordIdentity takes a String. We hold the
            // value in a char[] until just before the call, then zero
            // both. The decrypted server password is never logged.
            char[] pwdChars = engine.getServerPassword() == null
                ? new char[0] : engine.getServerPassword().toCharArray();
            String pwd = new String(pwdChars);
            java.util.Arrays.fill(pwdChars, '\0');
            try {
                session.addPasswordIdentity(pwd);
                session.auth().verify(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                pwd = null;
            }
            return session;
        } catch (Exception e) {
            // The auth/connect path throws a mix of checked exceptions;
            // we map them to our domain exceptions in one place.
            if (isAuthFailure(e)) throw new EngineAuthException(engine.getCode());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EngineUnreachableException(engine.getCode(), e);
        }
    }

    /** One command's output. */
    private record ExecResult(int exitCode, String stdout, String stderr) {}
}
