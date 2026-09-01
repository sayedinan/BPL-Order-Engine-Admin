@echo off
REM ===================================================================
REM  BPL Order Engine Admin - dev runner
REM
REM  Starts the local dev stack (Postgres + throwaway sshd) via
REM  docker compose, builds the v0.3 backend, runs it in a new
REM  window, then starts the Vite dev server in this window.
REM
REM  Defaults (overridable via env vars or command-line args):
REM    DB_USERNAME = bpl_admin
REM    DB_PASSWORD = bpl_admin
REM    DB_NAME     = bpl_admin_dev
REM    DB_PORT     = 5432
REM    SSHD_PORT   = 2222
REM    BACKEND_PORT = 8080
REM    FRONTEND_PORT = 5173
REM
REM  Stop everything:  stop.bat
REM ===================================================================

setlocal EnableDelayedExpansion

REM ---- Defaults (override via env var before calling) ----
if not defined DB_USERNAME   set DB_USERNAME=bpl_admin
if not defined DB_PASSWORD   set DB_PASSWORD=bpl_admin
if not defined DB_NAME       set DB_NAME=bpl_admin_dev
if not defined DB_PORT       set DB_PORT=5432
if not defined SSHD_PORT     set SSHD_PORT=2222
if not defined BACKEND_PORT  set BACKEND_PORT=8080
if not defined FRONTEND_PORT set FRONTEND_PORT=5173

REM ---- JWT secret (32+ chars; required by JwtService) ----
if not defined JWT_SECRET     set JWT_SECRET=dev-only-jwt-secret-32-chars-long-please

REM ---- Jasypt master password (required for Engine.serverPassword at-rest) ----
if not defined JASYPT_ENCRYPTOR_PASSWORD set JASYPT_ENCRYPTOR_PASSWORD=dev-only-jasypt-master

echo.
echo === BPL Order Engine Admin - dev runner ===
echo DB         : %DB_USERNAME%@localhost:%DB_PORT%/%DB_NAME%
echo sshd       : localhost:%SSHD_PORT%
echo Backend    : http://localhost:%BACKEND_PORT%
echo Frontend   : http://localhost:%FRONTEND_PORT%
echo.

REM ---- Check prereqs ----
where docker >nul 2>&1
if errorlevel 1 (
  echo ERROR: docker is not on PATH. Install Docker Desktop and retry.
  exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: java is not on PATH.
  exit /b 1
)
where node >nul 2>&1
if errorlevel 1 (
  echo ERROR: node is not on PATH.
  exit /b 1
)

REM ---- Bring up Postgres + sshd ----
echo [1/4] Starting Postgres + dev sshd via docker compose...
docker compose up -d
if errorlevel 1 (
  echo ERROR: docker compose failed.
  exit /b 1
)

REM ---- Wait for Postgres to be healthy ----
echo [2/4] Waiting for Postgres at localhost:%DB_PORT%...
set /a attempts=0
:wait_pg
set /a attempts+=1
docker compose exec -T postgres pg_isready -U %DB_USERNAME% -d %DB_NAME% >nul 2>&1
if not errorlevel 1 goto pg_ready
if !attempts! GEQ 30 (
  echo ERROR: Postgres did not become healthy in 30s.
  docker compose logs postgres
  exit /b 1
)
timeout /t 1 /nobreak >nul
goto wait_pg
:pg_ready
echo Postgres is ready.

REM ---- Build the backend (skip tests) ----
echo [3/4] Building backend...
pushd "%~dp0backend"
call gradlew.bat build -x test
if errorlevel 1 (
  echo ERROR: backend build failed.
  popd
  exit /b 1
)
popd

REM ---- Start backend in a new window ----
echo [4/4] Starting backend in a new window...
set DB_URL=jdbc:postgresql://localhost:%DB_PORT%/%DB_NAME%
set CORS_ALLOWED_ORIGINS=http://localhost:%FRONTEND_PORT
start "BPL Backend (port %BACKEND_PORT%)" cmd /k ^
  "set DB_URL=jdbc:postgresql://localhost:%DB_PORT%/%DB_NAME%&& ^
   set DB_USERNAME=%DB_USERNAME%&& ^
   set DB_PASSWORD=%DB_PASSWORD%&& ^
   set JWT_SECRET=%JWT_SECRET%&& ^
   set JASYPT_ENCRYPTOR_PASSWORD=%JASYPT_ENCRYPTOR_PASSWORD%&& ^
   set CORS_ALLOWED_ORIGINS=http://localhost:%FRONTEND_PORT%&& ^
   cd /d %~dp0backend && ^
   gradlew.bat bootRun"

REM ---- Wait for backend health ----
echo Waiting for backend at http://localhost:%BACKEND_PORT%/actuator/health ...
set /a attempts=0
:wait_be
set /a attempts+=1
powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://localhost:%BACKEND_PORT%/actuator/health).StatusCode } catch { 0 }" 1>nul 2>nul
if !errorlevel! EQU 0 goto be_ready
if !attempts! GEQ 60 (
  echo WARN: backend did not respond in 60s. Continuing anyway.
  goto start_fe
)
timeout /t 1 /nobreak >nul
goto wait_be
:be_ready

:start_fe
REM ---- Install frontend deps if needed, then start Vite ----
echo.
echo Starting frontend (Vite) at http://localhost:%FRONTEND_PORT% ...
pushd "%~dp0frontend"
if not exist node_modules (
  echo Installing frontend dependencies (first run)...
  call npm install
  if errorlevel 1 (
    echo ERROR: npm install failed.
    popd
    exit /b 1
  )
)

REM ---- Vite reads .env.local with higher priority than .env.development.
REM   We write it here so the dev server points at the real backend.
echo VITE_USE_MOCK=false>  .env.local
echo VITE_API_BASE_URL=http://localhost:%BACKEND_PORT%>> .env.local
echo (created frontend/.env.local so Vite talks to the real backend)

start "BPL Frontend (port %FRONTEND_PORT%)" cmd /k "npm run dev"

echo.
echo ===========================================================
echo  BOTH services started.
echo.
echo   Backend  : http://localhost:%BACKEND_PORT%
echo   Frontend : http://localhost:%FRONTEND_PORT%
echo.
echo  Sign in with one of:
echo    sysadmin / sysadmin123  (SYS_ADMIN, must change password)
echo    admin    / admin123      (ADMIN)
echo    user1    / user123       (USER, assigned to BPL)
echo    user2    / user123       (USER, assigned to PCL)
echo.
echo  Close the two titled windows (or run stop.bat) to shut down.
echo ===========================================================
echo.
popd
endlocal
