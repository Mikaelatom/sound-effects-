@echo off
REM Double-click this to start Claude Voice. Keep it next to speak.ps1.
title Claude Voice
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0speak.ps1"
echo.
echo Claude Voice has stopped.
pause
