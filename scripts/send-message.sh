#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-}"

usage() {
  cat <<EOF
Usage:
  ./scripts/send-message.sh <session-id>
  ./scripts/send-message.sh <session-id> "Compare Converse and InvokeModel."
  ./scripts/send-message.sh <session-id>    # prompts for message text interactively
  SESSION_ID=<session-id> ./scripts/send-message.sh
  SESSION_ID=<session-id> MESSAGE_TEXT="Compare Converse and InvokeModel." ./scripts/send-message.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./scripts/send-message.sh

What it does:
  1. Reads the existing session
  2. Sends one new message to that session
  3. Reads the updated session again

Positional arguments:
  session-id      Existing session ID stored by the application
  message-text    Optional user message sent to the existing session.
                  If omitted, the script prompts interactively.

Optional environment variables:
  SESSION_ID      Existing session ID stored by the application when no positional session-id is provided

  BASE_URL        API base URL
                  Default: http://localhost:8080

  MESSAGE_TEXT    User message sent to the existing session when no positional message-text is provided

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -gt 2 ]]; then
  echo "Too many arguments." >&2
  echo >&2
  usage >&2
  exit 1
fi

if [[ $# -ge 1 ]]; then
  SESSION_ID="$1"
fi

if [[ $# -ge 2 ]]; then
  MESSAGE_TEXT="$2"
fi

if [[ -z "${SESSION_ID}" ]]; then
  echo "SESSION_ID is required." >&2
  echo >&2
  usage >&2
  exit 1
fi

if [[ -z "${MESSAGE_TEXT}" ]]; then
  if [[ -t 0 ]]; then
    printf "message> "
    read -r MESSAGE_TEXT
  elif IFS= read -r MESSAGE_TEXT; then
    printf "message> "
  else
    echo "MESSAGE_TEXT is required when running non-interactively." >&2
    echo >&2
    usage >&2
    exit 1
  fi
fi

if [[ -z "${MESSAGE_TEXT}" ]]; then
  echo "MESSAGE_TEXT cannot be empty." >&2
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
