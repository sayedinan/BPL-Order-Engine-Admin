package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.user.User;
import com.BPL_Order_Engine_Admin.manager.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * v0.3 JWT auth filter. Two passes (per the task-decomposition skill):
 * <ol>
 *   <li>15.8 — extract the token, parse + verify (no SecurityContext population yet).</li>
 *   <li>15.9 — load the {@code User}, build a {@code UserPrincipal}, populate
 *       the {@code SecurityContextHolder}.</li>
 * </ol>
 *
 * <p>Behavior:
 * <ul>
 *   <li>No {@code Authorization} header → continue (Spring Security's
 *       downstream rules decide whether the path is public).</li>
 *   <li>Malformed/expired/unsigned token → log + continue (the downstream
 *       rules will return 401; we don't write a body here).</li>
 *   <li>Valid token + known user → populate SecurityContext and continue.</li>
 *   <li>Valid token + unknown user → log + continue (treat as unauthenticated).</li>
 * </ul>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // Pass 1: parse + verify. Any throw → malformed/expired/unsigned.
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            chain.doFilter(request, response);
            return;
        }

        // Pass 2: load the user, build the principal, populate the
        // SecurityContext. We require the user to still exist in the
        // DB; a valid token for a deleted user is treated as anonymous.
        String username = claims.getSubject();
        if (username == null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            log.debug("JWT subject '{}' not found in DB", username);
            chain.doFilter(request, response);
            return;
        }

        UserPrincipal principal = new UserPrincipal(userOpt.get());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
