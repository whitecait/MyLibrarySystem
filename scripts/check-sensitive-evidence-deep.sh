#!/usr/bin/env bash
# Deep sensitive-evidence scan: check-sensitive-evidence.sh cannot see inside
# zip archives (Playwright traces, report bundles). This helper unpacks every
# zip under the evidence tree into a scratch directory and scans the unpacked
# tree together with the original tree (acceptance plan 2026-09-19, item 2.7).
#
#   bash scripts/check-sensitive-evidence-deep.sh <evidence-dir> SECRET_FILE...
#
# Exit codes:
#   0  clean - loose files and every zip archive were scanned, no secret found
#   1  hit - a secret value was found (inside an archive or in loose files)
#   2  usage or setup error - the scan did not run
#   3  at least one archive could not be unpacked, or the scan of an unpacked
#      archive failed; the failing archives are listed on stderr. An unpack
#      failure is never read as a clean scan.
# The scratch copy is always removed by the EXIT trap; no archive is modified
# in place.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

if (( $# < 2 )); then
  echo "usage: $0 EVIDENCE_DIR SECRET_FILE..." >&2
  exit 2
fi

evidence="$1"
shift
if ! test -d "$evidence"; then
  echo "evidence directory is missing or not a directory: $evidence" >&2
  exit 2
fi

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

found=""
failed_archives=()
while IFS= read -r -d '' archive; do
  dest="$scratch/$(echo "$archive" | sed 's#/#_#g')"
  mkdir -p "$dest"
  if unzip -qq -o "$archive" -d "$dest" 2>/dev/null; then
    scan_status=0
    bash scripts/check-sensitive-evidence.sh "$dest" "$@" 2>/dev/null || scan_status=$?
    case "$scan_status" in
      0)
        ;;
      1)
        echo "sensitive value found inside archive: $archive" >&2
        found=1
        ;;
      *)
        echo "scan of unpacked archive failed (exit status $scan_status): $archive" >&2
        failed_archives+=("$archive")
        ;;
    esac
  else
    echo "could not unpack: $archive" >&2
    failed_archives+=("$archive")
  fi
done < <(find "$evidence" -type f -name '*.zip' -print0)

# Loose files: their exit code is authoritative for the loose part of the tree.
loose_status=0
bash scripts/check-sensitive-evidence.sh "$evidence" "$@" || loose_status=$?

overall="$loose_status"
if [[ -n "$found" ]]; then
  overall=1
fi
if (( ${#failed_archives[@]} > 0 )); then
  echo "deep scan failed: the following archives could not be unpacked or scanned:" >&2
  printf '  %s\n' "${failed_archives[@]}" >&2
  if [[ "$overall" == "0" ]]; then
    overall=3
  fi
fi

if [[ "$overall" == "1" ]]; then
  exit 1
fi
if [[ "$overall" != "0" ]]; then
  exit "$overall"
fi
echo "deep scan clean (loose files + $(find "$evidence" -type f -name '*.zip' | wc -l) zip archives)"
