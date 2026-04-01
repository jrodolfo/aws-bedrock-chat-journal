#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GRADLE_TASKS="${GRADLE_TASKS:-test build}"

usage() {
  cat <<EOF
Usage:
  ./scripts/build-and-test.sh
  GRADLE_TASKS="clean test build" ./scripts/build-and-test.sh

What it does:
  Runs the configured Gradle verification/build tasks from the repository root.

Optional environment variables:
  GRADLE_TASKS    Gradle tasks to execute
                  Default: test build

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

cd "${REPO_ROOT}"
exec "${REPO_ROOT}/gradlew" ${GRADLE_TASKS}
