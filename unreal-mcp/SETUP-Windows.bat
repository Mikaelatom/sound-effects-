@echo off
setlocal
title Unreal connector for Claude - setup
cd /d "%~dp0"
echo.
echo  ==========================================
echo    Unreal connector for Claude - setup
echo  ==========================================
echo.

where uv >nul 2>nul
if errorlevel 1 (
  echo Installing uv, a small helper that runs the connector...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://astral.sh/uv/install.ps1 | iex"
  set "PATH=%USERPROFILE%\.local\bin;%PATH%"
)
where uv >nul 2>nul
if errorlevel 1 goto :fail

echo Installing the connector...
uv tool install --force --reinstall "%~dp0." || goto :fail
for /f "delims=" %%i in ('uv tool dir --bin') do set "BIN=%%i"

uv run --no-project --with "%~dp0." python "%~dp0setup\configure.py" "%BIN%\unreal-mcp.exe" "%~1" || goto :fail
echo.
pause
exit /b 0

:fail
echo.
echo Something went wrong. Take a screenshot of this window and show it to Claude.
pause
exit /b 1
