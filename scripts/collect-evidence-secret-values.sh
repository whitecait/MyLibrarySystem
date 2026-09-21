#!/usr/bin/env bash
# Build the temporary secret-value set for check-sensitive-evidence.sh
# (acceptance plan 2026-09-19, item 2.7). Extracts VALUES from env-style
# files — key names, comments and blank lines are dropped so the match set
# contains secret values only.
#
#   SECRET_SET="$(mktemp)"
#   bash scripts/collect-evidence-secret-values.sh .env "$SECRET_SET"
#   bash scripts/check-sensitive-evidence.sh <evidence-dir> "$SECRET_SET"
#   rm -f "$SECRET_SET"
#
# The temp file holds raw secret values: keep it in tmpfs/scratch, never in
# the evidence tree, and delete it right after the scan.
set -Eeuo pipefail

if (( $# < 2 )); then
  echo "usage: $0 ENV_FILE... OUT_FILE" >&2
  exit 2
fi

out_file="${@: -1}"
env_files=("${@:1:$#-1}")
: > "$out_file"

for env_file in "${env_files[@]}"; do
  test -f "$env_file" || { echo "not a file: $env_file" >&2; exit 2; }
  # Credential keys only: the acceptance .env files also carry operational
  # config (ports, pinned image tags, provider base URLs) that legitimately
  # appears in evidence; matching those turned the scan into noise.
  awk -F= '
    { sub(/\r$/, "") }
    $0 !~ /^[[:space:]]*#/ && $0 !~ /^[[:space:]]*$/ && $0 ~ /=/ {
      key = $0
      sub(/=.*$/, "", key)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
      if (key !~ /(PASSWORD|SECRET|TOKEN|API_KEY|_KEY)/) next
      val = $0
      if (val ~ /=/) sub(/^[^=]*=/, "", val)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", val)
      if (length(val) > 0) print val
    }' "$env_file" >> "$out_file"
done

echo "secret values: $(wc -l < "$out_file")"
