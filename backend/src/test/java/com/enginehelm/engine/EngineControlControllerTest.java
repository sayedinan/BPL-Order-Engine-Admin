package com.enginehelm.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.enginehelm.host.Host;
import com.enginehelm.host.HostService;
import com.enginehelm.host.HostSummaryDto;
import com.enginehelm.security.SecurityConfig;
import com.enginehelm.user.AppUserDetailsService;

@WebMvcTest(controllers = EngineControlController.class)
@Import({SecurityConfig.class})
@TestPropertySource(properties = {
        "enginehelm.cors.allowed-origins=http://localhost:5173"
})
class EngineControlControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private HostService hosts;

    @MockBean
    private EngineConfigService engines;

    @MockBean
    private AppUserDetailsService userDetailsService;

    @Test
    void unauthenticatedReturns401() throws Exception {
        mvc.perform(get("/api/admin/engines"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListEngines() throws Exception {
        Host h = new Host();
        h.setId(7L);
        h.setAlias("web-tier-host");
        h.setHostnameOrIp("web.invalid");
        h.setPort(22);
        when(engines.list()).thenReturn(List.of(
                new EngineDto(1L, "eng-web-01", 7L, "web-tier-host", "web.invalid", 22)));

        mvc.perform(get("/api/admin/engines").with(user("admin@local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("eng-web-01"));
    }

    @Test
    void adminCanListHosts() throws Exception {
        when(hosts.list()).thenReturn(List.of(
                new HostSummaryDto(1L, "web-tier-host", "web.invalid", 22)));

        mvc.perform(get("/api/admin/hosts").with(user("admin@local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alias").value("web-tier-host"));
    }

    @Test
    void adminCannotCreateEngine() throws Exception {
        mvc.perform(post("/api/admin/engines")
                        .with(user("admin@local").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "eng-new",
                                  "hostId": 1,
                                  "startScript": "echo s",
                                  "stopScript": "echo t",
                                  "statusScript": "echo u",
                                  "logScript": "echo l"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void sysAdminCreateWithSyntaxErrorReturns400() throws Exception {
        BashValidationResult bad = new BashValidationResult(
                Map.of(
                        "start", new BashValidationResult.ScriptCheck(0, ""),
                        "stop", new BashValidationResult.ScriptCheck(1, "syntax error near 'then'"),
                        "status", new BashValidationResult.ScriptCheck(0, ""),
                        "log", new BashValidationResult.ScriptCheck(0, "")),
                List.of());
        when(engines.create(any(), anyString())).thenThrow(new BashSyntaxException(bad));

        mvc.perform(post("/api/admin/engines")
                        .with(user("sysadmin@local").roles("SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "eng-bad",
                                  "hostId": 1,
                                  "startScript": "echo s",
                                  "stopScript": "if then fi",
                                  "statusScript": "echo u",
                                  "logScript": "echo l"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.perScript.stop.exitCode").value(1));
    }

    @Test
    void sysAdminValidateReturnsResult() throws Exception {
        BashValidationResult r = new BashValidationResult(
                Map.of(
                        "start", new BashValidationResult.ScriptCheck(0, ""),
                        "stop", new BashValidationResult.ScriptCheck(0, ""),
                        "status", new BashValidationResult.ScriptCheck(0, ""),
                        "log", new BashValidationResult.ScriptCheck(0, "")),
                List.of());
        when(engines.validate(any())).thenReturn(r);

        mvc.perform(post("/api/admin/engines/validate")
                        .with(user("sysadmin@local").roles("SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startScript": "echo s",
                                  "stopScript": "echo t",
                                  "statusScript": "echo u",
                                  "logScript": "echo l"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perScript.start.exitCode").value(0));
    }
}
