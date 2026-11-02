#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "usage: wait-for-line.sh FILE EXTENDED_REGEX [TIMEOUT_SECONDS=1800]" >&2
  echo "prints the first matching line; exits 0 on match, 124 on timeout" >&2
  exit 2
fi

file=$1
pattern=$2
limit=${3:-1800}

timeout "$limit" grep -m1 -E -- "$pattern" < <(tail -n +1 -F --pid=$$ -- "$file" 2>/dev/null)
