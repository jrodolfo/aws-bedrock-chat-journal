#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MODEL_ID="${MODEL_ID:-amazon.nova-lite-v1:0}"
SYSTEM_PROMPT="${SYSTEM_PROMPT:-You are a helpful AWS study assistant.}"
STREAM="${STREAM:-true}"
SHOW_METADATA="${SHOW_METADATA:-true}"

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

  SHOW_METADATA   true or false
                  Default: true

Commands:
  /help           Show chat commands
  /session        Show current session info
  /model          Show the current session model
  /prompt         Show the current system prompt
  /history        Show the stored conversation
  /stream on      Enable streaming replies
  /stream off     Disable streaming replies
  /metadata on    Show metadata after replies
  /metadata off   Hide metadata after replies
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
SHOW_METADATA_MODE="$(normalize_stream_mode "${SHOW_METADATA}")"

print_request_failure_hint() {
  echo >&2
  echo "Request failed." >&2
  echo "Is the app running at ${BASE_URL}?" >&2
  echo "Try ./scripts/run-local.sh" >&2
}

print_chat_help() {
  cat <<EOF
Commands:
  /help
  /session
  /model
  /prompt
  /history
  /stream on
  /stream off
  /metadata on
  /metadata off
  /reset
  /exit
EOF
}

print_session_info() {
  local mode_label="streaming"
  local metadata_label="on"
  if [[ "${STREAM_MODE}" != "true" ]]; then
    mode_label="non-streaming"
  fi
  if [[ "${SHOW_METADATA_MODE}" != "true" ]]; then
    metadata_label="off"
  fi

  echo "Session ID: ${SESSION_ID}"
  echo "Mode: ${mode_label}"
  echo "Metadata: ${metadata_label}"
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

curl_json_request() {
  if ! curl --silent --show-error "$@"; then
    print_request_failure_hint
    return 1
  fi
}

fetch_current_session() {
  curl_json_request "${BASE_URL}/api/sessions/${SESSION_ID}"
}

print_current_model() {
  local session_json
  session_json="$(fetch_current_session)" || return 1

  SESSION_JSON="${session_json}" python3 - <<'PY'
import json
import os

session = json.loads(os.environ["SESSION_JSON"])
print("Model: " + str(session.get("modelId") or "(none)"))
PY
}

print_current_prompt() {
  local session_json
  session_json="$(fetch_current_session)" || return 1

  SESSION_JSON="${session_json}" python3 - <<'PY'
import json
import os

session = json.loads(os.environ["SESSION_JSON"])
prompt = session.get("systemPrompt")
if prompt:
    print("System prompt:")
    print(prompt)
else:
    print("System prompt: (none)")
PY
}

print_history() {
  local session_json
  session_json="$(fetch_current_session)" || return 1

  SESSION_JSON="${session_json}" python3 - <<'PY'
import json
import os

session = json.loads(os.environ["SESSION_JSON"])
messages = session.get("messages") or []

if not messages:
    print("No messages in this session.")
    raise SystemExit(0)

for index, message in enumerate(messages, start=1):
    role = message.get("role", "unknown")
    print(f"[{index}] {role}")
    for block in message.get("content") or []:
        text = block.get("text")
        if text:
            print(text)
    print()
PY
}

create_session_if_needed() {
  if [[ -n "${SESSION_ID}" ]]; then
    return
  fi

  echo "Creating a new session..."
  local create_response
  create_response="$(
    curl_json_request \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_create_payload)" \
      "${BASE_URL}/api/sessions"
  )" || exit 1

  SESSION_ID="$(
    CREATE_RESPONSE="${create_response}" python3 - <<'PY'
import json
import os
import sys

try:
    response = json.loads(os.environ["CREATE_RESPONSE"])
except json.JSONDecodeError:
    print("Failed to parse create-session response as JSON.", file=sys.stderr)
    print(os.environ["CREATE_RESPONSE"], file=sys.stderr)
    raise SystemExit(1)

print(response["sessionId"])
PY
  )" || {
    echo "Session creation did not return a valid JSON body." >&2
    exit 1
  }

  echo "Created session: ${SESSION_ID}"
}

print_non_streaming_response() {
  RESPONSE_JSON="${1}" SHOW_METADATA_MODE="${SHOW_METADATA_MODE}" python3 - <<'PY'
import json
import os

response = json.loads(os.environ["RESPONSE_JSON"])
reply = response.get("reply", "")
metadata = response.get("metadata") or {}
show_metadata = os.environ.get("SHOW_METADATA_MODE") == "true"

print("Assistant reply:")
print(reply)
print()

if not show_metadata:
    raise SystemExit(0)

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

  if ! curl --silent --show-error \
    --request POST \
    "${BASE_URL}/api/sessions/${SESSION_ID}/reset" >/dev/null; then
    print_request_failure_hint
    return 1
  fi

  echo "Session reset."
}

send_non_streaming_message() {
  local response
  response="$(
    curl_json_request \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_message_payload "$1")" \
      "${BASE_URL}/api/sessions/${SESSION_ID}/messages"
  )" || return 1

  print_non_streaming_response "${response}"
}

send_streaming_message() {
  BASE_URL="${BASE_URL}" SESSION_ID="${SESSION_ID}" MESSAGE_TEXT="$1" SHOW_METADATA="${SHOW_METADATA_MODE}" "${SCRIPT_DIR}/stream-message.sh"
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
    /model)
      print_current_model || true
      ;;
    /prompt)
      print_current_prompt || true
      ;;
    /history)
      print_history || true
      ;;
    "/stream on")
      STREAM_MODE="true"
      echo "Streaming enabled."
      ;;
    "/stream off")
      STREAM_MODE="false"
      echo "Streaming disabled."
      ;;
    "/metadata on")
      SHOW_METADATA_MODE="true"
      echo "Metadata enabled."
      ;;
    "/metadata off")
      SHOW_METADATA_MODE="false"
      echo "Metadata disabled."
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
