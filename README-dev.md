# BPL Order Engine Admin — Dev Runner Scripts

Three Windows batch files in the repo root. Run any of them from a
`cmd` or `powershell` window.

| Script | What it does |
|---|---|
| `run.bat` | Bring up Postgres + dev sshd via docker compose, build the backend, start it in a new window, then start the Vite dev server in another new window. |
| `stop.bat` | Kill the backend + frontend windows, then `docker compose down`. |
| `reset.bat` | Same as stop + `docker compose down -v` (drops the DB volume, so the dev seed runs again on the next `run.bat`). |

## What you get after `run.bat`

- **Postgres 16** on `localhost:5432`, db `bpl_admin_dev`, user `bpl_admin` / `bpl_admin`. Data persisted in the `bpl-pgdata` Docker volume.
- **Throwaway sshd** on `localhost:2222` (for `mode=REAL` engine experimentation). Username `bpl`, password `bpl`.
- **Backend** on `http://localhost:8080`, log stream visible in the titled window.
- **Frontend** (Vite dev server) on `http://localhost:5173`. The script writes `frontend/.env.local` with `VITE_USE_MOCK=false` so the Vite dev server talks to the real backend. `stop.bat` removes that file.

## Demo credentials (seeded by `DevDataInitializer` on dev profile)

| Username | Password | Role | Notes |
|---|---|---|---|
| `sysadmin` | `sysadmin123` | SYS_ADMIN | `mustChangePassword = true` on first login |
| `admin` | `admin123` | ADMIN | No must-change; can manage USER-role users only |
| `user1` | `user123` | USER | Assigned to BPL only |
| `user2` | `user123` | USER | Assigned to PCL only |

## Quick test

```cmd
run.bat
```

The script:
1. Starts Postgres + the dev sshd via `docker compose up -d`.
2. Waits for Postgres to be ready.
3. Builds the backend (`gradlew build -x test`).
4. Opens a new window titled **"BPL Backend (port 8080)"** and runs `gradlew bootRun`.
5. Polls `/actuator/health` until the backend is up.
6. Opens a new window titled **"BPL Frontend (port 5173)"** and runs `npm run dev` (after `npm install` on first run).
7. Writes `frontend/.env.local` with `VITE_USE_MOCK=false` so the Vite dev server talks to the real backend.

The browser opens at `http://localhost:5173`. Sign in as `sysadmin / sysadmin123`, change the password, click around.

To shut down:

```cmd
stop.bat
```

This kills the two titled windows, removes `frontend/.env.local`, and runs `docker compose down`. The next `run.bat` is a clean start (DB is preserved).

## Override defaults

`run.bat` reads these env vars (all have dev defaults):

| Var | Default | Purpose |
|---|---|---|
| `DB_USERNAME` | `bpl_admin` | Postgres user |
| `DB_PASSWORD` | `b_admin` | Postgres password |
| `DB_NAME` | `bpl_admin_dev` | Database name |
| `DB_PORT` | `5432` | Host port for Postgres |
| `SSHD_PORT` | `2222` | Host port for the dev sshd |
| `BACKEND_PORT` | `8080` | Spring Boot HTTP port |
| `FRONTEND_PORT` | `5173` | Vite dev server port |
| `JWT_SECRET` | (dev placeholder, 32+ chars) | HMAC key for the JWT filter |
| `JASYPT_ENCRYPTOR_PASSWORD` | (dev placeholder) | Master key for Engine.serverPassword encryption |

Set them in the environment before calling `run.bat`, or modify the
script's defaults. (For prod, set `SPRING_PROFILES_ACTIVE=prod` and
all secrets via env vars; the prod profile has no defaults.)

## Troubleshooting

- **"docker is not on PATH"** — install Docker Desktop, restart your shell.
- **Backend fails to start with `JWT_SECRET must be set ...`** — the env var didn't propagate. The script sets a dev default; if you've overridden it, make sure it's at least 32 chars.
- **Backend says `Connection refused` to Postgres** — `docker compose up -d` failed. Run `docker compose logs postgres` to see the error. Most common cause: another service is using port 5432.
- **Frontend shows a blank page** — the backend isn't up yet. Wait a few seconds; the page polls `/actuator/health` indirectly. Or open `http://localhost:8080/actuator/health` in a tab to confirm.
- **Need to start completely fresh** — run `reset.bat`. It drops the DB volume and re-seeds.
