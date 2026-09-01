package com.BPL_Order_Engine_Admin.manager.config;

import com.BPL_Order_Engine_Admin.manager.auth.JwtAuthFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;
import java.util.Map;

/**
 * v0.3 Spring Security configuration (SPEC §4 / API.md §0.3).
 *
 * <p>Filter chain: JWT filter (parses token + populates
 * SecurityContext), then Spring Security's authorization rules.
 *
 * <p>Path matchers (per RBAC matrix):
 * <ul>
 *   <li>POST /api/auth/login — public.</li>
 *   <li>POST /api/auth/logout — authenticated (any role).</li>
 *   <li>GET /api/auth/me — authenticated.</li>
 *   <li>POST /api/auth/change-password — authenticated.</li>
 *   <li>GET /api/audit-logs — SYS_ADMIN, ADMIN (USER is 403 outright in the controller).</li>
 *   <li>Everything else under /api/** — authenticated; the controller
 *       enforces per-resource role checks via {@code @PreAuthorize}.</li>
 *   <li>WS /api/engines/{code}/logs/stream — authenticated (the
 *       handler enforces role + assignment in #20).</li>
 * </ul>
 *
 * <p>CSRF disabled (API-only). Stateless sessions (JWT is the only
 * auth). CORS via {@link CorsConfig}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // WebSocket: the JwtAuthFilter doesn't apply (the
                // handshake is a different code path); the
                // EngineLogsWebSocketHandler enforces the JWT +
                // role+assignment check itself.
                .requestMatchers("/api/engines/*/logs/stream").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(unauthorizedEntryPoint())
                .accessDeniedHandler(forbiddenHandler()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(Customizer.withDefaults()); // disabled by STATELESS, but kept explicit
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /** 401 with the standard error envelope. */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI(), null);
        };
    }

    /** 403 with the standard error envelope. Generic message — no resource path leak. */
    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, ex) -> {
            writeError(response, HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI(), null);
        };
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path, Map<String, Object> details) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        if (details != null) {
            body.put("details", details);
        }
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
