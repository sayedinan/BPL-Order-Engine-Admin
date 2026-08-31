package com.enginehelm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.enginehelm.config.CorsConfig;

import com.enginehelm.user.AppUserDetailsService;

@Configuration
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;
    private final CorsConfig corsConfig;

    public SecurityConfig(AppUserDetailsService uds, CorsConfig corsConfig) {
        this.userDetailsService = uds;
        this.corsConfig = corsConfig;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfig.corsConfigurationSource()))
            .csrf(c -> c
                    .ignoringRequestMatchers("/api/**", "/h2-console/**")
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .headers(h -> h.frameOptions(f -> f.sameOrigin())) // h2-console
            .sessionManagement(s -> s
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(1))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/login", "/api/login", "/api/me", "/error").permitAll()
                    .requestMatchers("/api/admin/**").hasAnyRole("SYS_ADMIN", "ADMIN")
                    .anyRequest().authenticated())
            .formLogin(f -> f
                    .loginProcessingUrl("/api/login")
                    .successHandler((req, res, auth) -> {
                        res.setStatus(HttpStatus.OK.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write("{\"username\":\""
                                + auth.getName() + "\"}");
                    })
                    .failureHandler((req, res, ex) -> {
                        res.setStatus(HttpStatus.UNAUTHORIZED.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write("{\"error\":\"bad_credentials\"}");
                    }))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .logout(l -> l
                    .logoutUrl("/api/logout")
                    .deleteCookies("JSESSIONID")
                    .invalidateHttpSession(true)
                    .logoutSuccessHandler((req, res, auth) -> {
                        res.setStatus(HttpStatus.OK.value());
                    }))
            .httpBasic(AbstractHttpConfigurer::disable)
            .userDetailsService(userDetailsService);
        return http.build();
    }
}
