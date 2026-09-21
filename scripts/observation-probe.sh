#!/usr/bin/env bash
# Fixed-rate synthetic probe for the 7-day observation window (AT-0018 C02,
# MLS-NFR-AVL-0001 C02). Every cycle sends the core-scenario synthetic
# requests and appends one JSONL line to the raw probe file; the raw file is
# the availability evidence and is never trimmed (scripts/observation-report.mjs
# computes (total-failed)/total and applies the pre-registered maintenance
# windows).
#
#   PROBE_BASE_URL=http://127.0.0.1:8088 \
#   PROBE_INTERVAL_S=30 \
#   PROBE_ACCOUNT=probe-reader PROBE_PASSWORD='...' \
#     bash scripts/observation-probe.sh
#
# Probe set (core scenarios, read-only + login):
#   health  GET  /api/health                         expect 2xx
#   search  GET  /api/v1/titles?q=...&limit=5        expect 2xx
#   login   POST /api/v1/auth/sessions               expect 2xx
# (login declares a required Idempotency-Key — without it the call 400s.)
#
# Credentials reach curl only through a scratch file (never command args);
# responses are discarded. Run under a supervisor; SIGTERM/SIGINT close the
# JSONL cleanly.
set -Eeuo pipefail
umask 077

base_url="${PROBE_BASE_URL:-http://127.0.0.1:8088}"
interval="${PROBE_INTERVAL_S:-30}"
search_word="${PROBE_SEARCH_KEYWORD:-library}"
account="${PROBE_ACCOUNT:?Set PROBE_ACCOUNT}"
password="${PROBE_PASSWORD:?Set PROBE_PASSWORD}"
out="${PROBE_OUT:-artifacts/m6-acceptance/observation/probe-$(date -u +%Y%m%dT%H%M%SZ).jsonl}"
mkdir -p "$(dirname "$out")"

body_scratch="$(mktemp)"
trap 'rm -f "$body_scratch"' EXIT
printf '{"username":"%s","password":"%s"}' "$account" "$password" > "$body_scratch"

probe() { # probe <name> <curl args...>; emits {"probe","status","ms"}
  local name="$1"; shift
  local out_line status ms
  out_line="$(curl -sS -o /dev/null -m 10 -w '%{http_code} %{time_total}' "$@" 2>/dev/null || printf '000 0')"
  status="${out_line%% *}"
  ms="$(python3 -c "import sys;print(int(float('${out_line#* }')*1000))" 2>/dev/null || echo 0)"
  if [[ "$status" =~ ^2 ]]; then ok=true; else ok=false; fi
  printf '{"probe":"%s","status":%s,"ms":%s,"ok":%s}' "$name" "$status" "$ms" "$ok"
}

echo "probing ${base_url} every ${interval}s -> ${out}"
while true; do
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  health="$(probe health "${base_url}/api/health")"
  search="$(probe search "${base_url}/api/v1/titles?q=${search_word}&limit=5")"
  login="$(probe login -X POST -H 'Content-Type: application/json' -H 'Idempotency-Key: probe-'"$(date -u +%Y%m%d%H%M%S)" --data-binary "@${body_scratch}" "${base_url}/api/v1/auth/sessions")"
  if [[ "$health" == *'"ok":true'* && "$search" == *'"ok":true'* && "$login" == *'"ok":true'* ]]; then
    ok=true
  else
    ok=false
  fi
  printf '{"ts":"%s","results":[%s,%s,%s],"ok":%s}\n' "$ts" "$health" "$search" "$login" "$ok" >> "$out"
  sleep "$interval" &
  wait "$!" || true
done
