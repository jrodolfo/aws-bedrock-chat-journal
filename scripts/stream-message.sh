#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-Explain the Amazon Bedrock Converse API using streaming.}"

usage() {
  cat <<EOF
Usage:
  SESSION_ID=<session-id> ./scripts/stream-message.sh
  SESSION_ID=<session-id> MESSAGE_TEXT="Explain streaming." ./scripts/stream-message.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./scripts/stream-message.sh

What it does:
  1. Sends one user message to the streaming endpoint
  2. Prints server-sent events as they arrive
  3. Persists the final assistant reply only after successful completion

Required environment variables:
  SESSION_ID      Existing session ID stored by the application

Optional environment variables:
  BASE_URL        API base URL
                  Default: http://localhost:8080

  MESSAGE_TEXT    User message sent to the existing session
                  Default: Explain the Amazon Bedrock Converse API using streaming.

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

curl --no-buffer --silent --show-error \
  --request POST \
  --header "Accept: text/event-stream" \
  --header "Content-Type: application/json" \
  --data "{
    \"text\": \"${MESSAGE_TEXT}\"
  }" \
  "${BASE_URL}/api/sessions/${SESSION_ID}/messages/stream"
echo
