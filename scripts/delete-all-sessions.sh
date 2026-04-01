#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_SESSIONS_DIR="${REPO_ROOT}/data/sessions"
SESSIONS_DIR="${SESSIONS_DIR:-${DEFAULT_SESSIONS_DIR}}"
ASSUME_YES="false"

usage() {
  cat <<EOF
Usage:
  ./scripts/delete-all-sessions.sh
  ./scripts/delete-all-sessions.sh --yes
  SESSIONS_DIR=data/sessions ./scripts/delete-all-sessions.sh

What it does:
  1. Finds every session JSON file in the sessions directory
  2. Deletes those session files entirely
  3. Prompts for confirmation unless --yes is provided

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

echo "This will permanently delete ${#files[@]} session file(s) from ${SESSIONS_DIR}."

if [[ "${ASSUME_YES}" != "true" ]]; then
  printf "Type 'delete' to continue: "
  read -r confirmation

  if [[ "${confirmation}" != "delete" ]]; then
    echo "Aborted. No session files were deleted."
    exit 0
  fi
fi

delete_count=0

for file in "${files[@]}"; do
  rm "${file}"
  delete_count=$((delete_count + 1))
done

echo "Deleted ${delete_count} session file(s)."
