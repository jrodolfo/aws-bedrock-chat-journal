#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GRADLE_TASKS="${GRADLE_TASKS:-test build}"
REQUIRED_JAVA_MAJOR=25

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

require_java_25() {
  if ! command -v java >/dev/null 2>&1; then
    cat <<EOF
Error: Java ${REQUIRED_JAVA_MAJOR} is required, but 'java' is not on PATH.

Install Java ${REQUIRED_JAVA_MAJOR} and set JAVA_HOME accordingly.
On Amazon Linux 2023:
  sudo dnf install -y java-${REQUIRED_JAVA_MAJOR}-amazon-corretto-devel
  export JAVA_HOME=/usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-amazon-corretto.x86_64
  export PATH="\$JAVA_HOME/bin:\$PATH"
EOF
    exit 1
  fi

  local java_version_output java_version_line java_major
  java_version_output="$(java -version 2>&1)"
  java_version_line="$(grep -m1 'version "' <<<"${java_version_output}" || true)"
  java_major="$(sed -nE 's/.*version "([0-9]+).*/\1/p' <<<"${java_version_line}")"

  if [[ -z "${java_major}" ]]; then
    cat <<EOF
Error: Unable to determine the installed Java version from:
${java_version_output}

Java ${REQUIRED_JAVA_MAJOR} is required for this project.
EOF
    exit 1
  fi

  if [[ "${java_major}" != "${REQUIRED_JAVA_MAJOR}" ]]; then
    cat <<EOF
Error: Java ${REQUIRED_JAVA_MAJOR} is required, but found Java ${java_major}.
Detected:
  ${java_version_line}

Set JAVA_HOME to a Java ${REQUIRED_JAVA_MAJOR} installation and put it first on PATH.
On Amazon Linux 2023:
  sudo dnf install -y java-${REQUIRED_JAVA_MAJOR}-amazon-corretto-devel
  export JAVA_HOME=/usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-amazon-corretto.x86_64
  export PATH="\$JAVA_HOME/bin:\$PATH"
EOF
    exit 1
  fi
}

require_java_25

cd "${REPO_ROOT}"
exec "${REPO_ROOT}/gradlew" ${GRADLE_TASKS}
