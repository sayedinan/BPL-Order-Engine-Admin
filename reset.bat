@echo off
REM ===================================================================
REM  BPL Order Engine Admin - dev reset
REM
REM  Drops the Postgres volume (deletes all seeded users, engines,
REM  audit rows) and brings the stack back up. Use when you want a
REM  clean slate without manually removing the volume.
REM ===================================================================

setlocal

echo.
echo === BPL Order Engine Admin - dev reset ===
echo This will:
echo   - stop the backend and frontend windows
echo   - drop the docker compose stack AND its volumes
echo   - bring the stack back up
echo.

set /a confirm=0
set /p confirm=Type YES to continue, anything else to abort:
if /I not "%confirm%"=="YES" (
  echo Aborted.
  exit /b 1
)

echo [1/3] Closing backend + frontend windows...
call "%~dp0stop.bat" >nul 2>nul
echo done.

echo [2/3] Removing docker compose volumes (Postgres data)...
docker compose down -v
if errorlevel 1 (
  echo WARN: docker compose down -v returned an error (continuing).
)

echo [3/3] Bringing the stack back up...
call "%~dp0run.bat"

endlocal
