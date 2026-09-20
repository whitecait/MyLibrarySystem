#!/usr/bin/env bash
# Collect redacted evidence that the running acceptance stack actually talks
# to the real model (acceptance plan 2026-09-19, item 2.2). Endpoint smoke
# alone is not sufficient: this records the model name, the redacted endpoint
# host, AI request counts and tool-call events from the running system.
#
#   bash scripts/collect-ai-connection-evidence.sh <project> <out-dir>
#
# Never prints or stores AI_API_KEY; host names are recorded without paths.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

project="${1:?usage: collect-ai-connection-evidence.sh <compose-project> <out-dir>}"
out_dir="${2:?usage: collect-ai-connection-evidence.sh <compose-project> <out-dir>}"
mkdir -p "$out_dir"

compose() { docker compose -p "$project" "$@"; }

psql_q() {
  compose exec -T database psql -U library -d library -tA -v ON_ERROR_STOP=1 -c "$1"
}

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
report="$out_dir/ai-connection-${stamp}.md"

endpoint_host="$(compose exec -T backend printenv AI_ENDPOINT_URL 2>/dev/null \
  | sed -E 's#^[a-z]+://([^/]+).*#\1#' || true)"
model_name="$(compose exec -T backend printenv AI_MODEL 2>/dev/null || true)"

{
  echo "# AI connection evidence — ${stamp}"
  echo
  echo "- compose project: \`${project}\`"
  echo "- redacted endpoint host: \`${endpoint_host:-<not set>}\`"
  echo "- configured model: \`${model_name:-<not set>}\`"
  echo
  echo "## ai_event counts by type"
  echo
  echo '```'
  psql_q "SELECT event_type, count(*) FROM ai_event GROUP BY event_type ORDER BY event_type;" \
    || echo "(query failed — stack not running or schema not migrated)"
  echo '```'
  echo
  echo "## model request/response and tool-call events"
  echo
  echo '```'
  psql_q "SELECT count(*) FROM ai_event WHERE event_type IN ('MODEL_REQUEST','MODEL_RESPONSE','TOOL_CALL','TOOL_RESULT');" \
    || true
  echo '```'
  echo
  echo "## visitor quota usage (model_calls)"
  echo
  echo '```'
  psql_q "SELECT usage_date, model_calls FROM ai_visitor_quota_usage ORDER BY usage_date DESC LIMIT 7;" \
    || true
  echo '```'
  echo
  echo "## recent MODEL_REQUEST payloads (model/thinking fields only)"
  echo
  echo '```'
  psql_q "SELECT occurred_at, payload->>'model', payload->>'thinking' FROM ai_event WHERE event_type='MODEL_REQUEST' ORDER BY id DESC LIMIT 5;" \
    || true
  echo '```'
  echo
  echo "## backend log lines mentioning the endpoint host (redacted: host only)"
  echo
  echo '```'
  compose logs backend 2>/dev/null | grep -i "$endpoint_host" | tail -10 || echo "(no log lines matched)"
  echo '```'
} > "$report"

echo "wrote $report"
echo "gate: MODEL_REQUEST/TOOL_* counts must be non-zero after exercising an AI session"
