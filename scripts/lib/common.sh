#!/usr/bin/env bash

PYTHON_CMD=()

is_windows_store_python_alias() {
  local resolved_path="$1"
  local normalized_path="${resolved_path//\\//}"

  case "${normalized_path}" in
    */WindowsApps/python.exe|*/WindowsApps/python3.exe)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

can_run_python_command() {
  local candidate=("$@")
  local resolved_path

  resolved_path="$(command -v "${candidate[0]}" 2>/dev/null || true)"
  if [[ -z "${resolved_path}" ]]; then
    return 1
  fi

  if is_windows_store_python_alias "${resolved_path}"; then
    return 1
  fi

  "${candidate[@]}" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 0) else 1)' >/dev/null 2>&1
}

init_python_cmd() {
  if [[ ${#PYTHON_CMD[@]} -gt 0 ]]; then
    return 0
  fi

  if can_run_python_command python3; then
    PYTHON_CMD=(python3)
    return 0
  fi

  if can_run_python_command python; then
    PYTHON_CMD=(python)
    return 0
  fi

  if can_run_python_command py -3; then
    PYTHON_CMD=(py -3)
    return 0
  fi

  cat >&2 <<'EOF'
Python 3 is required but no working interpreter was found.

Windows note:
- The Microsoft Store aliases for 'python' and 'python3' are not enough.
- Install Python 3, or disable the App Execution Aliases for python.exe/python3.exe.
- If Python is installed, the 'py -3' launcher also works.
EOF
  return 1
}

run_python() {
  init_python_cmd || return 1
  "${PYTHON_CMD[@]}" "$@"
}
