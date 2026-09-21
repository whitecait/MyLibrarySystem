#!/usr/bin/env bash
# Literal-match scan of an evidence path against known secret values.
#
#   bash scripts/check-sensitive-evidence.sh <evidence-path> SECRET_FILE...
#   bash scripts/check-sensitive-evidence.sh --help
#
# <evidence-path> is a directory or a single file. Every non-empty line of each
# SECRET_FILE is one literal search pattern. The value set is normally produced
# by scripts/collect-evidence-secret-values.sh from the .env files; an empty set
# is a setup error, not a clean scan (a scan that searched for nothing proves
# nothing).
#
# A hit is reported per file and per line: the path, the one-based line number,
# the value length and a first/last-character mask. The value text itself is
# never printed, so the scanner output is safe to keep as evidence.
#
# Exit codes:
#   0  clean - no secret value found
#   1  hit - at least one secret value found under the evidence path
#   2  usage or setup error - bad arguments, rg missing, evidence path missing,
#      secret file missing/unreadable, or the merged value set is empty; the
#      scan did not run
#   3  rg failed at runtime (rg exit status >= 2); the scan result is unknown
#      and must never be presented as clean
set -Eeuo pipefail

usage() {
  echo "usage: bash scripts/check-sensitive-evidence.sh EVIDENCE_PATH SECRET_FILE..." >&2
  echo "  EVIDENCE_PATH  directory or file to scan" >&2
  echo "  SECRET_FILE    one literal secret value per line; generate with" >&2
  echo "                 scripts/collect-evidence-secret-values.sh (an empty set is a setup error)" >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if (( $# < 2 )); then
  usage
  exit 2
fi

# Exit gate: a missing scan tool must fail loudly, never read as a clean scan.
command -v rg >/dev/null 2>&1 || { echo 'rg is not available; scanner cannot run' >&2; exit 2; }

evidence="$1"
shift
if ! test -d "$evidence" && ! test -f "$evidence"; then
  echo "evidence path is missing or is neither a file nor a directory: $evidence" >&2
  exit 2
fi

work="$(mktemp -d "${TMPDIR:-/tmp}/check-sensitive-evidence.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
patterns="$work/patterns"
: > "$patterns"
for secret_file in "$@"; do
  if ! test -r "$secret_file"; then
    echo "secret file is missing or unreadable: $secret_file" >&2
    exit 2
  fi
  awk 'length($0) > 0' "$secret_file" >> "$patterns"
done

# Every secret file held only empty lines: the scan cannot prove anything, and
# reporting "clean" here is how a missing value set turns into false assurance.
if ! test -s "$patterns"; then
  echo "the secret-value set is empty (no non-empty line in: $*); refusing to report a clean scan" >&2
  exit 2
fi

# rg contract: 0 = match found, 1 = no match, >= 2 = runtime error. The error
# branch stays separate so a failing rg can never collapse into "clean".
file_list="$work/files-with-matches"
rg_status=0
rg --no-ignore --hidden --files-with-matches --fixed-strings --file "$patterns" -- "$evidence" \
  > "$file_list" 2> "$work/rg-stderr" || rg_status=$?
case "$rg_status" in
  0)
    ;;
  1)
    exit 0
    ;;
  *)
    echo "rg failed while scanning '$evidence' (exit status $rg_status); scan result unknown, not clean" >&2
    sed 's/^/  rg: /' "$work/rg-stderr" >&2 || true
    exit 3
    ;;
esac

# Enumerate the hits as path:line with a mask (never the value itself). The
# per-file awk pass re-reads the value set and blanks each match before moving
# on, so overlapping values cannot leave a residue and no match is counted
# twice. Values are applied longest-first for the same reason.
hits_file="$work/hits"
: > "$hits_file"
LC_ALL=C sort -o "$file_list" "$file_list"
while IFS= read -r hit_path; do
  [ -n "$hit_path" ] || continue
  awk_ok=1
  awk -v setfile="$patterns" -v path="$hit_path" '
    BEGIN {
      n = 0
      while ((getline p < setfile) > 0) { if (length(p) > 0) pats[++n] = p }
      close(setfile)
      for (i = 2; i <= n; i++) {
        v = pats[i]; j = i - 1
        while (j >= 1 && length(pats[j]) < length(v)) { pats[j + 1] = pats[j]; j-- }
        pats[j + 1] = v
      }
    }
    {
      line = $0
      for (i = 1; i <= n; i++) {
        p = pats[i]; L = length(p); from = 1
        while (L > 0) {
          idx = index(substr(line, from), p)
          if (idx == 0) break
          pos = from + idx - 1
          printf "hit: %s:%d len=%d mask=%s\n", path, FNR, L, mask(p)
          line = substr(line, 1, pos - 1) sprintf("%" L "s", "") substr(line, pos + L)
          from = pos + L
        }
      }
    }
    function mask(p) {
      if (length(p) >= 8) return substr(p, 1, 1) ".." substr(p, length(p), 1)
      return "<len-only>"
    }
  ' "$hit_path" >> "$hits_file" || awk_ok=0
  if [ "$awk_ok" != "1" ]; then
    # A file rg can match but awk cannot read (binary content, permission race):
    # report it as a hit with an unknown line rather than dropping it.
    printf 'hit: %s:<line unknown: unreadable by awk>\n' "$hit_path" >> "$hits_file"
  fi
done < "$file_list"

hit_count="$(grep -c '^hit: ' "$hits_file" 2>/dev/null || true)"
hit_count="${hit_count:-0}"
file_count="$(wc -l < "$file_list" | tr -d ' ')"
{
  echo "sensitive value found in evidence: ${hit_count} hit(s) in ${file_count} file(s) under ${evidence}"
  echo "  (value text is never printed; len is the value length, mask shows its first and last character)"
  cat "$hits_file"
} >&2
exit 1
