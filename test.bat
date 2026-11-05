@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -jar dist\london-agent.jar --self-test
