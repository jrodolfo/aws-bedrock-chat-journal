#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PORT="${PORT:-8080}"

usage() {
  cat <<EOF
Usage:
  ./scripts/run-local.sh
  PORT=8081 ./scripts/run-local.sh

What it does:
  Starts the Spring Boot application with Gradle using the selected local port.

Optional environment variables:
  PORT            Server port to use for this run
                  Default: 8080

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

cd "${REPO_ROOT}"
exec "${REPO_ROOT}/gradlew" bootRun --args="--server.port=${PORT}"
