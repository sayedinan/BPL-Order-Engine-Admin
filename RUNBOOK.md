# RUNBOOK — BPL Order Engine Admin (production)

This file is the operator's guide for deploying, maintaining, and
troubleshooting a production instance of the v0.3 app. Engineers
hacking on the code use [README-dev.md](README-dev.md) instead.

The v0.3 app is a small Spring Boot + React stack. The production
shape is **one VM** running:

- `postgres` — data on a named Docker volume.
- `bpl-backend` — Spring Boot 4.1.1 on JVM 17.
- `bpl-frontend` — nginx serving the built React SPA.
- `caddy` — public TLS terminator and reverse proxy.

Caddy is the only service that publishes ports (80, 443). The
backend and frontend are reached only over the internal Docker
network.

---

## 0. Prerequisites

- Linux VM with Docker Engine 24+ and Docker Compose v2.
- A DNS A/AAAA record pointing your chosen hostname at the VM's
  public IP.
- Outbound HTTPS to the internet (Caddy fetches a Let's Encrypt
  cert on first start).

**Do not** reuse the dev secrets in [dev-secrets.template](dev-secrets.template)
verbatim for production. Generate fresh values.

---

## 1. First-time deploy

### 1.1 Clone and prep

```bash
git clone <repo-url> /opt/bpl-order-engine-admin
cd /opt/bpl-order-engine-admin
cp dev-secrets.template .env.local
chmod 600 .env.local
```

### 1.2 Fill in the secrets

Edit `.env.local`. The required variables are documented in the
template, but for a fresh production install:

```bash
# Database (used by docker-compose.prod.yml and the backend)
DB_URL=jdbc:postgresql://postgres:5432/bpl_order_engine
DB_USERNAME=bpl_app
DB_PASSWORD=<openssl rand -base64 32>
POSTGRES_DB=bpl_order_engine
POSTGRES_USER=bpl_app
# POSTGRES_PASSWORD must equal DB_PASSWORD.

# JWT signing
JWT_SECRET=<openssl rand -base64 48>

# Jasypt master password (for Engine.serverPassword at-rest)
JASYPT_ENCRYPTOR_PASSWORD=<openssl rand -base64 32>

# Public URL the operator chose (no trailing slash)
PUBLIC_URL=https://bpl-admin.example.com

# CORS — must match PUBLIC_URL exactly (scheme + host, no path)
CORS_ALLOWED_ORIGINS=https://bpl-admin.example.com

# Spring profile
SPRING_PROFILES_ACTIVE=prod
```

The backend's [RequiredEnvValidator](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/config/RequiredEnvValidator.java)
will refuse to start if any of the required variables is missing
or blank. The error message lists every missing var at once.

### 1.3 Update the Caddyfile

Edit [Caddyfile](Caddyfile). Replace the `bpl-admin.example.com`
placeholder (the default after `{$CADDY_HOSTNAME:...}`) with the
same hostname as `PUBLIC_URL` and `CORS_ALLOWED_ORIGINS`.

### 1.4 Bring the stack up

```bash
docker compose -f docker-compose.prod.yml --env-file .env.local \
  up -d --build
```

The first build takes a few minutes (Gradle download, npm install,
both image layers). Watch the backend boot:

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

A successful boot ends with:

```
Started BplOrderEngineAdminBackendApplication in N.N seconds
```

If the app exits within a few seconds, see [Troubleshooting](#7-troubleshooting).

### 1.5 Bootstrap the first admin

The dev seed users (`sysadmin` / `sysadmin123`, …) are loaded only
in the `dev` profile. A fresh prod DB has zero users. Create the
first SYS_ADMIN directly in Postgres. The BCrypt strength is 10
(matches the backend's `BCryptPasswordEncoder(10)`).

```bash
# Generate a BCrypt hash for the password you'll type at login.
# Requires the apache2-utils package (Debian/Ubuntu) or
# httpd-tools (RHEL/CentOS). One-liner:
HASH=$(htpasswd -bnBC 10 "" 'ChooseAStrongPassword!' | tr -d ':\n')
echo "$HASH"
# The hash starts with $2y$ and is 60 chars long. If you ever
# echo it to a log, redact the original password.

# Insert the admin row.
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U "$DB_USERNAME" -d "$POSTGRES_DB" -c \
  "INSERT INTO users (id, version, username, password_hash, role_type, must_change_password, created_at, updated_at)
   VALUES (gen_random_uuid(), 0, 'firstadmin', '$HASH', 'SYS_ADMIN', true, now(), now())"
```

Log in at `https://<your-hostname>/` as `firstadmin` with the
password you hashed. The `must_change_password` flag forces a
change on first login. After that, use the Admin Panel to create
the rest of the team.

---

## 2. Day-to-day

### 2.1 Status

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 backend
docker compose -f docker-compose.prod.yml logs --tail=200 caddy
```

### 2.2 Logs

Caddy writes access logs to stdout. The backend writes to stdout
(the default Spring Boot layout). Ship them off the VM with your
usual log forwarder (journald, fluentbit, vector, etc.).

### 2.3 Health

The reverse proxy exposes two health endpoints on the public
hostname (Caddy does not strip them by default — fine for our
threat model since they leak no secret):

- `GET /actuator/health` — Spring Boot's overall liveness/readiness
  signal. Returns 200 with `{"status":"UP"}` when the app is ready.
- `GET /healthz` — the frontend nginx's tiny health endpoint.

Caddy itself does not need its own health check.

---

## 3. Backups

The only stateful data is Postgres. The audit log, users, and
engine rows all live there.

### 3.1 Take a backup

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$DB_USERNAME" -d "$POSTGRES_DB" \
  | gzip > "backup-$(date +%F).sql.gz"
```

Copy the file off the VM. `bpl-pgdata-prod` is on a Docker volume
and is **not** a backup by itself — if the VM is lost, the volume
is lost.

### 3.2 Restore

```bash
gunzip -c backup-2026-09-02.sql.gz | docker compose -f docker-compose.prod.yml \
  exec -T postgres psql -U "$DB_USERNAME" -d "$POSTGRES_DB"
```

If you're restoring to a fresh database (e.g. disaster recovery),
run the schema migration first by starting the backend once with
an empty DB; Flyway will create the tables before psql tries to
load data.

---

## 4. Upgrades

```bash
cd /opt/bpl-order-engine-admin
git pull
# Back up the database first (see §3.1).
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
```

Caddy reloads its config without dropping in-flight connections
(Compose's `restart` on a config change is enough). The frontend
and backend containers restart sequentially; the frontend
container's `/healthz` and the backend's `/actuator/health` will
go through a few seconds of `unhealthy` during the rolling swap.

**Schema migrations**: Flyway runs at backend startup. New columns
or tables are added automatically. Flyway will refuse to start if
a destructive change is detected; in that case, follow the
release notes (not bundled in this runbook) and apply the
required manual step before bringing the stack back up.

**Rotating `JWT_SECRET` invalidates every live session.** Don't
change it on a whim; doing so logs every user out instantly. The
backend will still start; the next request from any browser will
return 401, the client will redirect to `/login`.

**Rotating `JASYPT_ENCRYPTOR_PASSWORD` is destructive.** The
Jasypt-encrypted `engines.server_password` column is encrypted
with the old master. After rotation, every existing engine's
`serverPassword` is unreadable. Reset each engine's SSH password
via the Admin Panel after a master-password rotation.

---

## 5. Adding/removing users

Use the Admin Panel in the UI as a SYS_ADMIN. There is no CLI for
user management.

If the only SYS_ADMIN's account is locked out (e.g. forgotten
password after a `mustChangePassword=true` rotation), use the same
`psql` insert/update as the bootstrap, with a fresh BCrypt hash.
There is no self-service password reset in v0.3 (deferred to v0.4
per SPEC §9).

---

## 6. Adding/removing engines

Same: use the Admin Panel. Engine creation requires a SYS_ADMIN
role. SSH passwords are encrypted at rest with the Jasypt master
password; they are never returned in API responses.

`mode=REAL` engines require a reachable SSH target. The shipped
app has no default engine pointing at any production address —
adding one is a deployment decision documented in [SPEC.md §10](SPEC.md#L966).

---

## 7. Troubleshooting

### 7.1 "Started BplOrderEngineAdminBackendApplication" never appears

The backend failed to start. Check:

- `docker compose -f docker-compose.prod.yml logs backend | head -50`
- Look for `Required environment variables missing`. If you see
  that, the [RequiredEnvValidator](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/config/RequiredEnvValidator.java)
  did its job. Add the missing variable to `.env.local` and
  restart.
- Look for `FlywayException`. The schema migration failed. The
  most common cause is a manual DB edit that left a row in a
  state Flyway doesn't expect. Run `flyway info` against the DB
  to see which migration is in `Pending` vs `Failed`.
- Look for `java.net.ConnectException: Connection refused` to
  `postgres:5432`. The backend is up before Postgres is ready. The
  compose file's `depends_on: condition: service_healthy` should
  prevent this; if you see it, the Postgres healthcheck is broken.

### 7.2 401 loops on every page

- Check that `JWT_SECRET` was set to the **same value** as on the
  prior deploy. If you changed it, every live token signed with
  the old secret is now invalid; the client redirects to
  `/login`, gets a new token, and the cycle resets. This is
  expected if you intended to rotate.
- Check the browser's `localStorage.bpl-admin.token` — if it's
  there but the server still 401s, the JWT is malformed; clear
  the storage and log in again.

### 7.3 "Access denied" on every page after a successful login

CORS is misconfigured. The browser sent the JWT and the server
rejected the *origin*. Check:

- `CORS_ALLOWED_ORIGINS` matches the public hostname **exactly**:
  same scheme, no trailing slash, no path. E.g. `https://bpl-admin.example.com`.
- The browser's address bar shows the same URL the variable
  contains.

If they don't match, fix the variable and restart the backend.

### 7.4 WebSocket connects but no log lines stream

- The frontend builds with `VITE_API_BASE_URL` baked in. If that
  value doesn't match the public URL the user is on, the WS path
  resolves to the wrong host. Check the rebuilt frontend image's
  build args. (The bundle is public; `grep` for the hostname in
  the dist's `index-*.js` to confirm.)
- Caddy's `reverse_proxy` passes `Upgrade` and `Connection`
  headers by default. If you've customized Caddy, make sure those
  headers aren't being stripped.

### 7.5 Backend healthcheck flapping

The 30-second probe runs `wget -qO- /actuator/health`. The first
call after a cold start takes a few seconds while JPA warms up.
If the flap is persistent, look at `management.endpoint.health`
output — a failing `db` indicator means the JDBC connection is
broken.

### 7.6 "No space left on device"

Docker volumes live under `/var/lib/docker`. Check `df -h /var/lib/docker`
and the volume sizes with `docker system df -v`. Old images and
dangling build cache are the usual culprits:

```bash
docker image prune -f
docker builder prune -f
```

Keep at least the most recent 2–3 backend/frontend images so you
can roll back.

---

## 8. File map

| File | Role |
|---|---|
| `docker-compose.prod.yml` | The production stack. |
| `Caddyfile` | The public reverse proxy. |
| `backend/Dockerfile` | Multi-stage build of the Spring Boot image. |
| `frontend/Dockerfile` | Multi-stage build of the React SPA + nginx. |
| `frontend/nginx.conf` | Internal nginx config inside the frontend image. |
| `frontend/vite.production-env.example` | Build-time env shape (committed example only). |
| `dev-secrets.template` | The starting point for `.env.local`. |
| `backend/src/main/resources/application-prod.properties` | The `prod` profile. |
| `backend/src/main/java/.../RequiredEnvValidator.java` | Fail-fast env-var check. |
| `SPEC.md` | The design contract. |
| `README-dev.md` | The developer's guide. |
