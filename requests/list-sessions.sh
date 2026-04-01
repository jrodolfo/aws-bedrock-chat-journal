#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_SESSIONS_DIR="${REPO_ROOT}/data/sessions"
SESSIONS_DIR="${SESSIONS_DIR:-${DEFAULT_SESSIONS_DIR}}"

usage() {
  cat <<EOF
Usage:
  ./requests/list-sessions.sh
  SESSIONS_DIR=data/sessions ./requests/list-sessions.sh

What it does:
  Lists stored session files with sessionId, modelId, message count, and a short system prompt preview.

Optional environment variables:
  SESSIONS_DIR    Directory containing session JSON files
                  Default: <repo-root>/data/sessions

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

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

python3 - "${files[@]}" <<'PY'
import json
import sys
from pathlib import Path

def preview(text: str | None, max_len: int = 60) -> str:
    if not text:
        return "-"
    compact = " ".join(text.split())
    return compact if len(compact) <= max_len else compact[: max_len - 3] + "..."

for file_name in sys.argv[1:]:
    path = Path(file_name)
    with path.open("r", encoding="utf-8") as handle:
        session = json.load(handle)

    messages = session.get("messages") or []
    print(f"file: {path.name}")
    print(f"sessionId: {session.get('sessionId', '-')}")
    print(f"modelId: {session.get('modelId', '-')}")
    print(f"messageCount: {len(messages)}")
    print(f"systemPrompt: {preview(session.get('systemPrompt'))}")
    print()
PY
