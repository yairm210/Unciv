#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [ "$#" -eq 0 ]; then
  echo "Usage: scripts/web/in-container.sh '<command>'"
  echo "Example: scripts/web/in-container.sh './gradlew :web:webBuildWasm'"
  exit 1
fi

docker compose -f docker-compose.web.yml run --rm web bash -lc "$*"
