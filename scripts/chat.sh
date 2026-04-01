#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MODEL_ID="${MODEL_ID:-amazon.nova-lite-v1:0}"
SYSTEM_PROMPT="${SYSTEM_PROMPT:-You are a helpful AWS study assistant.}"
STREAM="${STREAM:-true}"

usage() {
  cat <<EOF
Usage:
  ./scripts/chat.sh
  SESSION_ID=<session-id> ./scripts/chat.sh
  STREAM=false SESSION_ID=<session-id> ./scripts/chat.sh
  MODEL_ID=amazon.nova-lite-v1:0 SYSTEM_PROMPT="You are an AWS tutor." ./scripts/chat.sh

What it does:
  1. Reuses SESSION_ID when provided, otherwise creates a new session
  2. Opens an interactive terminal chat loop
  3. Sends each prompt through the REST API using streaming or non-streaming mode

Optional environment variables:
  BASE_URL        API base URL
                  Default: http://localhost:8080

  SESSION_ID      Existing session ID to continue
                  Default: create a new session automatically

  MODEL_ID        Bedrock model ID used when creating a new session
                  Default: amazon.nova-lite-v1:0

  SYSTEM_PROMPT   System prompt used when creating a new session
                  Default: You are a helpful AWS study assistant.

  STREAM          true or false
                  Default: true

Commands:
  /help           Show chat commands
  /session        Show current session info
  /stream on      Enable streaming replies
  /stream off     Disable streaming replies
  /reset          Reset the current session messages
  /exit           Exit the chat

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

normalize_stream_mode() {
  local lowered
  lowered="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"

  case "${lowered}" in
    true|on|yes|1) echo "true" ;;
    false|off|no|0) echo "false" ;;
    *)
      echo "Invalid STREAM value: ${1}" >&2
      exit 1
      ;;
  esac
}

STREAM_MODE="$(normalize_stream_mode "${STREAM}")"

print_chat_help() {
  cat <<EOF
Commands:
  /help
  /session
  /stream on
  /stream off
  /reset
  /exit
EOF
}

print_session_info() {
  local mode_label="streaming"
  if [[ "${STREAM_MODE}" != "true" ]]; then
    mode_label="non-streaming"
  fi

  echo "Session ID: ${SESSION_ID}"
  echo "Mode: ${mode_label}"
  echo "Base URL: ${BASE_URL}"
}

build_create_payload() {
  MODEL_ID="${MODEL_ID}" SYSTEM_PROMPT="${SYSTEM_PROMPT}" python3 - <<'PY'
import json
import os

payload = {}
model_id = os.environ.get("MODEL_ID", "").strip()
system_prompt = os.environ.get("SYSTEM_PROMPT", "").strip()

if model_id:
    payload["modelId"] = model_id
if system_prompt:
    payload["systemPrompt"] = system_prompt

print(json.dumps(payload))
PY
}

build_message_payload() {
  MESSAGE_TEXT="${1}" python3 - <<'PY'
import json
import os

print(json.dumps({"text": os.environ["MESSAGE_TEXT"]}))
PY
}

create_session_if_needed() {
  if [[ -n "${SESSION_ID}" ]]; then
    return
  fi

  echo "Creating a new session..."
  local create_response
  create_response="$(
    curl --silent --show-error \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_create_payload)" \
      "${BASE_URL}/api/sessions"
  )"

  SESSION_ID="$(
    printf '%s' "${create_response}" | python3 - <<'PY'
import json
import sys

response = json.load(sys.stdin)
print(response["sessionId"])
PY
  )"

  echo "Created session: ${SESSION_ID}"
}

print_non_streaming_response() {
  python3 - <<'PY'
import json
import sys

response = json.load(sys.stdin)
reply = response.get("reply", "")
metadata = response.get("metadata") or {}

print("Assistant reply:")
print(reply)
print()

requested_at = metadata.get("requestedAt")
responded_at = metadata.get("respondedAt")
duration_ms = metadata.get("durationMs")
total_tokens = metadata.get("totalTokens")
input_tokens = metadata.get("inputTokens")
output_tokens = metadata.get("outputTokens")
stop_reason = metadata.get("stopReason")

if requested_at:
    print(f"Requested at: {requested_at}")
if responded_at:
    print(f"Responded at: {responded_at}")
if duration_ms is not None:
    print(f"Duration: {duration_ms} ms")
if total_tokens is not None:
    print(f"Total tokens: {total_tokens}")
elif input_tokens is not None or output_tokens is not None:
    token_parts = []
    if input_tokens is not None:
        token_parts.append(f"input={input_tokens}")
    if output_tokens is not None:
        token_parts.append(f"output={output_tokens}")
    print("Tokens: " + ", ".join(token_parts))
if stop_reason:
    print(f"Stop reason: {stop_reason}")
PY
}

reset_current_session() {
  printf "Type 'yes' to reset session %s: " "${SESSION_ID}"
  read -r confirmation

  if [[ "${confirmation}" != "yes" ]]; then
    echo "Reset cancelled."
    return
  fi

  curl --silent --show-error \
    --request POST \
    "${BASE_URL}/api/sessions/${SESSION_ID}/reset" >/dev/null

  echo "Session reset."
}

send_non_streaming_message() {
  local response
  response="$(
    curl --silent --show-error \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_message_payload "$1")" \
      "${BASE_URL}/api/sessions/${SESSION_ID}/messages"
  )"

  printf '%s' "${response}" | print_non_streaming_response
}

send_streaming_message() {
  BASE_URL="${BASE_URL}" SESSION_ID="${SESSION_ID}" MESSAGE_TEXT="$1" "${SCRIPT_DIR}/stream-message.sh"
}

create_session_if_needed

echo
print_session_info
echo
echo "Type /help for commands."
echo

while true; do
  printf "you> "
  if ! read -r user_input; then
    echo
    break
  fi

  case "${user_input}" in
    "")
      continue
      ;;
    /help)
      print_chat_help
      ;;
    /session)
      print_session_info
      ;;
    "/stream on")
      STREAM_MODE="true"
      echo "Streaming enabled."
      ;;
    "/stream off")
      STREAM_MODE="false"
      echo "Streaming disabled."
      ;;
    /reset)
      reset_current_session
      ;;
    /exit)
      echo "Goodbye."
      break
      ;;
    /*)
      echo "Unknown command: ${user_input}" >&2
      echo "Type /help for commands." >&2
      ;;
    *)
      echo
      if [[ "${STREAM_MODE}" == "true" ]]; then
        send_streaming_message "${user_input}"
      else
        send_non_streaming_message "${user_input}"
      fi
      echo
      ;;
  esac
done
