#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-Continue the conversation.}"

usage() {
  cat <<EOF
Usage:
  SESSION_ID=<session-id> ./scripts/send-message.sh
  SESSION_ID=<session-id> MESSAGE_TEXT="Compare Converse and InvokeModel." ./scripts/send-message.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./scripts/send-message.sh

What it does:
  1. Reads the existing session
  2. Sends one new message to that session
  3. Reads the updated session again

Required environment variables:
  SESSION_ID      Existing session ID stored by the application

Optional environment variables:
  BASE_URL        API base URL
                  Default: http://localhost:8080

  MESSAGE_TEXT    User message sent to the existing session
                  Default: Continue the conversation.

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

echo "== Get current session =="
run_curl "${BASE_URL}/api/sessions/${SESSION_ID}"
echo
echo

echo "== Send message =="
run_curl \
  --request POST \
  --header "Content-Type: application/json" \
  --data "{
    \"text\": \"${MESSAGE_TEXT}\"
  }" \
  "${BASE_URL}/api/sessions/${SESSION_ID}/messages"
echo
echo

echo "== Get updated session =="
run_curl "${BASE_URL}/api/sessions/${SESSION_ID}"
echo
