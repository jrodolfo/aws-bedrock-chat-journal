#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPARISONS_DIR="${COMPARISONS_DIR:-${REPO_ROOT}/data/comparisons}"

usage() {
  cat <<EOF
Usage:
  ./scripts/list-comparisons.sh
  COMPARISONS_DIR=data/comparisons ./scripts/list-comparisons.sh

What it does:
  Lists saved comparison reports with file name, creation time, compared models, and a short prompt preview.

Optional environment variables:
  COMPARISONS_DIR Directory containing comparison JSON files
                  Default: <repo-root>/data/comparisons

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! -d "${COMPARISONS_DIR}" ]]; then
  echo "Comparisons directory not found: ${COMPARISONS_DIR}" >&2
  exit 1
fi

shopt -s nullglob
files=("${COMPARISONS_DIR}"/*.json)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No comparison JSON files found in ${COMPARISONS_DIR}"
  exit 0
fi

python3 - "${files[@]}" <<'PY'
import json
import sys
from pathlib import Path

def preview(text: str | None, max_len: int = 72) -> str:
    if not text:
        return "-"
    compact = " ".join(text.split())
    return compact if len(compact) <= max_len else compact[: max_len - 3] + "..."

for file_name in sys.argv[1:]:
    path = Path(file_name)
    with path.open("r", encoding="utf-8") as handle:
        comparison = json.load(handle)

    model_a = (comparison.get("modelA") or {}).get("modelId", "-")
    model_b = (comparison.get("modelB") or {}).get("modelId", "-")
    print(f"file: {path.name}")
    print(f"comparisonId: {comparison.get('comparisonId', '-')}")
    print(f"createdAt: {comparison.get('createdAt', '-')}")
    print(f"modelA: {model_a}")
    print(f"modelB: {model_b}")
    print(f"prompt: {preview(comparison.get('prompt'))}")
    print()
PY
