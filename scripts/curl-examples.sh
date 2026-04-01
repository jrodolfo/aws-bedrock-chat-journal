#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MODEL_ID="${MODEL_ID:-amazon.nova-lite-v1:0}"
SYSTEM_PROMPT="${SYSTEM_PROMPT:-You are a helpful AWS study assistant.}"
MESSAGE_TEXT="${MESSAGE_TEXT:-Explain the Amazon Bedrock Converse API in simple terms.}"

usage() {
  cat <<EOF
Usage:
  ./scripts/curl-examples.sh
  BASE_URL=http://localhost:8080 ./scripts/curl-examples.sh
  MODEL_ID=amazon.nova-lite-v1:0 SYSTEM_PROMPT="You are an AWS tutor." ./scripts/curl-examples.sh
  MESSAGE_TEXT="What is prompt management?" ./scripts/curl-examples.sh

What it does:
  1. Calls GET /api/health
  2. Creates a new chat session
  3. Reads the created session
  4. Sends one user message
  5. Reads the updated session again

Environment variables:
  BASE_URL       API base URL
                 Default: http://localhost:8080

  MODEL_ID       Bedrock model ID used when creating the session
                 Default: amazon.nova-lite-v1:0

  SYSTEM_PROMPT  System prompt stored with the session
                 Default: You are a helpful AWS study assistant.

  MESSAGE_TEXT   User message sent to the session
                 Default: Explain the Amazon Bedrock Converse API in simple terms.

Options:
  -h, --help     Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

echo "== Health =="
curl --silent "${BASE_URL}/api/health"
echo
echo

echo "== Create session =="
CREATE_RESPONSE="$(
  curl --silent --show-error \
    --request POST \
    --header "Content-Type: application/json" \
    --data "{
      \"modelId\": \"${MODEL_ID}\",
      \"systemPrompt\": \"${SYSTEM_PROMPT}\"
    }" \
    "${BASE_URL}/api/sessions"
)"
echo "${CREATE_RESPONSE}"
echo
echo

SESSION_ID="$(printf '%s' "${CREATE_RESPONSE}" | sed -n 's/.*"sessionId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [[ -z "${SESSION_ID}" ]]; then
  echo "Failed to extract sessionId from create-session response" >&2
  exit 1
fi

echo "Session ID: ${SESSION_ID}"
echo

echo "== Get session =="
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
