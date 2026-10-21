@echo off
setlocal
cd /d "%~dp0"

echo Running NeoForge Dedicated Server Test Script...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test_server.ps1" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Server script exited with error code %ERRORLEVEL%
    pause
)
endlocal
