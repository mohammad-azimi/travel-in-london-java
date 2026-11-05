#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
mkdir -p build/classes dist
if command -v javac >/dev/null 2>&1; then
    javac --release 17 -Xlint:all -encoding UTF-8 -d build/classes @sources.txt
else
    java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -Xlint:all -encoding UTF-8 -d build/classes @sources.txt
fi
if command -v jar >/dev/null 2>&1; then
    jar --create --file dist/london-agent.jar --main-class london.Main -C build/classes .
else
    java -m jdk.jartool/sun.tools.jar.Main --create --file dist/london-agent.jar --main-class london.Main -C build/classes .
fi
echo 'Built dist/london-agent.jar'
