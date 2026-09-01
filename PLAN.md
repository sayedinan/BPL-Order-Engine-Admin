# Plan: Make the v0.3 BPL Order Engine Admin deployable

## Context

The repo is a full v0.3 implementation (Spring Boot 4.1.1 backend, React 19 / Vite 8 frontend) that already demos end-to-end via the in-browser mock or the `run.bat` Docker Compose dev stack. Nothing in it is shaped for production: there's no Dockerfile, the WebSocket config hard-codes `http://localhost:5173` as the only allowed origin ([WebSocketConfig.java:29](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/engine/ws/WebSocketConfig.java#L29)), there is no production-only application config beyond a thin `application-prod.properties`, no reverse-proxy config, no deploy/upgrade/rollback script, and no first-boot guidance for the operator beyond the dev seed in `DevDataInitializer`.

Target shape (per the user): **single VM, app + Postgres on that VM, reverse proxy (nginx or Caddy) terminating TLS in front.** Everything below is sized to that.

The SPEC already has the right contracts — [SPEC.md §8](SPEC.md#L895) (env-var list, prod profile) and [SPEC.md §10](SPEC.md#L966) (live BPL container runbook). The work here is making the codebase match those contracts and producing the deploy artifacts that turn the dev stack into a production stack.

## Out of scope (explicit)

- Kubernetes manifests. Single VM only.
- Multi-tenancy, SSO, rate limiting, audit export, password reset, account disable. All deferred per [SPEC.md §9](SPEC.md#L949).
- Any code change to the auth/audit/engine domains. The prod-readiness work is at the deploy edge, not in business logic.

## Hard constraint

The secrets guard `.claude/hooks/block-plaintext-secrets.sh` runs on `Write|Edit` per [settings.json:65-74](.claude/settings.json#L65-L74). All deploy files must be secret-free: no `password=...`, no JWTs, no PEM keys, no `bpl_admin`/`bpl` literals in committed text. Use `${VAR}` placeholders, `openssl rand -base64 48`-style generation instructions in comments, and the `dev-secrets.template` pattern that's already in the repo ([dev-secrets.template](dev-secrets.template)).

## Changes

### 1. Bug found while reading the code: WebSocketConfig hard-coded origin

`backend/src/main/java/com/BPL_Order_Engine_Admin/manager/engine/ws/WebSocketConfig.java:29` sets `setAllowedOriginPatterns("http://localhost:5173")` for the WS handshake. In production this rejects the real frontend origin. Fix:

- Read the same `${app.cors.allowed-origins}` property `CorsConfig` already uses.
- Split the comma-separated list, pass each as a pattern (the same way `CorsConfig` does at [CorsConfig.java:27-32](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/config/CorsConfig.java#L27-L32)).

One-file change. Required before any production deploy works.

### 2. Tighten `application-prod.properties`

Current content ([application-prod.properties](backend/src/main/resources/application-prod.properties)) is three lines. Add:

- `server.port=${SERVER_PORT:8080}` — so the reverse-proxy forward can target a known port.
- `management.endpoints.web.exposure.include=health,info` — only the safe ones. Currently `run.bat` already polls `/actuator/health`, confirming the dependency is on the classpath via `spring-boot-starter-actuator` (it is, otherwise `/actuator/health` would 404). Lock down everything else.
- `management.endpoint.health.probes.enabled=true` and `management.health.livenessstate.enabled=true` / `readinessstate.enabled=true` — the K8s-style probes are also useful for a plain VM (`docker compose ps` healthcheck, or a `curl` loop). Cheap insurance.
- `logging.level.root=INFO` and `logging.level.com.BPL_Order_Engine_Admin=INFO` — explicit; default is INFO but pinning it stops dev's `application-dev.properties` from leaking.
- `spring.jpa.open-in-view=false` — the v0.3 default would open a Hibernate session per request for the duration of view rendering; turning this off is a known Spring Boot hardening and is a one-line win. The codebase already returns DTOs, not entities, from controllers, so this won't break anything.

### 3. `application-prod.properties` already requires `${CORS_ALLOWED_ORIGINS}` — verify fail-fast

[application-prod.properties:5](backend/src/main/resources/application-prod.properties#L5) has `app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}` with no default. If the operator forgets to set it, the app starts with an empty allowlist and every browser request 403s. The SPEC [SPEC.md:939](SPEC.md#L939) says "Missing `JWT_SECRET` or `JASYPT_ENCRYPTOR_PASSWORD` → app fails to start with a clear error." Add `JWT_SECRET` and `JASYPT_ENCRYPTOR_PASSWORD` to that list. Two ways to do it; pick the second:

- (rejected) Document-only. Easy to miss.
- (chosen) Add a `@ConfigurationProperties` "required env" bean or a small `EnvironmentPostProcessor` that throws on missing required vars. One new file in `manager/config/`, ~30 lines, runs before any bean is created, throws an `IllegalStateException` listing all missing vars at once (not just the first). Pattern: implement `EnvironmentPostProcessor`, accept a list of `app.required-env-vars[]` and verify each is non-null in the `Environment`. This is the same pattern Spring Boot's own `@Value("${...}")` produces, but it surfaces *all* missing vars in one boot log line.

The list to enforce: `JWT_SECRET`, `JASYPT_ENCRYPTOR_PASSWORD`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`. The first two are SPEC-mandated ([SPEC.md:939](SPEC.md#L939)); the other four are real deploy blockers (no DB → app crashes; no CORS → silent 403s).

### 4. `frontend/.env.production` and `vite.config.ts`

Add a `frontend/.env.production` (committed, no secrets) with `VITE_USE_MOCK=false` and a placeholder `VITE_API_BASE_URL` for the build-time default. Real prod sets `VITE_API_BASE_URL` at build time via the CI/operator.

In `vite.config.ts` ([vite.config.ts](frontend/vite.config.ts)), add a `build` block: `outDir: 'dist'`, `sourcemap: false` (so prod bundles don't leak the React source), `target: 'es2022'`. Also a `server.proxy` block isn't needed in prod — that was for the dev server.

Update the `dev` script in `package.json` to also accept `VITE_API_BASE_URL` from the environment. (Already works via Vite's default env handling; no change needed unless we want to be explicit.)

### 5. Production Dockerfiles

Two multi-stage Dockerfiles, both rooted in the repo root so the existing dev `docker-compose.yml` and prod `docker-compose.yml` can both be run from the same place.

**`backend/Dockerfile`**:
- Build stage: `eclipse-temurin:17-jdk-jammy` (or `21` if the toolchain in `build.gradle` ever moves). `COPY . /src`, `WORKDIR /src`, run `./gradlew bootJar -x test --no-daemon`. Result: `build/libs/BPL-Order-Engine-Admin-backend-*.jar`.
- Run stage: `eclipse-temurin:17-jre-jammy`. `USER 1000` (non-root). `EXPOSE 8080`. `HEALTHCHECK` hits `http://localhost:8080/actuator/health`. `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.
- Build-time hint for the secrets guard: do NOT bake secrets as `ENV` in the Dockerfile. They're injected at runtime by Compose from `.env.local` (or whatever the operator uses).

**`frontend/Dockerfile`**:
- Build stage: `node:20-alpine` (matches the dev stack's `node` prereq in [run.bat:57-60](run.bat#L57-L60)). `WORKDIR /app`, `COPY package*.json .`, `npm ci`, `COPY . .`, `ARG VITE_API_BASE_URL`, `ENV VITE_API_BASE_URL=$VITE_API_BASE_URL`, `ARG VITE_USE_MOCK=false`, `ENV VITE_USE_MOCK=$VITE_USE_MOCK`, `RUN npm run build`. Result: `/app/dist/`.
- Run stage: `nginx:1.27-alpine`. `COPY --from=build /app/dist /usr/share/nginx/html`. `COPY nginx.conf /etc/nginx/conf.d/default.conf`. `EXPOSE 80`.

**`frontend/nginx.conf`** (new, ~30 lines):
- SPA fallback: `try_files $uri $uri/ /index.html;` (so React Router handles `/dashboard`, `/logs`, etc.).
- `location /assets/` cache-control `immutable, max-age=31536000` (Vite already fingerprints the assets).
- No CORS headers here — the reverse proxy does TLS termination and proxies to the backend, so the browser sees a same-origin request and CORS doesn't apply. This is the key reason the reverse proxy is in the picture.
- No `/api` proxy block in this nginx; this nginx is the *frontend*'s static-file server, not the public-facing edge.

**`.dockerignore` files**: `backend/.dockerignore` excludes `build/`, `.gradle/`, `*.md` (not needed in the image). `frontend/.dockerignore` excludes `node_modules/`, `dist/`, `.vite/`.

### 6. Production `docker-compose.yml`

A new file, `docker-compose.prod.yml` (separate from the existing dev one — the dev one stays for `run.bat`).

Services:
- `postgres`: same image as dev ([docker-compose.yml:17](docker-compose.yml#L17)) but with: `restart: unless-stopped`, `volumes: [bpl-pgdata-prod:/var/lib/postgresql/data]` (separate volume name so a `docker compose down -v` on dev doesn't nuke prod), a `healthcheck` already in dev, env sourced from `.env.local` via `env_file`.
- `backend`: `build: ./backend` (uses the new Dockerfile), `restart: unless-stopped`, `depends_on: postgres: condition: service_healthy`, `env_file: .env.local`, `ports: []` (not published — only the reverse proxy reaches it). Optional: read-only `/tmp` mount for the SSH tailer scratch space if Apache MINA SSHD writes there.
- `frontend`: `build: ./frontend` (with `args: [VITE_API_BASE_URL=http://localhost:8080]` — overridden at build time in CI), `restart: unless-stopped`, `depends_on: [backend]`, `ports: []`.
- `caddy` (or `nginx` — pick **Caddy** because automatic HTTPS via Let's Encrypt is one line and the user picked "single VM, reverse proxy" without specifying): `image: caddy:2-alpine`, `restart: unless-stopped`, `volumes: [./Caddyfile:/etc/caddy/Caddyfile:ro, caddy-data:/data, caddy-config:/config]`, `ports: ["80:80", "443:443"]`. `depends_on: [backend, frontend]`.

**`Caddyfile`** (new, ~25 lines):
- Top-level site block: `bpl-admin.example.com { reverse_proxy frontend:80 }` — all browser traffic to the static SPA.
- `handle /api/*` block inside the same site: `reverse_proxy backend:8080`. Same-origin from the browser's POV, so CORS doesn't apply (and we already removed the dev default of `http://localhost:5173` in step 1).
- `handle /api/engines/*/logs/stream` block: Caddy supports WebSocket upgrade via `reverse_proxy` out of the box. The `Connection: upgrade` / `Upgrade: websocket` headers pass through.
- `encode zstd gzip` for response compression.
- Automatic HTTPS via Caddy's on-demand ACME for the configured hostname.
- Optional: `log` directive pointing to stdout (Caddy's default).

### 7. Operator runbook: `RUNBOOK.md`

A new top-level file modeled on the existing dev-secrets template. Sections:

- **First-time deploy**: clone, `cp dev-secrets.template .env.local`, edit each `change-me` with `openssl rand -base64 48` instructions inline, `cp .env.local backend/.env` if Compose needs it, `docker compose -f docker-compose.prod.yml --env-file .env.local up -d --build`, then `docker compose logs -f backend` until you see "Started BplOrderEngineAdminBackendApplication".
- **First admin**: there is no `V2__seed_admin.sql` (only `V1__init.sql`). The dev seed lives in `DevDataInitializer` ([DevDataInitializer.java:38-39](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/config/DevDataInitializer.java#L38-L39)) and is `@Profile("dev")` only. So a fresh prod DB has zero users. Document the bootstrap: hit the DB directly with `psql` and `INSERT INTO users (id, version, username, password_hash, role_type, must_change_password, created_at, updated_at) VALUES (gen_random_uuid(), 0, 'firstadmin', '<bcrypt hash>', 'SYS_ADMIN', true, now(), now())`. Include the exact one-liner: `htpasswd -bnBC 10 '' 'choose-a-real-password' | tr -d ':\n'`. This is the only secret-touching step in the runbook; the operator runs it once, then logs in and changes the password via the normal flow.
- **Upgrade**: `git pull && docker compose -f docker-compose.prod.yml build && docker compose -f docker-compose.prod.yml up -d`. Backups first (see next).
- **Backup**: `docker compose exec -T postgres pg_dump -U bpl_admin bpl_admin | gzip > backup-$(date +%F).sql.gz` (in the runbook; do not script-and-commit since `bpl_admin` is a dev default — operator must use their prod username).
- **Restore**: `gunzip -c backup-...sql.gz | docker compose exec -T postgres psql -U bpl_admin -d bpl_admin`.
- **Logs**: `docker compose logs -f backend` / `caddy-access`.
- **Troubleshooting**:
  - 401 loops → check `JWT_SECRET` is set and was the same value as the prior deploy (rotating it invalidates all live JWTs, which is desired, but operators sometimes do it by accident).
  - WS connects but no logs → check Caddy is forwarding `Upgrade` headers; v0.3 uses `Authorization: Bearer` in the WS handshake so check that the frontend's `VITE_API_BASE_URL` matches the prod hostname (the WS path is relative to it).
  - "Access denied" on every page after a successful login → CORS misconfiguration. Confirm `CORS_ALLOWED_ORIGINS` matches the prod hostname exactly, including scheme and no trailing slash.

### 8. README updates

Append a "Production deployment" section to [README-dev.md](README-dev.md) pointing to `RUNBOOK.md`. Don't merge dev and prod into one README; the dev one is for engineers hacking on it, the runbook is for operators.

### 9. Backups volume

Add `caddy-data` and `caddy-config` named volumes to the prod compose for Caddy's cert + on-disk state. Postgres already gets `bpl-pgdata-prod`.

## Files to add

- `backend/Dockerfile` (~25 lines)
- `backend/.dockerignore` (~10 lines)
- `frontend/Dockerfile` (~25 lines)
- `frontend/.dockerignore` (~10 lines)
- `frontend/nginx.conf` (~30 lines)
- `frontend/.env.production` (3 lines, no secrets)
- `docker-compose.prod.yml` (~50 lines)
- `Caddyfile` (~25 lines)
- `RUNBOOK.md` (~120 lines)
- `backend/src/main/java/com/BPL_Order_Engine_Admin/manager/config/RequiredEnvValidator.java` (~50 lines, implements `EnvironmentPostProcessor`)

## Files to modify

- [backend/src/main/java/com/BPL_Order_Engine_Admin/manager/engine/ws/WebSocketConfig.java](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/engine/ws/WebSocketConfig.java) — use the same `app.cors.allowed-origins` property as `CorsConfig`.
- [backend/src/main/resources/application-prod.properties](backend/src/main/resources/application-prod.properties) — add `server.port`, `management.*`, `logging.*`, `spring.jpa.open-in-view=false`.
- [frontend/vite.config.ts](frontend/vite.config.ts) — add `build` block.
- [README-dev.md](README-dev.md) — add a one-line "Production: see RUNBOOK.md" section.

## Reused / not changed

- [docker-compose.yml](docker-compose.yml) — stays as-is; it's the dev stack. `run.bat` keeps using it.
- [run.bat](run.bat), [stop.bat](stop.bat), [reset.bat](reset.bat) — stay as-is; dev only.
- [dev-secrets.template](dev-secrets.template) — used as the starting point for `.env.local` in prod too. The template's `SPRING_PROFILES_ACTIVE=dev` line is the only thing the operator changes (to `prod`).
- All business logic — auth, audit, engines, users, JPA, JWT, Jasypt. Unchanged.
- [SPEC.md](SPEC.md) — the contracts it describes are what this plan implements. No SPEC edits needed; one possible addendum is to note "WebSocket allowed-origins is now driven by `CORS_ALLOWED_ORIGINS`" but that's a code comment, not a SPEC change.

## Verification

1. **Local end-to-end prod build.** From the repo root:
   - `cp dev-secrets.template .env.local`, fill the `change-me` values with `openssl rand -base64 48` outputs.
   - Set `SPRING_PROFILES_ACTIVE=prod`, `VITE_USE_MOCK=false`, `VITE_API_BASE_URL=http://localhost` in `.env.local`.
   - `docker compose -f docker-compose.prod.yml --env-file .env.local up -d --build`.
   - `docker compose -f docker-compose.prod.yml logs backend` — should show "Started BplOrderEngineAdminBackendApplication" and no Flyway errors.
   - `curl -fsS http://localhost/actuator/health` (Caddy is on :80 in dev mode without a hostname) — expect `{"status":"UP"}`.
   - `curl -fsS -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"username":"firstadmin","password":"...the one you set..."}'` — expect 200 with a JWT.
   - With the JWT, `curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost/api/auth/me` — expect 200 with the user object.
   - The prod DB has no engines by default; create one via the UI (SYS_ADMIN role required), then `curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost/api/engines` — expect a list with that engine.
2. **TLS path.** Point a real domain at the VM, change Caddy's site block to that domain, `docker compose -f docker-compose.prod.yml restart caddy`. Caddy issues a cert; `curl -I https://your.domain` returns 200 with a Let's Encrypt issuer chain.
3. **WebSocket through the proxy.** Open the browser DevTools, sign in, click [View Logs] on a MOCK engine. DevTools shows the WS connection upgrading through Caddy to the backend. Lines stream in.
4. **Restart resilience.** `docker compose -f docker-compose.prod.yml restart backend` — the WS reconnects automatically (the client has exponential-backoff reconnection per the [useEngineLogsSocket contract](frontend/src/hooks/useEngineLogsSocket.ts)). Audit log + user list survive.
5. **Backup/restore.** `docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U ... ... > backup.sql`, then drop the volume, bring it back up, `cat backup.sql | docker compose -f docker-compose.prod.yml exec -T postgres psql -U ... -d ...`. Sign in still works; audit log present.
6. **Required-env-validator smoke.** Remove `JWT_SECRET` from `.env.local`, `docker compose -f docker-compose.prod.yml up backend`. Expect: container exits within a few seconds, logs show `IllegalStateException: Required environment variables missing: JWT_SECRET`. Restore `JWT_SECRET` and confirm a clean boot.
7. **WebSocketConfig regression.** A quick `grep` — only the new code path should mention `app.cors.allowed-origins`. The hard-coded `http://localhost:5173` string is gone.
8. **Bundle check.** `cd frontend && npm run build` after a clean checkout. `dist/` is generated, no `.map` files, and `grep -r 'localhost:5173' dist/` returns nothing.

## Order of work

1. WebSocketConfig fix (one file, unblocks any real deploy).
2. Required-env-validator (one new file, unblocks the "fail fast" promise in SPEC §8).
3. application-prod.properties additions.
4. frontend/.env.production and vite.config.ts build block.
5. Dockerfiles and nginx.conf.
6. docker-compose.prod.yml and Caddyfile.
7. RUNBOOK.md.
8. README-dev.md cross-link.
9. Run the full Verification suite end to end.
