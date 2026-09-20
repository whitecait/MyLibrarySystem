#!/usr/bin/env bash
# Deep sensitive-evidence scan: check-sensitive-evidence.sh cannot see inside
# zip archives (Playwright traces, report bundles). This helper unpacks every
# zip under the evidence tree into a scratch directory and scans the unpacked
# tree together with the original tree (acceptance plan 2026-09-19, item 2.7).
#
#   bash scripts/check-sensitive-evidence-deep.sh <evidence-dir> SECRET_FILE...
#
# Exit 1 on any hit, printing the archive that contained it. The scratch copy
# is always removed; no archive is modified in place.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

if (( $# < 2 )); then
  echo "usage: $0 EVIDENCE_DIR SECRET_FILE..." >&2
  exit 2
fi

evidence="$1"
shift
test -d "$evidence"

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

found=""
while IFS= read -r -d '' archive; do
  dest="$scratch/$(echo "$archive" | sed 's#/#_#g')"
  mkdir -p "$dest"
  if unzip -qq -o "$archive" -d "$dest" 2>/dev/null; then
    if bash scripts/check-sensitive-evidence.sh "$dest" "$@" 2>/dev/null; then
      :
    else
      echo "sensitive value found inside archive: $archive" >&2
      found=1
    fi
  else
    echo "warning: could not unpack $archive (skipped)" >&2
  fi
done < <(find "$evidence" -type f -name '*.zip' -print0)

# Loose files first (fail fast on the obvious case).
bash scripts/check-sensitive-evidence.sh "$evidence" "$@"

if [[ -n "$found" ]]; then
  exit 1
fi
echo "deep scan clean (loose files + $(find "$evidence" -type f -name '*.zip' | wc -l) zip archives)"
