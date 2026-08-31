package com.enginehelm.engine;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enginehelm.host.HostService;
import com.enginehelm.host.HostSummaryDto;

import jakarta.validation.Valid;

/**
 * Public REST surface for engine config. <b>Not</b> named
 * {@code EngineController} (SPEC §7.1, README "do not" list).
 * The per-action start / stop / status / logs endpoints will be
 * added here later by {@code ssh-execution-service}.
 */
@RestController
@RequestMapping("/api/admin")
public class EngineControlController {

    private final HostService hosts;
    private final EngineConfigService engines;

    public EngineControlController(HostService hosts, EngineConfigService engines) {
        this.hosts = hosts;
        this.engines = engines;
    }

    @GetMapping("/hosts")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','ADMIN')")
    public List<HostSummaryDto> listHosts() {
        return hosts.list();
    }

    @GetMapping("/engines")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','ADMIN')")
    public List<EngineDto> listEngines() {
        return engines.list();
    }

    @PostMapping("/engines/validate")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public BashValidationResult validateEngines(@RequestBody Map<String, String> body) {
        Map<String, String> scripts = new LinkedHashMap<>();
        for (String slot : BashSafetyScanner.SLOTS) {
            scripts.put(slot, body.getOrDefault(slot + "Script", ""));
        }
        return engines.validate(scripts);
    }

    @PostMapping("/engines")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ResponseEntity<?> createEngine(@Valid @RequestBody EngineCreateRequest req,
                                          Principal principal) {
        try {
            EngineDto created = engines.create(req, principal.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (BashSyntaxException e) {
            return ResponseEntity.badRequest().body(e.getResult());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage()));
        }
    }
}
