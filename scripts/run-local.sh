#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PORT="${PORT:-8080}"
REQUIRED_JAVA_MAJOR=25

print_java_install_hints() {
  cat <<EOF
Set JAVA_HOME to a Java ${REQUIRED_JAVA_MAJOR} installation and put it first on PATH.

On macOS:
  export JAVA_HOME="\$(/usr/libexec/java_home -v ${REQUIRED_JAVA_MAJOR})"
  export PATH="\$JAVA_HOME/bin:\$PATH"

On Windows PowerShell:
  \$env:JAVA_HOME="C:\\Program Files\\Java\\jdk-${REQUIRED_JAVA_MAJOR}"
  \$env:Path="\$env:JAVA_HOME\\bin;\$env:Path"

On Windows Git Bash:
  export JAVA_HOME="/c/Program Files/Java/jdk-${REQUIRED_JAVA_MAJOR}"
  export PATH="\$JAVA_HOME/bin:\$PATH"

On Amazon Linux 2023:
  sudo dnf install -y java-${REQUIRED_JAVA_MAJOR}-amazon-corretto-devel
  export JAVA_HOME=/usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-amazon-corretto.x86_64
  export PATH="\$JAVA_HOME/bin:\$PATH"
EOF
}

usage() {
  cat <<EOF
Usage:
  ./scripts/run-local.sh
  PORT=8081 ./scripts/run-local.sh

What it does:
  Builds the Spring Boot jar with Gradle and runs it with Java on the selected local port.

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

require_java_25() {
  if ! command -v java >/dev/null 2>&1; then
    cat >&2 <<EOF
Error: Java ${REQUIRED_JAVA_MAJOR} is required, but 'java' is not on PATH.

Install Java ${REQUIRED_JAVA_MAJOR} and set JAVA_HOME accordingly.
$(print_java_install_hints)
EOF
    exit 1
  fi

  local java_version_output java_version_line java_major
  java_version_output="$(java -version 2>&1)"
  java_version_line="$(grep -m1 'version "' <<<"${java_version_output}" || true)"
  java_major="$(sed -nE 's/.*version "([0-9]+).*/\1/p' <<<"${java_version_line}")"

  if [[ -z "${java_major}" ]]; then
    cat >&2 <<EOF
Error: Unable to determine the installed Java version from:
${java_version_output}

Java ${REQUIRED_JAVA_MAJOR} is required for this project.
EOF
    exit 1
  fi

  if [[ "${java_major}" != "${REQUIRED_JAVA_MAJOR}" ]]; then
    cat >&2 <<EOF
Error: Java ${REQUIRED_JAVA_MAJOR} is required, but found Java ${java_major}.
Detected:
  ${java_version_line}

$(print_java_install_hints)
EOF
    exit 1
  fi
}

require_java_25

cd "${REPO_ROOT}"
BOOT_JAR_TASK_ARGS=(bootJar)
JAVA_RUN_ARGS=(-jar)

"${REPO_ROOT}/gradlew" "${BOOT_JAR_TASK_ARGS[@]}"

boot_jar_path="$(find "${REPO_ROOT}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | sort | head -n 1)"

if [[ -z "${boot_jar_path}" ]]; then
  echo "Unable to find the Spring Boot jar under build/libs after running bootJar." >&2
  exit 1
fi

set +e
java "${JAVA_RUN_ARGS[@]}" "${boot_jar_path}" --server.port="${PORT}"
exit_code=$?
set -e

if [[ "${exit_code}" -eq 143 ]]; then
  echo "Spring Boot stopped cleanly after receiving SIGTERM."
  exit 0
fi

exit "${exit_code}"
