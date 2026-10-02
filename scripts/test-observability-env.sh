#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
export DB_PASSWORD=c1-database-fixture-not-real
export GRAFANA_PASSWORD=c1-grafana-fixture-not-real
export AI_VISITOR_COOKIE_SIGNING_KEY=c1-cookie-fixture-key-not-real-1234567890
export AI_API_KEY=c1-model-fixture-never-real
export API_KEY=c1-legacy-fixture-never-real
export AI_ENDPOINT_URL=https://provider.invalid/v1/chat/completions AI_MODEL=fixture-model
source scripts/observability-env.sh
[[ -z "$AI_API_KEY" && -z "$AI_ENDPOINT_URL" && -z "$AI_MODEL" ]]
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
# Parse the actual current overlays; no containers are started.
docker compose --env-file /dev/null -f compose.yaml -f compose.observability.yaml config 2>&1 | redact > "$work/config.txt"
python3 - "$work/config.txt" <<'PY'
import sys
text=open(sys.argv[1]).read()
for value in ['c1-database-fixture-not-real','c1-grafana-fixture-not-real',
              'c1-cookie-fixture-key-not-real-1234567890','c1-model-fixture-never-real',
              'c1-legacy-fixture-never-real','provider.invalid','fixture-model']:
    assert value not in text
print('Actual Compose model configuration isolated; secret evidence redaction passed.')
PY
printf '%s\n' "$MLS_C1_INHERITED_MODEL_KEY" "$MLS_C1_INHERITED_LEGACY_KEY" | redact > "$work/redacted.txt"
[[ "$(cat "$work/redacted.txt")" == $'[REDACTED]\n[REDACTED]' ]]
