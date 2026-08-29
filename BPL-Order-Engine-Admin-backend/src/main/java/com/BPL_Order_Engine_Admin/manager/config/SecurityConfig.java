package com.BPL_Order_Engine_Admin.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration for the admin app.
 *
 * <p>Uses HTTP Basic with two in-memory users &mdash; one
 * {@code ADMIN} and one {@code VIEWER} &mdash; matching the credentials
 * listed in the task prompt. Path-based authorization enforces the role
 * matrix in SPEC &sect;3.4.
 *
 * <p>CSRF is disabled because the API is consumed by a Vite SPA
 * sending JSON from a different origin (handled by {@link CorsConfig}).
 * Sessions are stateless so each request carries its own credentials
 * (typical for Basic auth).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // Apply CORS from CorsConfig so the Vite dev origin can
                // call /api/** with credentials.
                .cors(c -> c.configurationSource(corsConfigurationSource))
                // API-only, no server-rendered forms; CSRF off.
                .csrf(AbstractHttpConfigurer::disable)
                // Pure Basic auth, no server session.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> {})
                // Role-based authorization (see SPEC &sect;3.4).
                .authorizeHttpRequests(auth -> auth
                        // READ endpoints: ADMIN or VIEWER.
                        .requestMatchers(HttpMethod.GET, "/api/engines/*/status").hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/engines/*/logs").hasAnyRole("ADMIN", "VIEWER")
                        // WRITE endpoints: ADMIN only.
                        .requestMatchers(HttpMethod.POST, "/api/engines/*/start").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/engines/*/stop").hasRole("ADMIN")
                        // Spring's default error mapping endpoint must
                        // remain reachable so unauthenticated/unauthorized
                        // responses can be turned into the standard error
                        // envelope by the dispatcher.
                        .requestMatchers("/error").permitAll()
                        // Everything else (incl. any future endpoints)
                        // requires auth.
                        .anyRequest().authenticated())
                // Return 401 (not 403) when no credentials are present
                // so the frontend can distinguish "not logged in" from
                // "logged in but not allowed".
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\","
                                            + "\"message\":\"Authentication required\","
                                            + "\"path\":\"" + req.getRequestURI() + "\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"status\":403,\"error\":\"Forbidden\","
                                            + "\"message\":\"Access denied\","
                                            + "\"path\":\"" + req.getRequestURI() + "\"}");
                        }));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is the modern default; no plain-text passwords stored.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails viewer = User.builder()
                .username("viewer")
                .password(encoder.encode("viewer123"))
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, viewer);
    }
}
