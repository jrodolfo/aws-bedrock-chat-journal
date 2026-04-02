#!/usr/bin/env bash

find_python_bin() {
  if command -v python3 >/dev/null 2>&1; then
    printf '%s\n' "python3"
    return 0
  fi

  if command -v python >/dev/null 2>&1; then
    printf '%s\n' "python"
    return 0
  fi

  echo "Python 3 is required but neither 'python3' nor 'python' was found in PATH." >&2
  return 1
}
