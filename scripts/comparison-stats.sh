#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPARISONS_DIR="${COMPARISONS_DIR:-${REPO_ROOT}/data/comparisons}"

usage() {
  cat <<EOF
Usage:
  ./scripts/comparison-stats.sh
  COMPARISONS_DIR=data/comparisons ./scripts/comparison-stats.sh

What it does:
  Reads saved comparison reports and prints aggregate stats such as:
  - total comparison count
  - how often each model appears
  - average duration per model
  - average total tokens per model
  - how often each model was faster
  - how often each model used fewer tokens

Optional environment variables:
  COMPARISONS_DIR Directory containing comparison JSON files
                  Default: <repo-root>/data/comparisons

Notes:
  - Stats depend on the metadata available in each saved comparison report.
  - Missing duration or token fields are skipped instead of causing failures.

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
from collections import defaultdict
from pathlib import Path

comparison_count = 0
usage_count = defaultdict(int)
duration_totals = defaultdict(float)
duration_counts = defaultdict(int)
token_totals = defaultdict(float)
token_counts = defaultdict(int)
faster_counts = defaultdict(int)
lower_token_counts = defaultdict(int)
models_seen = set()

def add_metadata_stats(model_id: str, metadata: dict) -> None:
    duration = metadata.get("durationMs")
    total_tokens = metadata.get("totalTokens")

    if duration is not None:
        duration_totals[model_id] += float(duration)
        duration_counts[model_id] += 1

    if total_tokens is not None:
        token_totals[model_id] += float(total_tokens)
        token_counts[model_id] += 1

for file_name in sys.argv[1:]:
    path = Path(file_name)
    with path.open("r", encoding="utf-8") as handle:
        comparison = json.load(handle)

    comparison_count += 1

    model_a = comparison.get("modelA") or {}
    model_b = comparison.get("modelB") or {}
    model_a_id = model_a.get("modelId")
    model_b_id = model_b.get("modelId")

    if model_a_id:
        usage_count[model_a_id] += 1
        models_seen.add(model_a_id)
        add_metadata_stats(model_a_id, model_a.get("metadata") or {})

    if model_b_id:
        usage_count[model_b_id] += 1
        models_seen.add(model_b_id)
        add_metadata_stats(model_b_id, model_b.get("metadata") or {})

    summary = comparison.get("summary") or {}
    faster_model = summary.get("fasterModel")
    lower_token_model = summary.get("lowerTokenModel")

    if faster_model and faster_model != "tie":
        faster_counts[faster_model] += 1

    if lower_token_model and lower_token_model != "tie":
        lower_token_counts[lower_token_model] += 1

def average(total_map: dict, count_map: dict, key: str) -> str:
    count = count_map.get(key, 0)
    if count == 0:
        return "-"
    return f"{total_map[key] / count:.1f}"

print("overall:")
print(f"  comparisons: {comparison_count}")
print(f"  distinctModels: {len(models_seen)}")
print()

print("model usage:")
for model_id in sorted(models_seen):
    print(f"  {model_id}: {usage_count[model_id]}")
print()

print("performance summary:")
for model_id in sorted(models_seen):
    avg_duration = average(duration_totals, duration_counts, model_id)
    faster = faster_counts.get(model_id, 0)
    print(f"  {model_id}: avgDurationMs={avg_duration}, fasterCount={faster}")
print()

print("token summary:")
for model_id in sorted(models_seen):
    avg_tokens = average(token_totals, token_counts, model_id)
    fewer_tokens = lower_token_counts.get(model_id, 0)
    print(f"  {model_id}: avgTotalTokens={avg_tokens}, lowerTokenCount={fewer_tokens}")
PY
