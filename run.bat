@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call build.bat
if errorlevel 1 (
    pause
    exit /b 1
)
java -jar dist\london-agent.jar %*
if errorlevel 1 pause
