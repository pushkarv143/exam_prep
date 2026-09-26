@echo off
rem Double-click to run deploy\server-setup.sh on the Oracle server. Output goes to deploy\server-setup.log.
cd /d "%~dp0\.."
set "KEY=%USERPROFILE%\Downloads\ssh-key-2026-09-26.key"
set "HOST=ubuntu@129.154.226.175"

rem OpenSSH on Windows refuses keys that other users can read.
icacls "%KEY%" /inheritance:r /grant:r "%USERNAME%:R" >nul

echo Setting up %HOST% ... this takes 2-4 minutes.
ssh -i "%KEY%" -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=20 %HOST% "tr -d '\r' | bash -s" < deploy\server-setup.sh > deploy\server-setup.log 2>&1
echo Exit code: %ERRORLEVEL% >> deploy\server-setup.log
type deploy\server-setup.log
echo.
echo Finished. You can close this window.
pause
