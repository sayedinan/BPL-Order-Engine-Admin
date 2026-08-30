package com.BPL_Order_Engine_Admin.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for the dev environment. Permits the Vite dev
 * server (default origin {@code http://localhost:5173}) to call the
 * API at {@code http://localhost:8080} with credentials (HTTP Basic
 * auth header). When this app is deployed behind a real frontend
 * domain, replace the allow-origin list accordingly.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Vite default; explicit list rather than "*" because
        // allowCredentials(true) is incompatible with a wildcard.
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // Required for HTTP Basic &mdash; browsers only send the
        // Authorization header on cross-origin XHR when this is true.
        config.setAllowCredentials(true);
        // Keep preflight responses cacheable for 1 hour so the SPA
        // doesn't re-OPTIONS every request.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
