@echo off
rem Double-click to deploy the newest images (built by GitHub Actions) to the Oracle server.
rem Output goes to deploy\deploy.log.
cd /d "%~dp0\.."
set "KEY=%USERPROFILE%\Downloads\ssh-key-2026-09-26.key"
set "IP=129.154.226.175"
set "HOST=ubuntu@%IP%"
rem Frontend on Vercel: its domain must be allowed by the backend (CORS) and used in e-mail links.
set "VERCEL=https://exam-prep-pushkar15.vercel.app"
set "SETTINGS='FRONTEND_URL=%VERCEL%' 'EXTRA_CORS_ORIGINS=%VERCEL%,https://exam-prep-*-pushkar15.vercel.app'"
set "SSHOPTS=-i "%KEY%" -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=20"

echo Deploying to %HOST% ... this can take 5-10 minutes the first time.
> deploy\deploy.log 2>&1 (
  ssh %SSHOPTS% %HOST% "mkdir -p examprep"
  scp %SSHOPTS% deploy\docker-compose.prod.yml deploy\Caddyfile deploy\deploy.sh %HOST%:examprep/
  ssh %SSHOPTS% %HOST% "cd examprep && sed -i 's/\r$//' docker-compose.prod.yml Caddyfile deploy.sh && bash deploy.sh %IP% %SETTINGS%"
)
echo Exit code: %ERRORLEVEL% >> deploy\deploy.log
type deploy\deploy.log
echo.
echo Finished. You can close this window.
pause
