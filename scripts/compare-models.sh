#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPARISONS_DIR="${COMPARISONS_DIR:-${REPO_ROOT}/data/comparisons}"
MODEL_A="${MODEL_A:-}"
MODEL_B="${MODEL_B:-}"
SYSTEM_PROMPT="${SYSTEM_PROMPT:-You are a helpful AWS study assistant.}"
MESSAGE_TEXT="${MESSAGE_TEXT:-}"
SESSION_A=""
SESSION_B=""

usage() {
  cat <<EOF
Usage:
  MODEL_A=<model-id> MODEL_B=<model-id> MESSAGE_TEXT="Compare these models." ./scripts/compare-models.sh
  MODEL_A=amazon.nova-lite-v1:0 MODEL_B=amazon.nova-pro-v1:0 MESSAGE_TEXT="Explain Converse API." ./scripts/compare-models.sh
  BASE_URL=http://localhost:8080 COMPARISONS_DIR=data/comparisons MODEL_A=<model-a> MODEL_B=<model-b> MESSAGE_TEXT="Prompt" ./scripts/compare-models.sh

What it does:
  1. Creates two temporary sessions, one for each model
  2. Sends the same prompt to both models
  3. Saves a comparison report under data/comparisons
  4. Prints both replies and metadata
  5. Deletes the temporary sessions

Required environment variables:
  MODEL_A         First Bedrock model ID
  MODEL_B         Second Bedrock model ID
  MESSAGE_TEXT    Prompt sent to both temporary sessions

Optional environment variables:
  BASE_URL        API base URL
                  Default: http://localhost:8080

  SYSTEM_PROMPT   System prompt used for both temporary sessions
                  Default: You are a helpful AWS study assistant.

  COMPARISONS_DIR Directory where comparison JSON reports are saved
                  Default: <repo-root>/data/comparisons

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "${MODEL_A}" ]]; then
  echo "MODEL_A is required." >&2
  echo >&2
  usage >&2
  exit 1
fi

if [[ -z "${MODEL_B}" ]]; then
  echo "MODEL_B is required." >&2
  echo >&2
  usage >&2
  exit 1
fi

if [[ -z "${MESSAGE_TEXT}" ]]; then
  echo "MESSAGE_TEXT is required." >&2
  echo >&2
  usage >&2
  exit 1
fi

mkdir -p "${COMPARISONS_DIR}"

print_request_failure_hint() {
  echo >&2
  echo "Request failed." >&2
  echo "Is the app running at ${BASE_URL}?" >&2
  echo "Try ./scripts/run-local.sh" >&2
}

run_curl() {
  if ! curl --silent --show-error "$@"; then
    print_request_failure_hint
    return 1
  fi
}

build_create_payload() {
  MODEL_ID="$1" SYSTEM_PROMPT="${SYSTEM_PROMPT}" python3 - <<'PY'
import json
import os

print(json.dumps({
    "modelId": os.environ["MODEL_ID"],
    "systemPrompt": os.environ["SYSTEM_PROMPT"],
}))
PY
}

build_message_payload() {
  MESSAGE_TEXT="${MESSAGE_TEXT}" python3 - <<'PY'
import json
import os

print(json.dumps({"text": os.environ["MESSAGE_TEXT"]}))
PY
}

create_temp_session() {
  local model_id="$1"
  local create_response

  create_response="$(
    run_curl \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_create_payload "${model_id}")" \
      "${BASE_URL}/api/sessions"
  )" || return 1

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
}

send_message() {
  local session_id="$1"

  run_curl \
    --request POST \
    --header "Content-Type: application/json" \
    --data "$(build_message_payload)" \
    "${BASE_URL}/api/sessions/${session_id}/messages"
}

delete_temp_session() {
  local session_id="$1"
  if [[ -z "${session_id}" ]]; then
    return
  fi

  curl --silent --show-error \
    --request DELETE \
    "${BASE_URL}/api/sessions/${session_id}" >/dev/null 2>&1 || true
}

cleanup() {
  delete_temp_session "${SESSION_A}"
  delete_temp_session "${SESSION_B}"
}

trap cleanup EXIT

SESSION_A="$(create_temp_session "${MODEL_A}")"
SESSION_B="$(create_temp_session "${MODEL_B}")"

RESPONSE_A="$(send_message "${SESSION_A}")"
RESPONSE_B="$(send_message "${SESSION_B}")"

REPORT_PATH="$(
  MODEL_A="${MODEL_A}" MODEL_B="${MODEL_B}" SYSTEM_PROMPT="${SYSTEM_PROMPT}" MESSAGE_TEXT="${MESSAGE_TEXT}" \
  RESPONSE_A="${RESPONSE_A}" RESPONSE_B="${RESPONSE_B}" COMPARISONS_DIR="${COMPARISONS_DIR}" python3 - <<'PY'
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path

comparison_id = str(uuid.uuid4())
created_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
comparisons_dir = Path(os.environ["COMPARISONS_DIR"])
report_path = comparisons_dir / f"{created_at.replace(':', '').replace('-', '')}_{comparison_id}.json"

report = {
    "comparisonId": comparison_id,
    "createdAt": created_at,
    "prompt": os.environ["MESSAGE_TEXT"],
    "systemPrompt": os.environ["SYSTEM_PROMPT"],
    "modelA": {
        "modelId": os.environ["MODEL_A"],
        **json.loads(os.environ["RESPONSE_A"]),
    },
    "modelB": {
        "modelId": os.environ["MODEL_B"],
        **json.loads(os.environ["RESPONSE_B"]),
    },
}

with report_path.open("w", encoding="utf-8") as handle:
    json.dump(report, handle, indent=2)
    handle.write("\n")

print(report_path)
PY
)"

echo "Comparison report: ${REPORT_PATH}"
echo
echo "Prompt:"
echo "${MESSAGE_TEXT}"
echo

RESPONSE_A="${RESPONSE_A}" RESPONSE_B="${RESPONSE_B}" MODEL_A="${MODEL_A}" MODEL_B="${MODEL_B}" python3 - <<'PY'
import json
import os

def print_model_result(label: str, model_id: str, response_text: str) -> None:
    response = json.loads(response_text)
    metadata = response.get("metadata") or {}

    print(f"{label}: {model_id}")
    print(response.get("reply", ""))
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
    print()

print_model_result("Model A", os.environ["MODEL_A"], os.environ["RESPONSE_A"])
print_model_result("Model B", os.environ["MODEL_B"], os.environ["RESPONSE_B"])
PY
