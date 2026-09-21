#!/usr/bin/env bash
# Prepare the acceptance env file consumed by compose.acceptance.yaml
# (acceptance plan 2026-09-19, item 2.2).
#
#   bash scripts/prepare-acceptance-env.sh [.env] [.env.acceptance]
#
# Reads BASE_URL / MODEL / API_KEY from the ignored .env, rejects a BASE_URL
# with a trailing slash or missing values, and writes AI_ENDPOINT_URL =
# <BASE_URL>/chat/completions plus AI_MODEL / AI_API_KEY and the Env-W
# acceptance quota AI_VISITOR_DAILY_LIMIT into the ignored output file.
# Secret values are never echoed; only the endpoint host is printed.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

env_file="${1:-.env}"
out_file="${2:-.env.acceptance}"

if [[ ! -f "$env_file" ]]; then
  echo "missing $env_file (copy .env.example and fill your own values)" >&2
  exit 2
fi

get_env() {
  awk -F= -v key="$1" '
    { sub(/\r$/, "") }
    $0 !~ /^[[:space:]]*#/ && $0 ~ "^[[:space:]]*" key "=" {
      val = $0
      sub(/^[^=]*=/, "", val)
      print val
      exit
    }' "$env_file"
}

base_url="$(get_env BASE_URL || true)"
model="$(get_env MODEL || true)"
api_key="$(get_env API_KEY || true)"

: "${base_url:?Set BASE_URL in .env (provider base incl. /v1, no trailing slash)}"
: "${model:?Set MODEL in .env}"
: "${api_key:?Set API_KEY in .env}"

case "$base_url" in
  */) echo "BASE_URL must not have a trailing slash (backend semantics use the full path, not a base): $base_url" >&2; exit 2 ;;
  http://* | https://*) ;;
  *) echo "BASE_URL must be an absolute http(s) URL" >&2; exit 2 ;;
esac

endpoint_url="${base_url}/chat/completions"
host="$(printf '%s' "$base_url" | sed -E 's#^[a-z]+://([^/]+).*#\1#')"

# Env-W acceptance quota (acceptance plan 2026-09-19 run log item ④: visitor
# quota 8/day for the E2E-AI-001 quota-exhaustion scenario). .env may pin a
# different value; otherwise the registered acceptance value is written
# explicitly so .env.acceptance always carries the quota convention that
# e2e/scenarios/e2e-ai-001-ai-portal.spec.js reads back as its expectation.
visitor_daily_limit="$(get_env AI_VISITOR_DAILY_LIMIT || true)"
: "${visitor_daily_limit:=8}"

{
  printf 'AI_ENDPOINT_URL=%s\n' "$endpoint_url"
  printf 'AI_MODEL=%s\n' "$model"
  printf 'AI_API_KEY=%s\n' "$api_key"
  printf 'AI_VISITOR_DAILY_LIMIT=%s\n' "$visitor_daily_limit"
} > "$out_file"
chmod 600 "$out_file"

echo "wrote $out_file (endpoint host: ${host}; values not echoed)"
