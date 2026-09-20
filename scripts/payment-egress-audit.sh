#!/usr/bin/env bash
# Payment-batch egress audit (acceptance plan 2026-09-19, item 2.9 /
# 4.11). Verifies the stack network is internal (no external route) and
# records the payment execution window. Must be started BEFORE the payment
# batch and closed after it; the evidence covers the actual window and is
# never reconstructed afterwards.
#
#   bash scripts/payment-egress-audit.sh start <project> <out-dir>
#   bash scripts/payment-egress-audit.sh close <project> <out-dir>
set -Eeuo pipefail
cd "$(dirname "$0")/.."

mode="${1:?usage: payment-egress-audit.sh start|close <project> <out-dir>}"
project="${2:?usage: payment-egress-audit.sh start|close <project> <out-dir>}"
out_dir="${3:?usage: payment-egress-audit.sh start|close <project> <out-dir>}"
mkdir -p "$out_dir"

network_id="$(docker network ls --filter "name=${project}" --format '{{.ID}}' | head -1)"
if [[ -z "$network_id" ]]; then
  echo "no docker network found for project ${project}" >&2
  exit 1
fi
internal="$(docker network inspect "$network_id" --format '{{.Internal}}')"
if [[ "$internal" != "true" ]]; then
  echo "network ${project} is NOT internal (Internal=${internal}); apply compose.payment-egress.yaml" >&2
  exit 1
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
record="$out_dir/payment-egress-${mode}-${stamp}.json"
cat > "$record" <<JSON
{
  "mode": "${mode}",
  "project": "${project}",
  "networkId": "${network_id}",
  "networkInternal": true,
  "utcTime": "${stamp}",
  "note": "default network is internal; containers have no external route during the payment window"
}
JSON
echo "wrote $record"
