#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="docker-compose.web.yml"
SERVICE_NAME="web"

if [ "$#" -eq 0 ]; then
  echo "Usage: scripts/web/in-container.sh '<command>'"
  echo "Example: scripts/web/in-container.sh './gradlew :web:webBuildWasm'"
  exit 1
fi

docker compose -f "$COMPOSE_FILE" up -d "$SERVICE_NAME" >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T "$SERVICE_NAME" bash -lc "$*"
