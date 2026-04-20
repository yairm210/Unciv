#!/usr/bin/env bash
set -euo pipefail

attempts="${WEB_BUILD_ATTEMPTS:-3}"
connection_timeout_ms="${WEB_BUILD_CONNECTION_TIMEOUT_MS:-120000}"
socket_timeout_ms="${WEB_BUILD_SOCKET_TIMEOUT_MS:-120000}"
retry_sleep_seconds="${WEB_BUILD_RETRY_SLEEP_SECONDS:-20}"

if ! [[ "$attempts" =~ ^[0-9]+$ ]] || [ "$attempts" -lt 1 ]; then
  echo "WEB_BUILD_ATTEMPTS must be a positive integer, got: $attempts" >&2
  exit 2
fi

gradle_cmd=(
  ./gradlew
  --no-daemon
  --stacktrace
  "-Dorg.gradle.internal.http.connectionTimeout=${connection_timeout_ms}"
  "-Dorg.gradle.internal.http.socketTimeout=${socket_timeout_ms}"
  :web:webBuildJs
)

for attempt in $(seq 1 "$attempts"); do
  echo "[web-build] attempt ${attempt}/${attempts}: ${gradle_cmd[*]}"
  if "${gradle_cmd[@]}"; then
    echo "[web-build] success on attempt ${attempt}/${attempts}"
    exit 0
  fi
  if [ "$attempt" -ge "$attempts" ]; then
    echo "[web-build] failed after ${attempts} attempts" >&2
    exit 1
  fi
  sleep_time=$((retry_sleep_seconds * attempt))
  echo "[web-build] retrying in ${sleep_time}s..."
  sleep "$sleep_time"
done

