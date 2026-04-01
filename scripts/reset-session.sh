#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"

usage() {
  cat <<EOF
Usage:
  SESSION_ID=<session-id> ./scripts/reset-session.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./scripts/reset-session.sh

What it does:
  1. Resets the message history for an existing session
  2. Keeps sessionId, modelId, and systemPrompt
  3. Prints the updated session

Required environment variables:
  SESSION_ID      Existing session ID stored by the application

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

if [[ -z "${SESSION_ID}" ]]; then
  echo "SESSION_ID is required." >&2
  echo >&2
  usage >&2
  exit 1
fi

run_curl() {
  if ! curl --silent --show-error "$@"; then
    echo >&2
    echo "Request failed." >&2
    echo "Is the app running at ${BASE_URL}?" >&2
    echo "Try ./scripts/run-local.sh" >&2
    exit 1
  fi
}

run_curl \
  --request POST \
  "${BASE_URL}/api/sessions/${SESSION_ID}/reset"
echo
