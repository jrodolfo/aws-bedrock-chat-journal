#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"
PYTHON_BIN="$(find_python_bin)"
DEFAULT_SESSIONS_DIR="${REPO_ROOT}/data/sessions"
SESSIONS_DIR="${SESSIONS_DIR:-${DEFAULT_SESSIONS_DIR}}"
OUTPUT_MODE="rendered"

usage() {
  cat <<EOF
Usage:
  ./scripts/pretty-print-sessions.sh
  ./scripts/pretty-print-sessions.sh --raw
  SESSIONS_DIR=data/sessions ./scripts/pretty-print-sessions.sh

What it does:
  Prints every JSON session file found in the sessions directory.
  Default output is a rendered conversation view with decoded message text.

Optional environment variables:
  SESSIONS_DIR    Directory containing session JSON files
                  Default: <repo-root>/data/sessions

Options:
  --raw           Show pretty-printed raw JSON instead of rendered conversation text
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

if [[ ! -d "${SESSIONS_DIR}" ]]; then
  echo "Sessions directory not found: ${SESSIONS_DIR}" >&2
  exit 1
fi

shopt -s nullglob
files=("${SESSIONS_DIR}"/*.json)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No session JSON files found in ${SESSIONS_DIR}"
  exit 0
fi

for file in "${files[@]}"; do
  echo "== ${file} =="

  if [[ "${OUTPUT_MODE}" == "raw" ]]; then
    "${PYTHON_BIN}" -m json.tool "${file}"
    echo
    continue
  fi

  "${PYTHON_BIN}" - "${file}" <<'PY'
import json
import re
import sys
from pathlib import Path

file_path = Path(sys.argv[1])

with file_path.open("r", encoding="utf-8") as handle:
    session = json.load(handle)


def normalize_markdown(text: str) -> str:
    lines = text.splitlines()
    normalized_lines = []

    for line in lines:
        stripped = line.lstrip()

        if stripped.startswith("#"):
            line = re.sub(r"^\s{0,3}#{1,6}\s*", "", line)

        line = re.sub(r"\*\*(.*?)\*\*", r"\1", line)
        line = re.sub(r"__(.*?)__", r"\1", line)
        line = re.sub(r"`([^`]*)`", r"\1", line)

        normalized_lines.append(line)

    return "\n".join(normalized_lines)

def print_metadata(metadata: dict) -> None:
    if not metadata:
        return

    lines = []

    requested_at = metadata.get("requestedAt")
    responded_at = metadata.get("respondedAt")
    duration_ms = metadata.get("durationMs")
    input_tokens = metadata.get("inputTokens")
    output_tokens = metadata.get("outputTokens")
    total_tokens = metadata.get("totalTokens")
    stop_reason = metadata.get("stopReason")

    if requested_at:
        lines.append(f"requestedAt: {requested_at}")
    if responded_at:
        lines.append(f"respondedAt: {responded_at}")
    if duration_ms is not None:
        lines.append(f"durationMs: {duration_ms}")
    if total_tokens is not None:
        lines.append(f"totalTokens: {total_tokens}")
    elif input_tokens is not None or output_tokens is not None:
        token_parts = []
        if input_tokens is not None:
            token_parts.append(f"input={input_tokens}")
        if output_tokens is not None:
            token_parts.append(f"output={output_tokens}")
        lines.append("tokens: " + ", ".join(token_parts))
    if stop_reason:
        lines.append(f"stopReason: {stop_reason}")

    if not lines:
        return

    print("metadata:")
    for line in lines:
        print(f"  {line}")

print(f"sessionId: {session.get('sessionId', '')}")
print(f"modelId: {session.get('modelId', '')}")

system_prompt = session.get("systemPrompt")
if system_prompt:
    print("systemPrompt:")
    print(system_prompt)

messages = session.get("messages", [])
if not messages:
    print()
    print("No messages.")
    sys.exit(0)

for index, message in enumerate(messages, start=1):
    role = message.get("role", "unknown")
    print()
    print(f"[{index}] {role}")
    print("-" * (len(role) + len(str(index)) + 3))

    content_blocks = message.get("content", [])
    if not content_blocks:
        print("(no content)")
        continue

    for block in content_blocks:
        text = block.get("text")
        if text is None:
            continue
        print(normalize_markdown(text))
        print()

    print_metadata(message.get("metadata") or {})
    if message.get("metadata"):
        print()
PY
  echo
done
