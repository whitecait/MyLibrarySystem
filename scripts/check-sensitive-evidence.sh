#!/usr/bin/env bash
set -Eeuo pipefail

if (( $# < 2 )); then
  echo "usage: $0 EVIDENCE_DIR SECRET_FILE..." >&2
  exit 2
fi

evidence="$1"
shift
test -d "$evidence"

patterns="$(mktemp)"
trap 'rm -f "$patterns"' EXIT
for secret_file in "$@"; do
  test -f "$secret_file"
  awk 'length($0) > 0' "$secret_file" >> "$patterns"
done

if test -s "$patterns" && rg --no-ignore --hidden --quiet --fixed-strings --file "$patterns" -- "$evidence"; then
  echo 'sensitive value found in evidence' >&2
  exit 1
fi
