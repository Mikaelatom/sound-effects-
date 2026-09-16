@echo off
REM Double-click this file to start Claude Voice. (Windows)
cd /d "%~dp0"
python speak.py || py speak.py
pause
