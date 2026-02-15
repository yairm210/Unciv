#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/web/build/dist"

if [[ ! -f "${DIST_DIR}/index.html" ]]; then
  echo "Missing dist index: ${DIST_DIR}/index.html" >&2
  exit 1
fi

PORT="$(python3 - <<'PY'
import socket
sock = socket.socket()
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()
PY
)"

SERVER_LOG="${ROOT_DIR}/tmp/war-ui-e2e-local-static-server.log"
mkdir -p "${ROOT_DIR}/tmp"

python3 "${ROOT_DIR}/scripts/web/static-server.py" --port "${PORT}" --dir "${DIST_DIR}" >"${SERVER_LOG}" 2>&1 &
SERVER_PID=$!
cleanup() {
  if kill -0 "${SERVER_PID}" >/dev/null 2>&1; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

BASE_URL="http://127.0.0.1:${PORT}"

for _ in $(seq 1 60); do
  if curl --connect-timeout 1 --max-time 1 -fsS "${BASE_URL}/index.html" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

curl --connect-timeout 1 --max-time 1 -fsS "${BASE_URL}/index.html" >/dev/null

cd "${ROOT_DIR}"
export WEB_BASE_URL="${BASE_URL}"
export HEADLESS="${HEADLESS:-false}"
export WAR_UI_STARTUP_RETRY_ATTEMPTS="${WAR_UI_STARTUP_RETRY_ATTEMPTS:-3}"

run_gate_with_startup_retry() {
  local gate_name="$1"
  local result_file="$2"
  shift 2
  local attempt=1
  while [[ "${attempt}" -le "${WAR_UI_STARTUP_RETRY_ATTEMPTS}" ]]; do
    if "$@"; then
      return 0
    fi
    if [[ -f "${result_file}" ]] && jq -e '.failures[]? | contains("state=null")' "${result_file}" >/dev/null 2>&1; then
      echo "${gate_name}: startup flake on attempt ${attempt}/${WAR_UI_STARTUP_RETRY_ATTEMPTS}, retrying..." >&2
      attempt=$((attempt + 1))
      sleep 1
      continue
    fi
    return 1
  done
  echo "${gate_name}: startup flake persisted after ${WAR_UI_STARTUP_RETRY_ATTEMPTS} attempts." >&2
  return 1
}

run_gate_with_startup_retry "war_from_start" "${ROOT_DIR}/tmp/web-ui-war-from-start-result.json" node "${ROOT_DIR}/scripts/web/run-web-ui-war-from-start.js"
run_gate_with_startup_retry "war_preworld" "${ROOT_DIR}/tmp/web-ui-war-preworld-result.json" node "${ROOT_DIR}/scripts/web/run-web-ui-war-preworld.js"
run_gate_with_startup_retry "war_deep" "${ROOT_DIR}/tmp/web-ui-war-deep-result.json" node "${ROOT_DIR}/scripts/web/run-web-ui-war-deep.js"
