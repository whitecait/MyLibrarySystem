#!/usr/bin/env bash
# Fixed-slot synthetic probe for the 7-day observation window (AT-0018 C02,
# MLS-NFR-AVL-0001 C02). Every cycle sends the five core-scenario synthetic
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
# Probe set (five probes; request shapes copied from the load-profile
# implementation e2e/perf/scenarios/steady.js + e2e/perf/lib/common.js,
# which is already validated against the contract):
#   health  GET  /api/health                              expect 2xx
#   search  GET  /api/v1/titles?q=...&limit=5             expect 2xx
#   detail  GET  /api/v1/titles/{id}                      expect 2xx
#   login   POST /api/v1/auth/sessions                    expect 2xx
#   loans   GET  /api/v1/me/business?kind=LOAN&limit=5    expect 2xx
#
# detail reuses the search response of the same cycle: steady.js reads
# titleId from search items the same way. If that response yields no id,
# detail records status:0 — no HTTP call happened, so the cycle carries no
# availability evidence for the detail path. loans reuses the login response
# of the same cycle (Authorization: Bearer <accessToken>); if login yields
# no token, loans records status:0 for the same reason.
#
# (login declares a required Idempotency-Key — without it the call 400s.)
# The key is "probe-<UTC second of the login>", unique per cycle: the
# mechanism introduced by dbdf18c.
#
# Fixed slots: cycle starts align to the interval grid (epoch-aligned
# multiples of PROBE_INTERVAL_S). The sleep after each cycle targets the
# next grid point, so the probe period stays one interval regardless of
# request latency. A cycle that outlives its slot skips to the next grid
# point — no burst catch-up. The first cycle runs immediately (its ts is
# the only off-grid one).
#
# Credentials reach curl only through scratch files (never command args);
# PROBE_ACCOUNT / PROBE_PASSWORD are injected through the environment and
# never appear in this script. Responses other than the search/login body
# are discarded. A network failure (connection refused, timeout — curl's
# 000) is recorded as "status":0, a valid JSON number; the report tool
# counts it as a failed probe. Run under a supervisor (e.g. the archived
# artifacts/m6-acceptance/observation/run-probe.sh pattern); SIGTERM/SIGINT
# close the JSONL cleanly.
set -Eeuo pipefail
umask 077

base_url="${PROBE_BASE_URL:-http://127.0.0.1:8088}"
interval="${PROBE_INTERVAL_S:-30}"
search_word="${PROBE_SEARCH_KEYWORD:-library}" # must already be URL-safe
timeout_s="${PROBE_TIMEOUT_S:-10}"
account="${PROBE_ACCOUNT:?Set PROBE_ACCOUNT}"
password="${PROBE_PASSWORD:?Set PROBE_PASSWORD}"
out="${PROBE_OUT:-artifacts/m6-acceptance/observation/probe-$(date -u +%Y%m%dT%H%M%SZ).jsonl}"

[[ "$interval" =~ ^[1-9][0-9]*$ ]] || { echo "PROBE_INTERVAL_S must be a positive integer, got: $interval" >&2; exit 2; }
[[ "$timeout_s" =~ ^[1-9][0-9]*$ ]] || { echo "PROBE_TIMEOUT_S must be a positive integer, got: $timeout_s" >&2; exit 2; }
mkdir -p "$(dirname "$out")"

cred_scratch="$(mktemp)"
search_scratch="$(mktemp)"
login_scratch="$(mktemp)"
trap 'rm -f "$cred_scratch" "$search_scratch" "$login_scratch"' EXIT
trap 'exit 0' INT TERM
printf '{"username":"%s","password":"%s"}' "$account" "$password" > "$cred_scratch"

# probe <body-file> <name> <curl args...> — emits {"probe","status","ms","ok"}.
# body-file receives the response body ("/dev/null" to discard). A missing
# HTTP status (connection refused, timeout; curl prints 000) is normalised
# to 0 so every line is valid JSON: "status":000 is not a JSON number.
probe() {
  local body_file="$1" name="$2"; shift 2
  local raw status ms ok
  raw="$(curl -sS -o "$body_file" -m "$timeout_s" -w '%{http_code} %{time_total}' "$@" 2>/dev/null)" || raw="${raw:-}"
  status="${raw%% *}"
  [[ "$status" =~ ^[0-9]+$ ]] || status=0
  [[ "$status" == "000" ]] && status=0
  ms="$(awk -v t="${raw#* }" 'BEGIN { printf "%d", t * 1000 }')" || ms=0
  if [[ "$status" =~ ^2 ]]; then ok=true; else ok=false; fi
  printf '{"probe":"%s","status":%s,"ms":%s,"ok":%s}' "$name" "$status" "$ms" "$ok"
}

# emit_unprobed <name> — the probe had no runnable precondition this cycle
# (no titleId from search / no token from login): no HTTP call happened and
# the line records status:0 (failed), never a fabricated status.
emit_unprobed() {
  printf '{"probe":"%s","status":0,"ms":0,"ok":false}' "$1"
}

# first_title_id — first titleId of a titles-list response body, or "".
first_title_id() {
  python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
    items = (d.get("data") or {}).get("items") or []
    tid = items[0].get("titleId") if items else None
    print(str(tid) if tid is not None else "")
except Exception:
    print("")' < "$search_scratch"
}

# access_token — bearer token of a login response body, or "".
access_token() {
  python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
    data = d.get("data") or {}
    tok = data.get("accessToken") or data.get("token")
    print(str(tok) if tok else "")
except Exception:
    print("")' < "$login_scratch"
}

echo "probing ${base_url} every ${interval}s (5 probes, fixed slots) -> ${out}"
while true; do
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  : > "$search_scratch"; : > "$login_scratch"

  health="$(probe /dev/null health "${base_url}/api/health")"
  search="$(probe "$search_scratch" search "${base_url}/api/v1/titles?q=${search_word}&limit=5")"

  title_id="$(first_title_id)"
  if [[ -n "$title_id" ]]; then
    detail="$(probe /dev/null detail "${base_url}/api/v1/titles/${title_id}")"
  else
    detail="$(emit_unprobed detail)"
  fi

  login="$(probe "$login_scratch" login -X POST \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: probe-$(date -u +%Y%m%d%H%M%S)" \
    --data-binary "@${cred_scratch}" "${base_url}/api/v1/auth/sessions")"

  token="$(access_token)"
  if [[ -n "$token" ]]; then
    loans="$(probe /dev/null loans -H "Authorization: Bearer ${token}" \
      "${base_url}/api/v1/me/business?kind=LOAN&limit=5")"
  else
    loans="$(emit_unprobed loans)"
  fi

  if [[ "$health" == *'"ok":true'* && "$search" == *'"ok":true'* && "$detail" == *'"ok":true'* \
       && "$login" == *'"ok":true'* && "$loans" == *'"ok":true'* ]]; then
    ok=true
  else
    ok=false
  fi
  printf '{"ts":"%s","results":[%s,%s,%s,%s,%s],"ok":%s}\n' \
    "$ts" "$health" "$search" "$detail" "$login" "$loans" "$ok" >> "$out"

  # Fixed slot: sleep until the next epoch-aligned multiple of the interval.
  now="$(date -u +%s)"
  sleep_seconds=$(( (now / interval + 1) * interval - now ))
  sleep "$sleep_seconds" &
  wait "$!" || true
done
