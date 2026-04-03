#!/usr/bin/env bash

set -euo pipefail

PORT="${1:-8080}"

usage() {
  cat <<EOF
Usage:
  ./scripts/find-port-pid.sh
  ./scripts/find-port-pid.sh 8081

What it does:
  Prints the PID of the process listening on the selected TCP port.

Arguments:
  port            TCP port to inspect
                  Default: 8080

Options:
  -h, --help      Show this help message
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! "${PORT}" =~ ^[0-9]+$ ]]; then
  echo "Port must be a number: ${PORT}" >&2
  exit 1
fi

find_pid_with_lsof() {
  lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null | head -n 1
}

find_pid_with_ss() {
  ss -ltnp "sport = :${PORT}" 2>/dev/null | awk '
    NR > 1 {
      if (match($0, /pid=([0-9]+)/, matches)) {
        print matches[1]
        exit
      }
    }
  '
}

pid=""

if command -v lsof >/dev/null 2>&1; then
  pid="$(find_pid_with_lsof)"
fi

if [[ -z "${pid}" ]] && command -v ss >/dev/null 2>&1; then
  pid="$(find_pid_with_ss)"
fi

if [[ -z "${pid}" ]]; then
  echo "No listening process found on port ${PORT}." >&2
  exit 1
fi

echo "Listening PID on port ${PORT}: ${pid}"
