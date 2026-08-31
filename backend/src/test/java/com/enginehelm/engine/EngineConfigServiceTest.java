package com.enginehelm.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.enginehelm.audit.AuditLogCategory;
import com.enginehelm.audit.AuditLogService;
import com.enginehelm.host.Host;
import com.enginehelm.host.HostRepository;
import com.enginehelm.user.SystemRole;
import com.enginehelm.user.User;
import com.enginehelm.user.UserRepository;

class EngineConfigServiceTest {

    private EngineRepository engines;
    private HostRepository hosts;
    private UserRepository users;
    private BashSafetyScanner scanner;
    private AuditLogService audit;
    private EngineConfigService service;

    @BeforeEach
    void setUp() {
        engines = mock(EngineRepository.class);
        hosts = mock(HostRepository.class);
        users = mock(UserRepository.class);
        scanner = new BashSafetyScanner();
        audit = mock(AuditLogService.class);
        service = new EngineConfigService(engines, hosts, users, scanner, audit);
    }

    @Test
    void createPersistsEngineAndWritesAudit() {
        Host host = newHost(7L, "web-tier-host");
        when(hosts.findById(7L)).thenReturn(Optional.of(host));
        when(engines.existsByName("eng-test")).thenReturn(false);
        when(engines.save(any(Engine.class))).thenAnswer(inv -> {
            Engine e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        User actor = new User();
        actor.setId(1L);
        actor.setUsername("sysadmin@local");
        actor.setSystemRole(SystemRole.SYS_ADMIN);
        when(users.findByUsername("sysadmin@local")).thenReturn(Optional.of(actor));

        EngineCreateRequest req = new EngineCreateRequest();
        req.setName("eng-test");
        req.setHostId(7L);
        req.setStartScript("echo start");
        req.setStopScript("echo stop");
        req.setStatusScript("echo status");
        req.setLogScript("echo log");

        EngineDto dto = service.create(req, "sysadmin@local");

        assertThat(dto.id()).isEqualTo(99L);
        assertThat(dto.name()).isEqualTo("eng-test");
        assertThat(dto.hostAlias()).isEqualTo("web-tier-host");
        verify(audit, times(1)).record(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createWithBashSyntaxErrorThrows() {
        Host host = newHost(7L, "web-tier-host");
        when(hosts.findById(7L)).thenReturn(Optional.of(host));
        when(engines.existsByName("eng-bad")).thenReturn(false);

        EngineCreateRequest req = new EngineCreateRequest();
        req.setName("eng-bad");
        req.setHostId(7L);
        req.setStartScript("echo start");
        req.setStopScript("if then fi");   // syntax error
        req.setStatusScript("echo status");
        req.setLogScript("echo log");

        assertThatThrownBy(() -> service.create(req, "sysadmin@local"))
                .isInstanceOf(BashSyntaxException.class)
                .satisfies(e -> assertThat(((BashSyntaxException) e).getResult().hasBlockingFailure())
                        .isTrue());

        verify(engines, never()).save(any(Engine.class));
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(),
                any(AuditLogCategory.class), any(), any(), any(), any());
    }

    @Test
    void createIsSysAdminOnly() {
        // The @PreAuthorize annotation is enforced by Spring Security's
        // method-security advisor. Without the advisor (e.g. in a
        // pure unit test that bypasses Spring), the call succeeds —
        // but a real deployment requires ROLE_SYS_ADMIN. Verify the
        // annotation is present at the class / method level.
        // This is a static check; it documents the contract that
        // the controller + method-security advisor enforce at runtime.
        assertThat(EngineConfigService.class.isAnnotationPresent(
                org.springframework.stereotype.Service.class)).isTrue();

        // Sanity: the create method's @PreAuthorize is wired in via
        // @EnableMethodSecurity on EngineHelmApplication.
        try {
            var m = EngineConfigService.class.getMethod("create",
                    EngineCreateRequest.class, String.class);
            var ann = m.getAnnotation(
                    org.springframework.security.access.prepost.PreAuthorize.class);
            assertThat(ann).isNotNull();
            assertThat(ann.value()).contains("SYS_ADMIN");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("create signature changed unexpectedly", e);
        }
    }

    @Test
    void auditRowIsWrittenWithConfigCategory() {
        // When the test as 'createPersistsEngineAndWritesAudit' runs,
        // we already check that audit.record is called once. Here we
        // confirm the exact category / action expected.
        // (re-uses mocks from setUp)
        Host host = newHost(7L, "web-tier-host");
        when(hosts.findById(7L)).thenReturn(Optional.of(host));
        when(engines.existsByName("eng-x")).thenReturn(false);
        when(engines.save(any(Engine.class))).thenAnswer(inv -> {
            Engine e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });

        User actor = new User();
        actor.setId(1L);
        actor.setUsername("sysadmin@local");
        actor.setSystemRole(SystemRole.SYS_ADMIN);
        when(users.findByUsername("sysadmin@local")).thenReturn(Optional.of(actor));

        EngineCreateRequest req = new EngineCreateRequest();
        req.setName("eng-x");
        req.setHostId(7L);
        req.setStartScript("echo s");
        req.setStopScript("echo t");
        req.setStatusScript("echo u");
        req.setLogScript("echo l");

        service.create(req, "sysadmin@local");

        org.mockito.ArgumentCaptor<AuditLogCategory> cat =
                org.mockito.ArgumentCaptor.forClass(AuditLogCategory.class);
        org.mockito.ArgumentCaptor<String> action =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(audit).record(any(), any(), any(), any(), any(),
                action.capture(), cat.capture(),
                any(), any(), any(), any());
        assertThat(action.getValue()).isEqualTo("engine_config_change");
        assertThat(cat.getValue()).isEqualTo(AuditLogCategory.CONFIG);
    }

    private static Host newHost(Long id, String alias) {
        Host h = new Host();
        h.setId(id);
        h.setAlias(alias);
        h.setHostnameOrIp("host.invalid");
        h.setPort(22);
        h.setSshUsername("engine");
        h.setHostKeyFingerprint("SHA256:PLACEHOLDER");
        h.setDefaultCredentialId(1L);
        return h;
    }
}
