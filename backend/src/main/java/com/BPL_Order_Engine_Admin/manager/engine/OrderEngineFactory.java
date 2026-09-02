package com.BPL_Order_Engine_Admin.manager.engine;

import com.BPL_Order_Engine_Admin.manager.engine.impl.MockEngineOperations;
import com.BPL_Order_Engine_Admin.manager.engine.impl.SshBackedEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * v0.3 engine factory (SPEC §3.7). Looks up an engine row by
 * {@code code} from the database, then constructs the matching
 * {@link OrderEngineOperations} implementation.
 *
 * <p>Source of truth is the database row, not Spring beans. Adding
 * a new engine is a row, not a class.
 *
 * <p>The {@link MockEngineOperations} constructor wraps a row
 * (state is per-instance). The {@code SshBackedEngine} is a
 * per-request wrapper around the row + the {@code SshClientProvider}
 * cache (built in #19).
 */
@Component
public class OrderEngineFactory {

    private final EngineRepository engineRepository;
    private final SshClientProvider sshClientProvider;
    private final Duration connectTimeout;
    private final Duration startStopTimeout;
    private final Duration logsOpTimeout;

    public OrderEngineFactory(
            EngineRepository engineRepository,
            SshClientProvider sshClientProvider,
            @Value("${app.ssh.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ssh.start-stop-timeout:30s}") Duration startStopTimeout,
            @Value("${app.ssh.logs-operation-timeout:10s}") Duration logsOpTimeout) {
        this.engineRepository = engineRepository;
        this.sshClientProvider = sshClientProvider;
        this.connectTimeout = connectTimeout;
        this.startStopTimeout = startStopTimeout;
        this.logsOpTimeout = logsOpTimeout;
    }

    public OrderEngineOperations get(String code) {
        EngineEntity engine = engineRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseThrow(() -> new EngineNotSupportedException(code));
        return switch (engine.getMode()) {
            case MOCK -> new MockEngineOperations(engine);
            case REAL -> new SshBackedEngine(engine, sshClientProvider,
                connectTimeout, startStopTimeout, logsOpTimeout);
        };
    }
}
