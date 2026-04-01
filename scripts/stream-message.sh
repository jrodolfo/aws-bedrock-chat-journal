#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-Explain the Amazon Bedrock Converse API using streaming.}"
OUTPUT_MODE="pretty"

usage() {
  cat <<EOF
Usage:
  SESSION_ID=<session-id> ./scripts/stream-message.sh
  SESSION_ID=<session-id> ./scripts/stream-message.sh --raw
  SESSION_ID=<session-id> MESSAGE_TEXT="Explain streaming." ./scripts/stream-message.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./scripts/stream-message.sh

What it does:
  1. Sends one user message to the streaming endpoint
  2. Prints streamed assistant text as it arrives
  3. Persists the final assistant reply only after successful completion

Required environment variables:
  SESSION_ID      Existing session ID stored by the application

Optional environment variables:
  BASE_URL        API base URL
                  Default: http://localhost:8080

  MESSAGE_TEXT    User message sent to the existing session
                  Default: Explain the Amazon Bedrock Converse API using streaming.

Options:
  --raw           Print raw server-sent events instead of pretty terminal output
  -h, --help      Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --raw)
      OUTPUT_MODE="raw"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${SESSION_ID}" ]]; then
  echo "SESSION_ID is required." >&2
  echo >&2
  usage >&2
  exit 1
fi

run_curl() {
  curl --no-buffer --silent --show-error \
    --request POST \
    --header "Accept: text/event-stream" \
    --header "Content-Type: application/json" \
    --data "{
      \"text\": \"${MESSAGE_TEXT}\"
    }" \
    "${BASE_URL}/api/sessions/${SESSION_ID}/messages/stream"
}

set +e

if [[ "${OUTPUT_MODE}" == "raw" ]]; then
  run_curl
  curl_status=$?
else
  BASE_URL="${BASE_URL}" SESSION_ID="${SESSION_ID}" MESSAGE_TEXT="${MESSAGE_TEXT}" python3 - <<'PY'
import json
import os
import subprocess
import sys

current_event = None
data_lines = []
event_error = False

def handle_event(event_name: str | None, payload_lines: list[str]) -> None:
    global event_error

    if not event_name:
        return

    payload_text = "\n".join(payload_lines).strip()
    payload = {}
    if payload_text:
        try:
            payload = json.loads(payload_text)
        except json.JSONDecodeError:
            payload = {}

    if event_name == "start":
        print("Streaming reply:", flush=True)
        return

    if event_name == "chunk":
        text = payload.get("text", "")
        print(text, end="", flush=True)
        return

    if event_name == "complete":
        response = payload.get("response", {})
        metadata = response.get("metadata", {})
        print(flush=True)
        print(flush=True)
        print("Completed.", flush=True)
        if metadata:
            stop_reason = metadata.get("stopReason")
            duration_ms = metadata.get("durationMs")
            total_tokens = metadata.get("totalTokens")
            summary_parts = []
            if stop_reason:
                summary_parts.append(f"stopReason={stop_reason}")
            if duration_ms is not None:
                summary_parts.append(f"durationMs={duration_ms}")
            if total_tokens is not None:
                summary_parts.append(f"totalTokens={total_tokens}")
            if summary_parts:
                print("Metadata: " + ", ".join(summary_parts), flush=True)
        return

    if event_name == "error":
        message = payload.get("message", "Streaming failed")
        print(flush=True)
        print(flush=True)
        print(f"Error: {message}", file=sys.stderr, flush=True)
        event_error = True
        sys.exit(1)

def process_stream(stream) -> None:
    global current_event, data_lines

    for raw_line in stream:
        line = raw_line.rstrip("\n")
        if line.startswith("event:"):
            current_event = line[len("event:"):].strip()
            continue
        if line.startswith("data:"):
            data_lines.append(line[len("data:"):].strip())
            continue
        if line == "":
            handle_event(current_event, data_lines)
            current_event = None
            data_lines = []

curl_command = [
    "curl",
    "--no-buffer",
    "--silent",
    "--show-error",
    "--request",
    "POST",
    "--header",
    "Accept: text/event-stream",
    "--header",
    "Content-Type: application/json",
    "--data",
    json.dumps({"text": os.environ["MESSAGE_TEXT"]}),
    f'{os.environ["BASE_URL"]}/api/sessions/{os.environ["SESSION_ID"]}/messages/stream',
]

process = subprocess.Popen(
    curl_command,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1,
)

assert process.stdout is not None
assert process.stderr is not None

try:
    process_stream(process.stdout)
finally:
    process.stdout.close()

stderr_output = process.stderr.read()
return_code = process.wait()

if current_event or data_lines:
    handle_event(current_event, data_lines)

if return_code != 0 and not event_error:
    if stderr_output:
        print(stderr_output, file=sys.stderr, end="")
    sys.exit(return_code)
PY
  parser_status=$?
fi

set -e

if [[ "${OUTPUT_MODE}" == "raw" ]]; then
  curl_status=${curl_status:-0}
  if [[ "${curl_status}" -ne 0 ]]; then
    exit "${curl_status}"
  fi
else
  parser_status=${parser_status:-0}
  if [[ "${parser_status}" -ne 0 ]]; then
    exit "${parser_status}"
  fi
fi
