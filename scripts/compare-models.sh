#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPARISONS_DIR="${COMPARISONS_DIR:-${REPO_ROOT}/data/comparisons}"
MODEL_A="${MODEL_A:-}"
MODEL_B="${MODEL_B:-}"
SUMMARY_MODEL="${SUMMARY_MODEL:-}"
SYSTEM_PROMPT="${SYSTEM_PROMPT:-You are a helpful AWS study assistant.}"
PROMPT_PRESET="${PROMPT_PRESET:-}"
PROMPT_PRESET_A="${PROMPT_PRESET_A:-}"
PROMPT_PRESET_B="${PROMPT_PRESET_B:-}"
SYSTEM_PROMPT_A="${SYSTEM_PROMPT_A:-}"
SYSTEM_PROMPT_B="${SYSTEM_PROMPT_B:-}"
TEMPERATURE_A="${TEMPERATURE_A:-}"
TEMPERATURE_B="${TEMPERATURE_B:-}"
TOP_P_A="${TOP_P_A:-}"
TOP_P_B="${TOP_P_B:-}"
MAX_TOKENS_A="${MAX_TOKENS_A:-}"
MAX_TOKENS_B="${MAX_TOKENS_B:-}"
MESSAGE_TEXT="${MESSAGE_TEXT:-}"
SESSION_A=""
SESSION_B=""
SESSION_SUMMARY=""

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

  SYSTEM_PROMPT   Shared system prompt used when side-specific prompts are not set
                  Default: You are a helpful AWS study assistant.

  PROMPT_PRESET   Shared named prompt preset
                  Examples: exam-tutor, quiz-me, bedrock-accuracy, compare-services

  PROMPT_PRESET_A Prompt preset override for model A
                  Default: PROMPT_PRESET

  PROMPT_PRESET_B Prompt preset override for model B
                  Default: PROMPT_PRESET

  SYSTEM_PROMPT_A Explicit system prompt override for model A
                  Overrides PROMPT_PRESET_A and PROMPT_PRESET

  SYSTEM_PROMPT_B Explicit system prompt override for model B
                  Overrides PROMPT_PRESET_B and PROMPT_PRESET

  TEMPERATURE_A   Inference override for model A temperature
  TEMPERATURE_B   Inference override for model B temperature
  TOP_P_A         Inference override for model A topP
  TOP_P_B         Inference override for model B topP
  MAX_TOKENS_A    Inference override for model A maxTokens
  MAX_TOKENS_B    Inference override for model B maxTokens

  SUMMARY_MODEL   Optional model used to generate a semantic key-differences summary
                  Default: MODEL_B

  Inference means the generation settings that shape the reply, such as:
    - temperature  controls randomness
    - topP         narrows token selection by probability
    - maxTokens    limits response length

  COMPARISONS_DIR Directory where comparison JSON reports are saved
                  Default: <repo-root>/data/comparisons

Options:
  -h, --help      Show this help message
EOF

  cat <<'EOF'

Prompt preset precedence:
  1. SYSTEM_PROMPT_A or SYSTEM_PROMPT_B
  2. PROMPT_PRESET_A or PROMPT_PRESET_B
  3. shared PROMPT_PRESET
  4. shared SYSTEM_PROMPT

Available prompt presets:
  exam-tutor        Certification-style explanations, distinctions, and tradeoffs
  quiz-me           One question at a time, then brief grading and correction
  bedrock-accuracy  Conservative wording that avoids invented AWS details
  compare-services  Structured side-by-side comparisons of AWS options
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

resolve_prompt_preset() {
  case "$1" in
    exam-tutor)
      cat <<'EOF'
You are an AWS certification study tutor focused on Amazon Bedrock and related AWS AI services. Give concise answers first, then expand only when helpful. Emphasize distinctions, tradeoffs, and likely exam traps. When comparing services, explain when to use each one and why.
EOF
      ;;
    quiz-me)
      cat <<'EOF'
You are an AWS certification quiz coach focused on Amazon Bedrock and generative AI topics. Ask one question at a time. Wait for the user's answer. Then grade it briefly, explain what was correct or missing, and ask the next question.
EOF
      ;;
    bedrock-accuracy)
      cat <<'EOF'
You are an AWS Bedrock study assistant. Be conservative and accuracy-focused. Do not invent AWS API details. Separate facts from assumptions. If you are uncertain, say so clearly. Prefer careful wording over confident guesses, especially for service behavior, SDK details, and limits.
EOF
      ;;
    compare-services)
      cat <<'EOF'
You are an AWS study assistant that specializes in structured comparisons. When asked to compare services, models, or approaches, use a clear side-by-side format covering purpose, strengths, limitations, when to use each option, and common confusion points.
EOF
      ;;
    *)
      return 1
      ;;
  esac
}

resolve_prompt_side() {
  local side="$1"
  local shared_preset="${PROMPT_PRESET}"
  local side_preset=""
  local explicit_prompt=""

  case "${side}" in
    A)
      side_preset="${PROMPT_PRESET_A}"
      explicit_prompt="${SYSTEM_PROMPT_A}"
      ;;
    B)
      side_preset="${PROMPT_PRESET_B}"
      explicit_prompt="${SYSTEM_PROMPT_B}"
      ;;
    *)
      echo "Unknown comparison side: ${side}" >&2
      exit 1
      ;;
  esac

  if [[ -n "${explicit_prompt}" ]]; then
    printf '%s\n' "${explicit_prompt}"
    return 0
  fi

  if [[ -n "${side_preset}" ]]; then
    resolve_prompt_preset "${side_preset}" || {
      echo "Unknown prompt preset for side ${side}: ${side_preset}" >&2
      usage >&2
      exit 1
    }
    return 0
  fi

  if [[ -n "${shared_preset}" ]]; then
    resolve_prompt_preset "${shared_preset}" || {
      echo "Unknown shared PROMPT_PRESET: ${shared_preset}" >&2
      usage >&2
      exit 1
    }
    return 0
  fi

  printf '%s\n' "${SYSTEM_PROMPT}"
}

resolve_preset_name_side() {
  local side="$1"

  case "${side}" in
    A)
      if [[ -n "${SYSTEM_PROMPT_A}" ]]; then
        printf '%s' ""
      elif [[ -n "${PROMPT_PRESET_A}" ]]; then
        printf '%s' "${PROMPT_PRESET_A}"
      else
        printf '%s' "${PROMPT_PRESET}"
      fi
      ;;
    B)
      if [[ -n "${SYSTEM_PROMPT_B}" ]]; then
        printf '%s' ""
      elif [[ -n "${PROMPT_PRESET_B}" ]]; then
        printf '%s' "${PROMPT_PRESET_B}"
      else
        printf '%s' "${PROMPT_PRESET}"
      fi
      ;;
  esac
}

build_create_payload() {
  MODEL_ID="$1" \
  SESSION_SYSTEM_PROMPT="$2" \
  SESSION_TEMPERATURE="$3" \
  SESSION_TOP_P="$4" \
  SESSION_MAX_TOKENS="$5" \
  python3 - <<'PY'
import json
import os

payload = {
    "modelId": os.environ["MODEL_ID"],
    "systemPrompt": os.environ["SESSION_SYSTEM_PROMPT"],
}

inference_config = {}
temperature = os.environ.get("SESSION_TEMPERATURE", "").strip()
top_p = os.environ.get("SESSION_TOP_P", "").strip()
max_tokens = os.environ.get("SESSION_MAX_TOKENS", "").strip()

if temperature:
    inference_config["temperature"] = float(temperature)
if top_p:
    inference_config["topP"] = float(top_p)
if max_tokens:
    inference_config["maxTokens"] = int(max_tokens)

if inference_config:
    payload["inferenceConfig"] = inference_config

print(json.dumps(payload))
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
  local system_prompt="$2"
  local temperature="$3"
  local top_p="$4"
  local max_tokens="$5"
  local create_response

  create_response="$(
    run_curl \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_create_payload "${model_id}" "${system_prompt}" "${temperature}" "${top_p}" "${max_tokens}")" \
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
  delete_temp_session "${SESSION_SUMMARY}"
}

trap cleanup EXIT

if [[ -z "${SUMMARY_MODEL}" ]]; then
  SUMMARY_MODEL="${MODEL_B}"
fi

EFFECTIVE_PROMPT_A="$(resolve_prompt_side "A")"
EFFECTIVE_PROMPT_B="$(resolve_prompt_side "B")"
EFFECTIVE_PRESET_A="$(resolve_preset_name_side "A")"
EFFECTIVE_PRESET_B="$(resolve_preset_name_side "B")"

SESSION_A="$(create_temp_session "${MODEL_A}" "${EFFECTIVE_PROMPT_A}" "${TEMPERATURE_A}" "${TOP_P_A}" "${MAX_TOKENS_A}")"
SESSION_B="$(create_temp_session "${MODEL_B}" "${EFFECTIVE_PROMPT_B}" "${TEMPERATURE_B}" "${TOP_P_B}" "${MAX_TOKENS_B}")"

RESPONSE_A="$(send_message "${SESSION_A}")"
RESPONSE_B="$(send_message "${SESSION_B}")"
SUMMARY_RESPONSE=""

build_summary_prompt() {
  MESSAGE_TEXT="${MESSAGE_TEXT}" RESPONSE_A="${RESPONSE_A}" RESPONSE_B="${RESPONSE_B}" MODEL_A="${MODEL_A}" MODEL_B="${MODEL_B}" python3 - <<'PY'
import json
import os

response_a = json.loads(os.environ["RESPONSE_A"])
response_b = json.loads(os.environ["RESPONSE_B"])

reply_a = response_a.get("reply", "")
reply_b = response_b.get("reply", "")

prompt = f"""Compare these two Bedrock model replies to the same prompt.

Original prompt:
{os.environ.get('MESSAGE_TEXT', '')}

Model A: {os.environ['MODEL_A']}
Reply A:
{reply_a}

Model B: {os.environ['MODEL_B']}
Reply B:
{reply_b}

Write a short comparison that focuses on:
- style differences
- level of detail
- clarity
- likely usefulness for AWS exam study

Be concise. Do not invent facts beyond the two replies."""

print(prompt)
PY
}

build_summary_message_payload() {
  SUMMARY_PROMPT="$1" python3 - <<'PY'
import json
import os

print(json.dumps({"text": os.environ["SUMMARY_PROMPT"]}))
PY
}

generate_semantic_summary() {
  local summary_prompt="$1"
  local summary_response

  SESSION_SUMMARY="$(create_temp_session "${SUMMARY_MODEL}" "You compare two Bedrock replies and summarize their differences." "" "" "")" || return 1

  summary_response="$(
    run_curl \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$(build_summary_message_payload "${summary_prompt}")" \
      "${BASE_URL}/api/sessions/${SESSION_SUMMARY}/messages"
  )" || return 1

  printf '%s' "${summary_response}"
}

SUMMARY_PROMPT="$(MESSAGE_TEXT="${MESSAGE_TEXT}" build_summary_prompt)"
if SUMMARY_RESPONSE="$(generate_semantic_summary "${SUMMARY_PROMPT}" 2>/dev/null)"; then
  :
else
  SUMMARY_RESPONSE=""
fi

REPORT_PATH="$(
  MODEL_A="${MODEL_A}" MODEL_B="${MODEL_B}" SUMMARY_MODEL="${SUMMARY_MODEL}" \
  SYSTEM_PROMPT_A="${EFFECTIVE_PROMPT_A}" SYSTEM_PROMPT_B="${EFFECTIVE_PROMPT_B}" \
  PROMPT_PRESET_A="${EFFECTIVE_PRESET_A}" PROMPT_PRESET_B="${EFFECTIVE_PRESET_B}" \
  TEMPERATURE_A="${TEMPERATURE_A}" TEMPERATURE_B="${TEMPERATURE_B}" \
  TOP_P_A="${TOP_P_A}" TOP_P_B="${TOP_P_B}" \
  MAX_TOKENS_A="${MAX_TOKENS_A}" MAX_TOKENS_B="${MAX_TOKENS_B}" \
  MESSAGE_TEXT="${MESSAGE_TEXT}" \
  RESPONSE_A="${RESPONSE_A}" RESPONSE_B="${RESPONSE_B}" SUMMARY_RESPONSE="${SUMMARY_RESPONSE}" COMPARISONS_DIR="${COMPARISONS_DIR}" python3 - <<'PY'
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
    "systemPromptA": os.environ["SYSTEM_PROMPT_A"],
    "systemPromptB": os.environ["SYSTEM_PROMPT_B"],
    "modelA": {
        "modelId": os.environ["MODEL_A"],
        **json.loads(os.environ["RESPONSE_A"]),
    },
    "modelB": {
        "modelId": os.environ["MODEL_B"],
        **json.loads(os.environ["RESPONSE_B"]),
    },
}

if os.environ.get("PROMPT_PRESET_A"):
    report["promptPresetA"] = os.environ["PROMPT_PRESET_A"]
if os.environ.get("PROMPT_PRESET_B"):
    report["promptPresetB"] = os.environ["PROMPT_PRESET_B"]

def parse_inference(prefix: str) -> dict:
    inference = {}

    temperature = os.environ.get(f"TEMPERATURE_{prefix}", "").strip()
    top_p = os.environ.get(f"TOP_P_{prefix}", "").strip()
    max_tokens = os.environ.get(f"MAX_TOKENS_{prefix}", "").strip()

    if temperature:
        inference["temperature"] = float(temperature)
    if top_p:
        inference["topP"] = float(top_p)
    if max_tokens:
        inference["maxTokens"] = int(max_tokens)

    return inference

inference_a = parse_inference("A")
inference_b = parse_inference("B")
if inference_a:
    report["inferenceConfigA"] = inference_a
if inference_b:
    report["inferenceConfigB"] = inference_b

def reply_length(reply: str | None) -> int:
    return len((reply or "").strip())

def summary_for_models(report: dict) -> dict:
    model_a = report["modelA"]
    model_b = report["modelB"]
    metadata_a = model_a.get("metadata") or {}
    metadata_b = model_b.get("metadata") or {}

    summary = {}

    duration_a = metadata_a.get("durationMs")
    duration_b = metadata_b.get("durationMs")
    if duration_a is not None and duration_b is not None:
        if duration_a < duration_b:
            summary["fasterModel"] = model_a.get("modelId")
        elif duration_b < duration_a:
            summary["fasterModel"] = model_b.get("modelId")
        else:
            summary["fasterModel"] = "tie"
        summary["durationDifferenceMs"] = abs(duration_a - duration_b)

    tokens_a = metadata_a.get("totalTokens")
    tokens_b = metadata_b.get("totalTokens")
    if tokens_a is not None and tokens_b is not None:
        if tokens_a < tokens_b:
            summary["lowerTokenModel"] = model_a.get("modelId")
        elif tokens_b < tokens_a:
            summary["lowerTokenModel"] = model_b.get("modelId")
        else:
            summary["lowerTokenModel"] = "tie"
        summary["tokenDifference"] = abs(tokens_a - tokens_b)

    length_a = reply_length(model_a.get("reply"))
    length_b = reply_length(model_b.get("reply"))
    if length_a < length_b:
        summary["shorterReplyModel"] = model_a.get("modelId")
        summary["longerReplyModel"] = model_b.get("modelId")
    elif length_b < length_a:
        summary["shorterReplyModel"] = model_b.get("modelId")
        summary["longerReplyModel"] = model_a.get("modelId")
    else:
        summary["shorterReplyModel"] = "tie"
        summary["longerReplyModel"] = "tie"
    summary["replyLengthDifference"] = abs(length_a - length_b)

    return summary

report["summary"] = summary_for_models(report)

if os.environ.get("SUMMARY_RESPONSE"):
    summary_response = json.loads(os.environ["SUMMARY_RESPONSE"])
    report["summary"]["generatedByModelId"] = os.environ["SUMMARY_MODEL"]
    report["summary"]["keyDifferences"] = summary_response.get("reply", "")

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

SYSTEM_PROMPT_A="${EFFECTIVE_PROMPT_A}" SYSTEM_PROMPT_B="${EFFECTIVE_PROMPT_B}" \
PROMPT_PRESET_A="${EFFECTIVE_PRESET_A}" PROMPT_PRESET_B="${EFFECTIVE_PRESET_B}" \
TEMPERATURE_A="${TEMPERATURE_A}" TEMPERATURE_B="${TEMPERATURE_B}" \
TOP_P_A="${TOP_P_A}" TOP_P_B="${TOP_P_B}" \
MAX_TOKENS_A="${MAX_TOKENS_A}" MAX_TOKENS_B="${MAX_TOKENS_B}" \
RESPONSE_A="${RESPONSE_A}" RESPONSE_B="${RESPONSE_B}" MODEL_A="${MODEL_A}" MODEL_B="${MODEL_B}" python3 - <<'PY'
import json
import os

response_a = json.loads(os.environ["RESPONSE_A"])
response_b = json.loads(os.environ["RESPONSE_B"])
summary_response = json.loads(os.environ["SUMMARY_RESPONSE"]) if os.environ.get("SUMMARY_RESPONSE") else None
metadata_a = response_a.get("metadata") or {}
metadata_b = response_b.get("metadata") or {}

duration_a = metadata_a.get("durationMs")
duration_b = metadata_b.get("durationMs")
tokens_a = metadata_a.get("totalTokens")
tokens_b = metadata_b.get("totalTokens")
length_a = len((response_a.get("reply") or "").strip())
length_b = len((response_b.get("reply") or "").strip())

def print_setup(label: str, model_id: str, prompt: str, suffix: str) -> None:
    print(f"{label} setup")
    print(f"  modelId: {model_id}")
    preset_name = os.environ.get(f"PROMPT_PRESET_{suffix}")
    if preset_name:
        print(f"  promptPreset: {preset_name}")
    print(f"  systemPrompt: {prompt}")

    parts = []
    temperature = os.environ.get(f"TEMPERATURE_{suffix}")
    top_p = os.environ.get(f"TOP_P_{suffix}")
    max_tokens = os.environ.get(f"MAX_TOKENS_{suffix}")
    if temperature:
        parts.append(f"temperature={temperature}")
    if top_p:
        parts.append(f"topP={top_p}")
    if max_tokens:
        parts.append(f"maxTokens={max_tokens}")

    if parts:
        print("  inference: " + ", ".join(parts))
    else:
        print("  inference: app defaults")

    print()

print_setup("Model A", os.environ["MODEL_A"], os.environ["SYSTEM_PROMPT_A"], "A")
print_setup("Model B", os.environ["MODEL_B"], os.environ["SYSTEM_PROMPT_B"], "B")

print("Summary")
print("-------")
if duration_a is not None and duration_b is not None:
    if duration_a < duration_b:
        print(f"Faster model: {os.environ['MODEL_A']} ({abs(duration_a - duration_b)} ms faster)")
    elif duration_b < duration_a:
        print(f"Faster model: {os.environ['MODEL_B']} ({abs(duration_a - duration_b)} ms faster)")
    else:
        print("Faster model: tie")

if tokens_a is not None and tokens_b is not None:
    if tokens_a < tokens_b:
        print(f"Lower token usage: {os.environ['MODEL_A']} ({abs(tokens_a - tokens_b)} fewer tokens)")
    elif tokens_b < tokens_a:
        print(f"Lower token usage: {os.environ['MODEL_B']} ({abs(tokens_a - tokens_b)} fewer tokens)")
    else:
        print("Lower token usage: tie")

if length_a < length_b:
    print(f"Shorter reply: {os.environ['MODEL_A']} ({abs(length_a - length_b)} chars shorter)")
elif length_b < length_a:
    print(f"Shorter reply: {os.environ['MODEL_B']} ({abs(length_a - length_b)} chars shorter)")
else:
    print("Shorter reply: tie")

if summary_response and summary_response.get("reply"):
    print()
    print(f"Key differences ({os.environ['SUMMARY_MODEL']}):")
    print(summary_response["reply"])

print()

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
