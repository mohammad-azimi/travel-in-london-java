#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
bash build.sh
java -Djava.awt.headless=true -jar dist/london-agent.jar --self-test
