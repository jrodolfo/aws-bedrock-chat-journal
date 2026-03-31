#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-Continue the conversation.}"

usage() {
  cat <<EOF
Usage:
  SESSION_ID=<session-id> ./requests/send-message.sh
  SESSION_ID=<session-id> MESSAGE_TEXT="Compare Converse and InvokeModel." ./requests/send-message.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./requests/send-message.sh

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

echo "== Get current session =="
curl --silent --show-error "${BASE_URL}/api/sessions/${SESSION_ID}"
echo
echo

echo "== Send message =="
curl --silent --show-error \
  --request POST \
  --header "Content-Type: application/json" \
  --data "{
    \"text\": \"${MESSAGE_TEXT}\"
  }" \
  "${BASE_URL}/api/sessions/${SESSION_ID}/messages"
echo
echo

echo "== Get updated session =="
curl --silent --show-error "${BASE_URL}/api/sessions/${SESSION_ID}"
echo
