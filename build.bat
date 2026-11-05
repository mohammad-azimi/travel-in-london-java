@echo off
setlocal EnableExtensions
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo A JDK 17 or newer is required. Add its bin directory to PATH.
    echo Check: java -version and javac -version
    exit /b 1
)
if not exist build\classes mkdir build\classes
if not exist dist mkdir dist
javac --release 17 -Xlint:all -encoding UTF-8 -d build\classes @sources.txt
if errorlevel 1 exit /b 1
jar --create --file dist\london-agent.jar --main-class london.Main -C build\classes .
if errorlevel 1 exit /b 1
echo Built dist\london-agent.jar
