# Deployment status — v0.3 production

A snapshot of where the v0.3 codebase stands on being deployable to a
single-VM production host. For the **how to deploy**, see
[RUNBOOK.md](RUNBOOK.md). This file is a status board, not a procedure.

Last updated: 2026-09-02.

## TL;DR

The app is **deployable as-is** on a Linux VM with Docker Engine 24+ and
a real domain. All required artifacts are in the repo. The remaining work
is operational (provision a VM, point DNS, fill `.env.local`, run
`docker compose -f docker-compose.prod.yml up -d --build`), not code
work.

## Status board

| # | Capability | Status | Where it lives |
|---|---|---|---|
| 1 | Backend image (multi-stage, non-root, healthcheck) | Shipped | [backend/Dockerfile](backend/Dockerfile), [backend/.dockerignore](backend/.dockerignore) |
| 2 | Frontend image (multi-stage, nginx-served SPA) | Shipped | [frontend/Dockerfile](frontend/Dockerfile), [frontend/.dockerignore](frontend/.dockerignore), [frontend/nginx.conf](frontend/nginx.conf) |
| 3 | Production compose stack (postgres + backend + frontend + caddy) | Shipped | [docker-compose.prod.yml](docker-compose.prod.yml) |
| 4 | TLS-terminating reverse proxy (Caddy, auto-LE) | Shipped | [Caddyfile](Caddyfile) |
| 5 | Fail-fast env-var validator (fires on prod or on prod-shaped env without a profile) | Shipped | [RequiredEnvValidator.java](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/config/RequiredEnvValidator.java) |
| 6 | Prod-only Spring profile (port, actuator lockdown, OSIV off, logging) | Shipped | [application-prod.properties](backend/src/main/resources/application-prod.properties) |
| 7 | WebSocket origin driven by the same `app.cors.allowed-origins` as `CorsConfig` (no hard-coded `localhost:5173`) | Shipped | [WebSocketConfig.java](backend/src/main/java/com/BPL_Order_Engine_Admin/manager/engine/ws/WebSocketConfig.java) |
| 8 | Operator runbook (first deploy, upgrade, backup, restore, troubleshooting) | Shipped | [RUNBOOK.md](RUNBOOK.md) |
| 9 | Dev ↔ prod split (dev compose for `run.bat`, prod compose for VM) | Shipped | [docker-compose.yml](docker-compose.yml) (dev) + [docker-compose.prod.yml](docker-compose.prod.yml) (prod) |
| 10 | First-admin bootstrap procedure (no auto-seed in prod) | Shipped | [RUNBOOK.md §1.5](RUNBOOK.md) |

## Out of scope (per SPEC §9 and the original plan)

- Kubernetes manifests. Single VM only.
- SSO, MFA, rate limiting, audit export, password reset, account disable.
- Any change to auth, audit, engine, or user business logic.

## What changed since this plan was first drafted

The original plan (now archived above the dashed line) described nine
deliverables as if none of them existed. They were all implemented in
the `tried to bug fix` (e675cd7) commit cycle and the working-tree
changes that followed. The items below are the live deltas vs. the
original plan, in addition to the code work:

- **`RequiredEnvValidator` was tightened** (this commit) so it also
  fires when no Spring profile is active *and* at least one required
  prod env var is set. The original validator only fired on the
  active `prod` profile, which silently let through a common
  misconfiguration (prod image deployed without
  `SPRING_PROFILES_ACTIVE=prod`).
- **The runbook was updated** to match the reality that the
  frontend Dockerfile takes `VITE_API_BASE_URL` and `VITE_USE_MOCK` as
  build args directly, rather than via a `frontend/.env.production`
  file. There is no committed `frontend/.env.production`; the
  defaults are baked into the Dockerfile.
- **This file replaces the original plan** as a one-page status
  snapshot. The original task list is preserved above for historical
  context but is no longer the source of truth for what to do.

## Verification — what is unrun

The original plan's verification list (sections 1–8) has not been
exercised end-to-end against a real VM. The artifacts compile and the
config files cross-reference correctly, but the smoke checks below
have **not** been run by anyone in this repo and remain the operator's
responsibility on first deploy:

1. `docker compose -f docker-compose.prod.yml up -d --build` boots
   clean; backend log shows `Started BplOrderEngineAdminBackendApplication`.
2. `curl -fsS http://localhost/actuator/health` returns
   `{"status":"UP"}` (Caddy terminates :80 in plain-HTTP mode during
   the smoke test; flip to the real hostname for ACME).
3. Login + `/api/auth/me` with a BCrypt-seeded first admin.
4. WebSocket upgrade through Caddy streams engine logs.
5. Backend restart preserves users and audit log.
6. `pg_dump` / `psql` round-trip restores cleanly.
7. Removing `JWT_SECRET` from `.env.local` and restarting fails
   fast with the validator's "Required environment variables
   missing" message.

## Pointers

- **Deploying now?** Start at [RUNBOOK.md](RUNBOOK.md) §1.
- **Editing the deploy?** All deployable config is in:
  [docker-compose.prod.yml](docker-compose.prod.yml),
  [Caddyfile](Caddyfile), [backend/Dockerfile](backend/Dockerfile),
  [frontend/Dockerfile](frontend/Dockerfile),
  [frontend/nginx.conf](frontend/nginx.conf),
  [backend/src/main/resources/application-prod.properties](backend/src/main/resources/application-prod.properties).
- **Why is a thing the way it is?** [SPEC.md](SPEC.md) is the design
  contract; [API.md](API.md) is the wire contract; this file is the
  deploy status.
