@echo off
title RemotePad Server
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel%==0 (
    python server.py
) else (
    py server.py
)
echo.
echo Server stopped. If you saw "python is not recognized", install Python first.
pause
