package com.BPL_Order_Engine_Admin.manager.config;

import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;
import com.BPL_Order_Engine_Admin.manager.engine.EngineRepository;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import com.BPL_Order_Engine_Admin.manager.user.User;
import com.BPL_Order_Engine_Admin.manager.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.LinkedHashSet;

/**
 * Dev-only seed (replaces the v0.2 in-memory users). Runs only when
 * the {@code dev} profile is active.
 *
 * <p>Seeds:
 * <ul>
 *   <li>{@code sysadmin} / {@code sysadmin123} — SYS_ADMIN, must change password.</li>
 *   <li>{@code admin}    / {@code admin123}    — ADMIN, no must-change.</li>
 *   <li>{@code user1}    / {@code user123}     — USER, assigned to {@code BPL}.</li>
 *   <li>{@code user2}    / {@code user123}     — USER, assigned to {@code PCL}.</li>
 *   <li>Engines {@code BPL} (MOCK, RUNNING) and {@code PCL} (MOCK, STOPPED).</li>
 * </ul>
 *
 * <p>The BCrypt hashes are computed by the same
 * {@link PasswordEncoder} bean the auth flow uses, so the seed
 * always round-trips.
 */
@Configuration
@Profile("dev")
public class DevDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    @Bean
    public CommandLineRunner seedDevData(
            UserRepository userRepository,
            EngineRepository engineRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                seedUser(userRepository, passwordEncoder, "sysadmin", "sysadmin123", RoleType.SYS_ADMIN, true, null);
                seedUser(userRepository, passwordEncoder, "admin",    "admin123",    RoleType.ADMIN,    false, null);
                EngineEntity bpl = seedEngine(engineRepository, "BPL", "BPL Order Engine", "mock-only", EngineMode.MOCK, EngineStatus.RUNNING);
                EngineEntity pcl = seedEngine(engineRepository, "PCL", "PCL Order Engine", "mock-only", EngineMode.MOCK, EngineStatus.STOPPED);
                seedUser(userRepository, passwordEncoder, "user1", "user123", RoleType.USER, true, new LinkedHashSet<>(java.util.List.of(bpl)));
                seedUser(userRepository, passwordEncoder, "user2", "user123", RoleType.USER, true, new LinkedHashSet<>(java.util.List.of(pcl)));
                log.info("Seeded dev users and engines");
            }
        };
    }

    private static void seedUser(
            UserRepository repo,
            PasswordEncoder enc,
            String username,
            String password,
            RoleType role,
            boolean mustChange,
            java.util.Set<EngineEntity> engines) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(enc.encode(password));
        u.setRoleType(role);
        u.setMustChangePassword(mustChange);
        if (engines != null) {
            u.setAssignedEngines(engines);
        }
        repo.save(u);
    }

    private static EngineEntity seedEngine(
            EngineRepository repo,
            String code,
            String name,
            String password,
            EngineMode mode,
            EngineStatus status) {
        EngineEntity e = new EngineEntity();
        e.setCode(code);
        e.setName(name);
        e.setServerIp("127.0.0.1");
        e.setServerUsername(code.toLowerCase() + "-mock");
        e.setServerPassword(password);
        e.setMode(mode);
        e.setStartScript("echo '" + code + " started'");
        e.setStopScript("echo '" + code + " stopped'");
        e.setLogScript("tail -F /tmp/" + code.toLowerCase() + ".log");
        e.setStatus(status);
        e.setLastTransitionAt(Instant.now());
        return repo.save(e);
    }
}
