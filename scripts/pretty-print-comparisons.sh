#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPARISONS_DIR="${COMPARISONS_DIR:-${REPO_ROOT}/data/comparisons}"
OUTPUT_MODE="rendered"

usage() {
  cat <<EOF
Usage:
  ./scripts/pretty-print-comparisons.sh
  ./scripts/pretty-print-comparisons.sh --raw
  COMPARISONS_DIR=data/comparisons ./scripts/pretty-print-comparisons.sh

What it does:
  Prints every JSON comparison file found in the comparisons directory.
  Default output is a rendered comparison view with prompt, replies, and metadata.

Optional environment variables:
  COMPARISONS_DIR Directory containing comparison JSON files
                  Default: <repo-root>/data/comparisons

Options:
  --raw           Show pretty-printed raw JSON instead of rendered comparison text
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

for file in "${files[@]}"; do
  echo "== ${file} =="

  if [[ "${OUTPUT_MODE}" == "raw" ]]; then
    python3 -m json.tool "${file}"
    echo
    continue
  fi

  python3 - "${file}" <<'PY'
import json
import re
import sys
from pathlib import Path

file_path = Path(sys.argv[1])

with file_path.open("r", encoding="utf-8") as handle:
    comparison = json.load(handle)

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

def format_inference_config(inference_config: dict | None) -> str:
    if not inference_config:
        return "app defaults"

    parts = []
    temperature = inference_config.get("temperature")
    top_p = inference_config.get("topP")
    max_tokens = inference_config.get("maxTokens")

    if temperature is not None:
        parts.append(f"temperature={temperature}")
    if top_p is not None:
        parts.append(f"topP={top_p}")
    if max_tokens is not None:
        parts.append(f"maxTokens={max_tokens}")

    return ", ".join(parts) if parts else "app defaults"

def print_model_result(label: str, result: dict) -> None:
    model_id = result.get("modelId", "-")
    reply = result.get("reply", "")
    metadata = result.get("metadata") or {}

    print(label)
    print("-" * len(label))
    print(f"modelId: {model_id}")
    print("reply:")
    print(normalize_markdown(reply))
    print()
    print_metadata(metadata)
    if metadata:
        print()

def print_summary(summary: dict) -> None:
    if not summary:
        return

    print("summary:")
    faster_model = summary.get("fasterModel")
    duration_diff = summary.get("durationDifferenceMs")
    lower_token_model = summary.get("lowerTokenModel")
    token_diff = summary.get("tokenDifference")
    shorter_reply_model = summary.get("shorterReplyModel")
    longer_reply_model = summary.get("longerReplyModel")
    reply_length_diff = summary.get("replyLengthDifference")
    generated_by_model = summary.get("generatedByModelId")
    key_differences = summary.get("keyDifferences")

    if faster_model:
        if faster_model == "tie":
            print("  fasterModel: tie")
        elif duration_diff is not None:
            print(f"  fasterModel: {faster_model} ({duration_diff} ms faster)")
        else:
            print(f"  fasterModel: {faster_model}")

    if lower_token_model:
        if lower_token_model == "tie":
            print("  lowerTokenModel: tie")
        elif token_diff is not None:
            print(f"  lowerTokenModel: {lower_token_model} ({token_diff} fewer tokens)")
        else:
            print(f"  lowerTokenModel: {lower_token_model}")

    if shorter_reply_model:
        if shorter_reply_model == "tie":
            print("  shorterReplyModel: tie")
        elif reply_length_diff is not None:
            print(f"  shorterReplyModel: {shorter_reply_model} ({reply_length_diff} chars shorter)")
        else:
            print(f"  shorterReplyModel: {shorter_reply_model}")

    if longer_reply_model and longer_reply_model != "tie":
        print(f"  longerReplyModel: {longer_reply_model}")

    if generated_by_model:
        print(f"  generatedByModelId: {generated_by_model}")

    if key_differences:
        print("  keyDifferences:")
        for line in normalize_markdown(key_differences).splitlines():
            print(f"    {line}")

def print_setup(comparison: dict) -> None:
    system_prompt_a = comparison.get("systemPromptA")
    system_prompt_b = comparison.get("systemPromptB")
    inference_a = comparison.get("inferenceConfigA")
    inference_b = comparison.get("inferenceConfigB")
    legacy_system_prompt = comparison.get("systemPrompt")

    if not any([system_prompt_a, system_prompt_b, inference_a, inference_b, legacy_system_prompt]):
        return

    print("setup:")

    if system_prompt_a or inference_a:
        print("  Model A")
        if system_prompt_a:
            print(f"    systemPrompt: {normalize_markdown(system_prompt_a)}")
        print(f"    inference: {format_inference_config(inference_a)}")

    if system_prompt_b or inference_b:
        print("  Model B")
        if system_prompt_b:
            print(f"    systemPrompt: {normalize_markdown(system_prompt_b)}")
        print(f"    inference: {format_inference_config(inference_b)}")

    if not (system_prompt_a or system_prompt_b or inference_a or inference_b) and legacy_system_prompt:
        print(f"  systemPrompt: {normalize_markdown(legacy_system_prompt)}")

print(f"comparisonId: {comparison.get('comparisonId', '-')}")
print(f"createdAt: {comparison.get('createdAt', '-')}")
print("prompt:")
print(normalize_markdown(comparison.get("prompt", "")))
print()

print_setup(comparison)
if any([
    comparison.get("systemPromptA"),
    comparison.get("systemPromptB"),
    comparison.get("inferenceConfigA"),
    comparison.get("inferenceConfigB"),
    comparison.get("systemPrompt"),
]):
    print()

print_summary(comparison.get("summary") or {})
if comparison.get("summary"):
    print()

print_model_result("Model A", comparison.get("modelA") or {})
print_model_result("Model B", comparison.get("modelB") or {})
PY
  echo
done
