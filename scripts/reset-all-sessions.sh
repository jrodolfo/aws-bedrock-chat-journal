#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"
DEFAULT_SESSIONS_DIR="${REPO_ROOT}/data/sessions"
SESSIONS_DIR="${SESSIONS_DIR:-${DEFAULT_SESSIONS_DIR}}"
ASSUME_YES="false"

usage() {
  cat <<EOF
Usage:
  ./scripts/reset-all-sessions.sh
  ./scripts/reset-all-sessions.sh --yes
  SESSIONS_DIR=data/sessions ./scripts/reset-all-sessions.sh

What it does:
  1. Finds every session JSON file in the sessions directory
  2. Clears only the messages array in each file
  3. Keeps sessionId, modelId, systemPrompt, and inferenceConfig
  4. Prompts for confirmation unless --yes is provided

Optional environment variables:
  SESSIONS_DIR    Directory containing session JSON files
                  Default: <repo-root>/data/sessions

Options:
  --yes           Skip the confirmation prompt
  -h, --help      Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes)
      ASSUME_YES="true"
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

echo "This will clear messages from ${#files[@]} session file(s) in ${SESSIONS_DIR}."
echo "Session files and metadata will be kept."

if [[ "${ASSUME_YES}" != "true" ]]; then
  printf "Type 'yes' to continue: "
  read -r confirmation

  if [[ "${confirmation}" != "yes" ]]; then
    echo "Aborted. No session files were changed."
    exit 0
  fi
fi

reset_count=0

for file in "${files[@]}"; do
  run_python - "${file}" <<'PY'
import json
import sys
from pathlib import Path

file_path = Path(sys.argv[1])

with file_path.open("r", encoding="utf-8") as handle:
    session = json.load(handle)

session["messages"] = []

with file_path.open("w", encoding="utf-8") as handle:
    json.dump(session, handle, indent=2)
    handle.write("\n")
PY
  reset_count=$((reset_count + 1))
done

echo "Reset ${reset_count} session file(s)."
