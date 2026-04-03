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
