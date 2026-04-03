#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"
BASE_URL="${BASE_URL:-http://localhost:8080}"

usage() {
  cat <<EOF
Usage:
  ./scripts/check-backend.sh
  BASE_URL=http://localhost:8080 ./scripts/check-backend.sh

What it does:
  Calls GET /api/health and confirms that the backend responds with status OK.
  When BASE_URL is local, it may also print the listening PID for that port.

Optional environment variables:
  BASE_URL        API base URL
                  Default: http://localhost:8080

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

print_request_failure_hint() {
  echo >&2
  echo "Request failed." >&2
  echo "Is the app running at ${BASE_URL}?" >&2
  echo "Try ./scripts/run-local.sh" >&2
}

extract_host_and_port() {
  BASE_URL_VALUE="${BASE_URL}" run_python - <<'PY'
import os
from urllib.parse import urlparse

parsed = urlparse(os.environ["BASE_URL_VALUE"])
host = parsed.hostname or ""
port = parsed.port
if port is None:
    if parsed.scheme == "https":
        port = 443
    elif parsed.scheme == "http":
        port = 80
    else:
        port = ""

print(host)
print(port)
PY
}

lookup_local_listening_pid() {
  local port="$1"
  local pid=""

  if command -v cmd.exe >/dev/null 2>&1; then
    pid="$(
      NETSTAT_OUTPUT="$(cmd.exe /c netstat -ano -p tcp 2>NUL | tr -d '\r')" TARGET_PORT="${port}" run_python - <<'PY'
import os
import re

target_port = os.environ["TARGET_PORT"]
pattern = re.compile(rf'^\s*TCP\s+\S+:{re.escape(target_port)}\s+\S+\s+LISTENING\s+(\d+)\s*$')

for line in os.environ["NETSTAT_OUTPUT"].splitlines():
    match = pattern.match(line)
    if match:
        print(match.group(1))
        break
PY
    )"
    if [[ -n "${pid}" ]]; then
      printf '%s\n' "${pid}"
      return 0
    fi
  fi

  if command -v lsof >/dev/null 2>&1; then
    pid="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null | head -n 1)"
    if [[ -n "${pid}" ]]; then
      printf '%s\n' "${pid}"
      return 0
    fi
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    pid="$(powershell.exe -NoProfile -Command \
      "$lines = netstat -ano -p tcp; $match = $lines | Select-String -Pattern '(^|\s)TCP\s+\S+:${port}\s+\S+\s+LISTENING\s+(\d+)\s*$' | Select-Object -First 1; if ($match) { $match.Matches[0].Groups[2].Value }" \
      2>/dev/null | tr -d '\r' | head -n 1)"
    if [[ -n "${pid}" ]]; then
      printf '%s\n' "${pid}"
      return 0
    fi
  fi

  if command -v netstat >/dev/null 2>&1; then
    pid="$(netstat -ano 2>/dev/null | awk -v target=":${port}" '
      $1 ~ /TCP/ && $2 ~ target "$" && $4 == "LISTENING" { print $5; exit }
    ' | tr -d '\r' | head -n 1)"
    if [[ -n "${pid}" ]]; then
      printf '%s\n' "${pid}"
      return 0
    fi
  fi

  return 0
}

if ! response="$(curl --fail --silent --show-error "${BASE_URL}/api/health")"; then
  print_request_failure_hint
  exit 1
fi

if ! health_status="$(
  HEALTH_RESPONSE="${response}" run_python - <<'PY'
import json
import os
import sys

try:
    payload = json.loads(os.environ["HEALTH_RESPONSE"])
except json.JSONDecodeError:
    raise SystemExit(1)

status = payload.get("status")
if status != "OK":
    raise SystemExit(1)

print(status)
PY
)"; then
  echo "Unexpected health response from ${BASE_URL}/api/health." >&2
  echo "Expected JSON with status=OK." >&2
  echo "Response: ${response}" >&2
  exit 1
fi

echo "Backend is up."
echo "Base URL: ${BASE_URL}"
echo "Health status: ${health_status}"

mapfile -t base_url_parts < <(extract_host_and_port)
base_url_host="${base_url_parts[0]:-}"
base_url_port="${base_url_parts[1]:-}"

if [[ "${base_url_host}" == "localhost" || "${base_url_host}" == "127.0.0.1" ]]; then
  listening_pid="$(lookup_local_listening_pid "${base_url_port}")"
  if [[ -n "${listening_pid}" ]]; then
    echo "Listening PID on port ${base_url_port}: ${listening_pid}"
  fi
fi
