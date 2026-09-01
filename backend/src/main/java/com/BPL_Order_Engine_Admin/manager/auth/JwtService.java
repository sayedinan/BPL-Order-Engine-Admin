package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * v0.3 JWT service (SPEC §4.2, API.md §0.2).
 *
 * <p>Signs and validates tokens using HMAC. The master secret is
 * loaded from {@code JWT_SECRET} (env var) and must be ≥ 256 bits
 * (32+ bytes UTF-8). On startup, if the secret is missing or too
 * short, the app fails to start.
 *
 * <p>Claims issued:
 * <ul>
 *   <li>{@code sub} — username</li>
 *   <li>{@code roles} — array of role names (without the "ROLE_" prefix)</li>
 *   <li>{@code mustChangePassword} — boolean</li>
 *   <li>{@code iss} — "bpl-order-engine-admin"</li>
 *   <li>{@code iat} / {@code exp} — standard</li>
 * </ul>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.ttl:PT8H}") Duration ttl,
            @Value("${app.jwt.issuer:bpl-order-engine-admin}") String issuer) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be set to a string of at least 32 characters (256 bits). "
                + "Set the JWT_SECRET env var before starting the app.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public String sign(String username, RoleType role, boolean mustChangePassword) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(username)
            .claim("roles", List.of(role.name()))
            .claim("mustChangePassword", mustChangePassword)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(signingKey)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Duration ttl() {
        return ttl;
    }
}
