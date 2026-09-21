#!/usr/bin/env bash
# Literal-match scan of an evidence tree against known secret values.
#
#   bash scripts/check-sensitive-evidence.sh <evidence-dir> SECRET_FILE...
#
# Every non-empty line of each SECRET_FILE is one literal search pattern.
# Exit codes:
#   0  clean - no secret value found
#   1  hit - at least one secret value found under the evidence directory
#   2  usage or setup error - bad arguments, rg missing, evidence directory or
#      secret file missing/unreadable; the scan did not run
#   3  rg failed at runtime (rg exit status >= 2); the scan result is unknown
#      and must never be presented as clean
set -Eeuo pipefail

if (( $# < 2 )); then
  echo "usage: $0 EVIDENCE_DIR SECRET_FILE..." >&2
  exit 2
fi

# Exit gate: a missing scan tool must fail loudly, never read as a clean scan.
command -v rg >/dev/null 2>&1 || { echo 'rg is not available; scanner cannot run' >&2; exit 2; }

evidence="$1"
shift
if ! test -d "$evidence"; then
  echo "evidence directory is missing or not a directory: $evidence" >&2
  exit 2
fi

patterns="$(mktemp)"
trap 'rm -f "$patterns"' EXIT
for secret_file in "$@"; do
  if ! test -r "$secret_file"; then
    echo "secret file is missing or unreadable: $secret_file" >&2
    exit 2
  fi
  awk 'length($0) > 0' "$secret_file" >> "$patterns"
done

# Every secret file held only empty lines: there is nothing to search for.
if ! test -s "$patterns"; then
  exit 0
fi

# rg contract: 0 = match found, 1 = no match, >= 2 = runtime error. The error
# branch stays separate so a failing rg can never collapse into "clean".
rg_status=0
rg --no-ignore --hidden --quiet --fixed-strings --file "$patterns" -- "$evidence" || rg_status=$?
case "$rg_status" in
  0)
    echo 'sensitive value found in evidence' >&2
    exit 1
    ;;
  1)
    exit 0
    ;;
  *)
    echo "rg failed while scanning '$evidence' (exit status $rg_status); scan result unknown, not clean" >&2
    exit 3
    ;;
esac
