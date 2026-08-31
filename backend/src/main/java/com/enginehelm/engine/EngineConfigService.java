package com.enginehelm.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enginehelm.audit.AuditLogCategory;
import com.enginehelm.audit.AuditLogService;
import com.enginehelm.host.Host;
import com.enginehelm.host.HostRepository;
import com.enginehelm.user.User;
import com.enginehelm.user.UserRepository;

/**
 * Read / write engine config. The write path is sys.admin-only and
 * goes through {@link BashSafetyScanner}; on success, the
 * {@code engine_config_change} row is written via
 * {@link AuditLogService}.
 */
@Service
public class EngineConfigService {

    private final EngineRepository engines;
    private final HostRepository hosts;
    private final UserRepository users;
    private final BashSafetyScanner scanner;
    private final AuditLogService audit;

    public EngineConfigService(EngineRepository engines,
                               HostRepository hosts,
                               UserRepository users,
                               BashSafetyScanner scanner,
                               AuditLogService audit) {
        this.engines = engines;
        this.hosts = hosts;
        this.users = users;
        this.scanner = scanner;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EngineDto> list() {
        Map<Long, Host> byId = new LinkedHashMap<>();
        for (Host h : hosts.findAll()) byId.put(h.getId(), h);
        return engines.findAll().stream()
                .map(e -> EngineDto.from(e, byId.get(e.getHostId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EngineDto get(Long id) {
        Engine e = engines.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("engine not found: " + id));
        Host h = hosts.findById(e.getHostId()).orElse(null);
        return EngineDto.from(e, h);
    }

    @Transactional(readOnly = true)
    public BashValidationResult validate(Map<String, String> scripts) {
        return scanner.scan(scripts);
    }

    @Transactional
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public EngineDto create(EngineCreateRequest req, String actorUsername) {
        if (engines.existsByName(req.getName())) {
            throw new IllegalArgumentException("engine name already exists: " + req.getName());
        }
        Host host = hosts.findById(req.getHostId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "host not found: " + req.getHostId()));

        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("start", req.getStartScript());
        scripts.put("stop", req.getStopScript());
        scripts.put("status", req.getStatusScript());
        scripts.put("log", req.getLogScript());

        BashValidationResult result = scanner.scan(scripts);
        if (result.hasBlockingFailure()) {
            throw new BashSyntaxException(result);
        }

        Engine e = new Engine();
        e.setName(req.getName());
        e.setHostId(host.getId());
        e.setStartScript(req.getStartScript());
        e.setStopScript(req.getStopScript());
        e.setStatusScript(req.getStatusScript());
        e.setLogScript(req.getLogScript());
        Engine saved = engines.save(e);

        User actor = users.findByUsername(actorUsername).orElse(null);
        audit.record(
                actor,
                actorUsername,
                actor == null ? "unknown" : actor.getSystemRole().persisted(),
                null,
                saved.getId(),
                "engine_config_change",
                AuditLogCategory.CONFIG,
                null,
                null,
                null,
                "created engine " + saved.getName());

        return EngineDto.from(saved, host);
    }
}
