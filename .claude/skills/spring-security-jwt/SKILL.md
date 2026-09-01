---
name: spring-security-jwt
description: v0.3 — how SecurityConfig, JwtAuthFilter, JwtService, and UserPrincipal fit together. The parse-vs-authenticate split, BCrypt password hashing, role hierarchy, and the env-var-driven secret config.
---

# Spring Security + JWT (v0.3)

v0.3 replaces v0.2's HTTP Basic auth with a JWT-based flow. The
mechanism is a `OncePerRequestFilter` that runs before Spring's
authorization filter, plus a JPA-backed `UserDetailsService`.

## Components

| Component | Where | Responsibility |
|---|---|---|
| `SecurityConfig` | `manager.config` | Filter chain, JPA `UserDetailsService` bean, `@EnableMethodSecurity`, role hierarchy, CORS |
| `JwtService` | `manager.auth` | Sign and validate JWTs; reads `app.jwt.secret` from env |
| `JwtAuthFilter` | `manager.auth` | Extract `Authorization: Bearer …`; **parse** then **authenticate** (two distinct passes — see below) |
| `UserPrincipal` | `manager.auth` | `UserDetails` wrapping the `User` entity |
| `UserRepository` | `manager.user` | `findByUsername(String)` for the JPA `UserDetailsService` |
| `AuthenticationSuccessListener` | `manager.audit` | Writes `LOGIN_SUCCESS` row |
| `AuthenticationFailureListener` | `manager.audit` | Writes `LOGIN_FAIL` row (with the attempted username, not from `SecurityContext`) |
| `BCryptPasswordEncoder` | bean in `SecurityConfig` | Strength 10 (default); used in `UserService` for both `create` and `change-password` |

## The parse-vs-authenticate split (🔒)

Per the `task-decomposition` skill, the JWT filter's *parse* step
(extracting the token and validating its signature/expiry) and its
*authenticate* step (populating `SecurityContextHolder` with a
`UserPrincipal`) are **two separate subtasks** when implementing.
They live in the same file but are not written together. The seam
between them is where the highest-leverage bugs live (a parse-success
with a populate-fail leaves the request anonymous; a populate-success
on a stale token impersonates a logged-out user).

```java
// First subtask: parse only. No SecurityContext population.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }
        String token = header.substring(7);
        try {
            JwtClaims claims = jwtService.parse(token);   // throws on bad/expired
            // (no SecurityContext work yet — that's the second subtask)
            request.setAttribute("jwt.claims", claims);   // stash for the next pass
        } catch (JwtException e) {
            // malformed or expired; let the request proceed anonymously
            // the @PreAuthorize on the controller will reject it with 401/403
        }
        chain.doFilter(req, res);
    }
}

// Second subtask: authenticate, populating SecurityContext.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    // ... same as above, but the parse branch now:
    JwtClaims claims = (JwtClaims) request.getAttribute("jwt.claims");
    if (claims != null) {
        UserPrincipal principal = userRepository.findByUsername(claims.sub())
            .map(UserPrincipal::new)
            .orElse(null);
        if (principal != null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }
    // ...
}
```

The first pass is verifiable on its own: a malformed token produces an anonymous request, and the `@PreAuthorize` on the controller handles the 401. The second pass turns an authenticated request into a `SecurityContext` that downstream code (controllers, `@Audited` aspect) can read.

## SecurityConfig shape

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository repo) {
        return username -> repo.findByUsername(username)
            .map(UserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl rh = new RoleHierarchyImpl();
        rh.setHierarchy("ROLE_SYS_ADMIN > ROLE_ADMIN\nROLE_ADMIN > ROLE_USER");
        return rh;
    }
}
```

The role hierarchy lets you write `@PreAuthorize("hasRole('ADMIN')")` and have it also accept `SYS_ADMIN`. **Do not skip this** — every controller method relies on it.

## JWT claims

```java
record JwtClaims(String sub, String role, boolean mustChangePassword) {}
```

- `sub`: the username. The `User` is looked up by this on each request; an old token whose user has been deleted becomes a 401 (because the `UserRepository.findByUsername` returns empty).
- `role`: the `RoleType` name. Used by the role hierarchy.
- `mustChangePassword`: read from the `User` at sign time. The `User` is re-fetched on every request, so a user who has just changed their password will get a new JWT issued by the change-password endpoint with `mustChangePassword = false`.

Signing: HS256 with `app.jwt.secret` (≥ 256 bits). TTL: 8 hours.

## Env vars

| Var | Purpose | Required? |
|---|---|---|
| `JWT_SECRET` | HS256 secret | Yes; missing → app fails to start |
| `JASYPT_ENCRYPTOR_PASSWORD` | For `Engine.serverPassword` | Yes; missing → app fails to start |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres | Yes |

None of these have defaults. The app refuses to start if any is missing, with a clear log line that names the missing var.

## Anti-patterns

- **Don't use HTTP Basic.** v0.2 had it; v0.3 is JWT only.
- **Don't store the JWT in a server-side session.** Stateless — the `SessionCreationPolicy.STATELESS` line is mandatory.
- **Don't use a JWT library that doesn't validate signatures by default.** `jjwt` does, but if you switch libs, verify the behavior.
- **Don't put the role in the JWT without re-fetching the user.** A stale JWT for a user who has been demoted should not grant the old role. The `User` is re-fetched by username on each request; the role hierarchy re-derives the authorities from the live `User.roleType`.
- **Don't log the JWT.** Not in success paths, not in error paths. The `JwtException` catch logs the *class* of the exception, not the token.
- **Don't bundle the parse step with the authenticate step in one subtask.** Per `task-decomposition.md`, they are always two.
- **Don't use `ROLE_` prefixes in `RoleType`.** Spring's `hasRole` adds the `ROLE_` prefix automatically. The enum value is `SYS_ADMIN`, the role authority string is `ROLE_SYS_ADMIN`.
