@echo off
REM ===================================================================
REM  BPL Order Engine Admin - shutdown
REM
REM  Kills the backend and frontend dev servers, then takes down
REM  the docker-compose dev stack (Postgres + sshd).
REM
REM  Note: the backend window and the frontend window are separate
REM  cmd processes. We kill them by title, then stop the containers.
REM ===================================================================

setlocal

echo.
echo === BPL Order Engine Admin - shutdown ===

echo [1/4] Removing frontend .env.local (Vite override file)...
if exist "%~dp0frontend\.env.local" (
  del /F /Q "%~dp0frontend\.env.local"
  echo   removed.
) else (
  echo   (no frontend\.env.local to remove)
)

echo [2/4] Closing backend window (title: "BPL Backend (port 8080)")...
taskkill /FI "WINDOWTITLE eq BPL Backend*" /T /F 2>nul
if errorlevel 1 (
  echo   (no matching backend window)
) else (
  echo   backend window closed.
)

echo [3/4] Closing frontend window (title: "BPL Frontend (port 5173)")...
taskkill /FI "WINDOWTITLE eq BPL Frontend*" /T /F 2>nul
if errorlevel 1 (
  echo   (no matching frontend window)
) else (
  echo   frontend window closed.
)

echo [4/4] Stopping docker compose dev stack (Postgres + sshd)...
docker compose down
if errorlevel 1 (
  echo WARN: docker compose down returned an error (continuing).
)

echo.
echo Done. Run run.bat to start again.
echo.
endlocal
