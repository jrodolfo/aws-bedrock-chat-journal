#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_ID="${SESSION_ID:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-Explain the Amazon Bedrock Converse API using streaming.}"
OUTPUT_MODE="pretty"
SHOW_METADATA="${SHOW_METADATA:-true}"

usage() {
  cat <<EOF
Usage:
  ./scripts/stream-message.sh <session-id>
  ./scripts/stream-message.sh <session-id> --raw
  ./scripts/stream-message.sh <session-id> "Explain streaming."
  SESSION_ID=<session-id> ./scripts/stream-message.sh
  SESSION_ID=<session-id> ./scripts/stream-message.sh --raw
  SESSION_ID=<session-id> MESSAGE_TEXT="Explain streaming." ./scripts/stream-message.sh
  BASE_URL=http://localhost:8080 SESSION_ID=<session-id> ./scripts/stream-message.sh

What it does:
  1. Sends one user message to the streaming endpoint
  2. Prints streamed assistant text as it arrives
  3. Persists the final assistant reply only after successful completion

Positional arguments:
  session-id      Existing session ID stored by the application
  message-text    Optional user message sent to the existing session

Optional environment variables:
  SESSION_ID      Existing session ID stored by the application when no positional session-id is provided

  BASE_URL        API base URL
                  Default: http://localhost:8080

  MESSAGE_TEXT    User message sent to the existing session when no positional message-text is provided
                  Default: Explain the Amazon Bedrock Converse API using streaming.

  SHOW_METADATA   true or false
                  Default: true

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
    --*)
      echo "Unknown option: $1" >&2
      echo >&2
      usage >&2
      exit 1
      ;;
    *)
      break
      ;;
  esac
done

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

print_request_failure_hint() {
  echo >&2
  echo "Request failed." >&2
  echo "Is the app running at ${BASE_URL}?" >&2
  echo "Try ./scripts/run-local.sh" >&2
}

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
  BASE_URL="${BASE_URL}" SESSION_ID="${SESSION_ID}" MESSAGE_TEXT="${MESSAGE_TEXT}" SHOW_METADATA="${SHOW_METADATA}" run_python - <<'PY'
import json
import os
import re
import subprocess
import sys

current_event = None
data_lines = []
event_error = False
pending_text = ""
show_metadata = os.environ.get("SHOW_METADATA", "true").lower() in {"true", "on", "yes", "1"}

def normalize_markdown(text: str) -> str:
    text = text.replace("*", "")
    text = text.replace("_", "")
    text = text.replace("`", "")
    text = re.sub(r"(^|\n)\s{0,3}#{1,6}\s*", r"\1", text)
    return text

def render_text(text: str, final: bool = False) -> None:
    global pending_text

    combined = pending_text + text

    if final:
        pending_text = ""
        if combined:
            print(normalize_markdown(combined), end="", flush=True)
        return

    if len(combined) <= 64:
        pending_text = combined
        return

    safe_text = combined[:-64]
    pending_text = combined[-64:]
    print(normalize_markdown(safe_text), end="", flush=True)

def print_metadata_summary(metadata: dict) -> None:
    if not metadata:
        return

    requested_at = metadata.get("requestedAt")
    responded_at = metadata.get("respondedAt")
    duration_ms = metadata.get("durationMs")
    total_tokens = metadata.get("totalTokens")
    input_tokens = metadata.get("inputTokens")
    output_tokens = metadata.get("outputTokens")
    stop_reason = metadata.get("stopReason")

    if requested_at:
        print(f"Requested at: {requested_at}", flush=True)
    if responded_at:
        print(f"Responded at: {responded_at}", flush=True)
    if duration_ms is not None:
        print(f"Duration: {duration_ms} ms", flush=True)
    if total_tokens is not None:
        print(f"Total tokens: {total_tokens}", flush=True)
    elif input_tokens is not None or output_tokens is not None:
        token_parts = []
        if input_tokens is not None:
            token_parts.append(f"input={input_tokens}")
        if output_tokens is not None:
            token_parts.append(f"output={output_tokens}")
        print("Tokens: " + ", ".join(token_parts), flush=True)
    if stop_reason:
        print(f"Stop reason: {stop_reason}", flush=True)

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
        render_text(text)
        return

    if event_name == "complete":
        render_text("", final=True)
        response = payload.get("response", {})
        metadata = response.get("metadata", {})
        print(flush=True)
        print(flush=True)
        print("Completed.", flush=True)
        if show_metadata:
            print_metadata_summary(metadata)
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
    print(file=sys.stderr)
    print("Request failed.", file=sys.stderr)
    print(f"Is the app running at {os.environ['BASE_URL']}?", file=sys.stderr)
    print("Try ./scripts/run-local.sh", file=sys.stderr)
    sys.exit(return_code)
PY
  parser_status=$?
fi

set -e

if [[ "${OUTPUT_MODE}" == "raw" ]]; then
  curl_status=${curl_status:-0}
  if [[ "${curl_status}" -ne 0 ]]; then
    print_request_failure_hint
    exit "${curl_status}"
  fi
else
  parser_status=${parser_status:-0}
  if [[ "${parser_status}" -ne 0 ]]; then
    exit "${parser_status}"
  fi
fi
