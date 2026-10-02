#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null
docker info >/dev/null
test -f compose.integration.yaml

run_id="$(date -u +%Y%m%d%H%M%S)-$$"
project="mls-integration-${run_id}"
export DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
export AI_VISITOR_COOKIE_SIGNING_KEY="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
export FRONTEND_PORT=0
evidence="${EVIDENCE_DIR:-$PWD/artifacts/integration-$run_id}"
mkdir -p "$evidence"
evidence="$(cd "$evidence" && pwd)"
scratch="$(mktemp -d)"
secrets="$scratch/secrets.txt"
touch "$secrets"
chmod 600 "$secrets"

remember_secret() {
  printf '%s\n' "$1" >> "$secrets"
}
remember_secret "$DB_PASSWORD"
remember_secret "$AI_VISITOR_COOKIE_SIGNING_KEY"

dc() { docker compose --env-file /dev/null -p "$project" -f compose.yaml -f compose.integration.yaml "$@"; }
redact() {
  python3 -c 'import sys
d = sys.stdin.read()
with open(sys.argv[1], encoding="utf-8") as values:
    for value in values:
        value = value.rstrip("\n")
        if value:
            d = d.replace(value, "[REDACTED]")
sys.stdout.write(d)' "$secrets"
}
cleanup() {
  result=$?
  trap - EXIT
  set +e
  dc logs --no-color --timestamps 2>&1 | redact > "$evidence/compose.log"
  dc ps -a > "$evidence/services.txt" 2>&1
  dc down --volumes --remove-orphans --timeout 20 >> "$evidence/cleanup.log" 2>&1
  cleanup_result=$?
  if ! scripts/check-sensitive-evidence.sh "$evidence" "$secrets"; then
    echo 'sensitive credential leaked into integration evidence' >&2
    result=1
  fi
  rm -rf "$scratch"
  if (( cleanup_result != 0 && result == 0 )); then result=$cleanup_result; fi
  printf 'exit_code=%s\n' "$result" > "$evidence/result.txt"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

json_at() {
  python3 -c 'import json,sys; n=json.load(sys.stdin); print(n'"$1"')'
}
assert_json() {
  local file="$1" expression="$2"
  python3 - "$file" "$expression" <<'PY'
import json, sys
value = json.load(open(sys.argv[1], encoding="utf-8"))
assert eval(sys.argv[2], {"__builtins__": {}}, {"value": value}), (sys.argv[1], value)
PY
}
request() {
  local name="$1" method="$2" path="$3" expected="$4"
  shift 4
  local retain=true
  if [[ "${1:-}" == "--private-response" ]]; then
    retain=false
    shift
  fi
  local status
  status="$(curl --silent --show-error --connect-timeout 3 --max-time 30 \
    --request "$method" "$base$path" --output "$scratch/$name.json" \
    --write-out '%{http_code}' "$@")"
  test "$status" = "$expected"
  if [[ "$retain" == true ]]; then
    cp "$scratch/$name.json" "$evidence/$name.json"
  fi
}

{
  git rev-parse HEAD
  docker version
  docker compose version
} > "$evidence/environment.txt"
dc config --quiet
dc build --pull
dc up -d --wait --wait-timeout 180
address="$(dc port frontend 80)"
base="http://$address"

# Public proxy and migration baseline.
request health GET /api/health 200
assert_json "$scratch/health.json" 'value == {"status":"UP"}'
python3 scripts/check-migrations.py --actual "$(dc exec -T database psql -U library -d library -Atc "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL AND success")"

# Visitor session start. Header is kept only in scratch because it contains the opaque credential.
status="$(curl --silent --show-error --connect-timeout 3 --max-time 30 --request POST \
  "$base/api/v1/ai/sessions" -H 'Idempotency-Key: integration-visitor-a' \
  -D "$scratch/visitor-a.headers" -o "$scratch/visitor-a.json" -w '%{http_code}')"
test "$status" = 200
visitor_a_id="$(json_at "['data']['sessionId']" < "$scratch/visitor-a.json")"
visitor_a_cookie="$(python3 - "$scratch/visitor-a.headers" <<'PY'
import re,sys
h=open(sys.argv[1], encoding="utf-8").read()
m=re.search(r'(?im)^set-cookie:\s*__Host-mls-ai-visitor=([^;]+);', h)
assert m and 'Secure' in h and 'HttpOnly' in h and 'SameSite=Strict' in h and 'Path=/' in h
print(m.group(1))
PY
)"
remember_secret "$visitor_a_cookie"
printf 'Cookie: __Host-mls-ai-visitor=%s\n' "$visitor_a_cookie" > "$scratch/visitor-a-auth.headers"
python3 - "$scratch/visitor-a.json" "$visitor_a_cookie" <<'PY'
import sys
assert sys.argv[2] not in open(sys.argv[1], encoding="utf-8").read()
PY
cp "$scratch/visitor-a.json" "$evidence/visitor-start.json"

request visitor-continuity POST "/api/v1/ai/sessions/$visitor_a_id/messages" 200 \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: integration-visitor-turn-1' \
  -H "@$scratch/visitor-a-auth.headers" --data '{"message":"查询开放馆藏"}'
assert_json "$scratch/visitor-continuity.json" 'value["code"] == "MLS-AI-S001" and value["data"]["quota"]["remaining"] == 2'

request wrong-cookie GET "/api/v1/ai/sessions/$visitor_a_id/events?limit=20" 404 \
  -H 'Cookie: __Host-mls-ai-visitor=wrong-integration-credential'
assert_json "$scratch/wrong-cookie.json" 'value["code"] == "MLS-AI-005"'

# Create a real reader session through the public API.
username="integration-reader-$run_id"
password='Integration-Pass-42!'
printf '{"username":"%s","password":"%s","displayName":"Integration Reader"}\n' \
  "$username" "$password" > "$scratch/register-body.json"
request register POST /api/v1/auth/registrations 200 -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: integration-register' \
  --data-binary "@$scratch/register-body.json"
reader_id="$(json_at "['data']['accountId']" < "$scratch/register.json")"
printf '{"username":"%s","password":"%s"}\n' "$username" "$password" > "$scratch/login-body.json"
request login POST /api/v1/auth/sessions 200 --private-response -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: integration-login' \
  --data-binary "@$scratch/login-body.json"
reader_token="$(json_at "['data']['accessToken']" < "$scratch/login.json")"
remember_secret "$reader_token"
printf 'Authorization: Bearer %s\n' "$reader_token" > "$scratch/reader-auth.headers"
printf 'Authorization: Bearer %s\nCookie: __Host-mls-ai-visitor=%s\n' \
  "$reader_token" "$visitor_a_cookie" > "$scratch/visitor-a-bearer-auth.headers"

# A bearer accompanying the visitor cookie must not upgrade the stored visitor session.
request visitor-with-bearer POST "/api/v1/ai/sessions/$visitor_a_id/messages" 200 \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: integration-visitor-turn-2' \
  -H "@$scratch/visitor-a-bearer-auth.headers" \
  --data '{"message":"查询我的借阅"}'
assert_json "$scratch/visitor-with-bearer.json" 'value["code"] == "MLS-AI-S001" and value["data"]["quota"]["remaining"] == 1'

# With one shared visitor call left, two different visitors race: exactly one succeeds.
start_visitor() {
  local name="$1" key="$2"
  curl --silent --show-error --request POST "$base/api/v1/ai/sessions" -H "Idempotency-Key: $key" \
    -D "$scratch/$name.headers" -o "$scratch/$name.json"
  python3 - "$scratch/$name.headers" "$scratch/$name.json" <<'PY'
import json,re,sys
h=open(sys.argv[1],encoding="utf-8").read(); b=json.load(open(sys.argv[2],encoding="utf-8"))
print(b["data"]["sessionId"]); print(re.search(r'(?im)^set-cookie:\s*__Host-mls-ai-visitor=([^;]+);',h).group(1))
PY
}
mapfile -t vb < <(start_visitor visitor-b integration-visitor-b)
mapfile -t vc < <(start_visitor visitor-c integration-visitor-c)
remember_secret "${vb[1]}"
remember_secret "${vc[1]}"
printf 'Cookie: __Host-mls-ai-visitor=%s\n' "${vb[1]}" > "$scratch/visitor-b-auth.headers"
printf 'Cookie: __Host-mls-ai-visitor=%s\n' "${vc[1]}" > "$scratch/visitor-c-auth.headers"
concurrent_turn() {
  local name="$1" session="$2" header_file="$3"
  curl --silent --show-error --request POST "$base/api/v1/ai/sessions/$session/messages" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: integration-$name-turn" \
    -H "@$header_file" --data '{"message":"查询开放馆藏"}' \
    -o "$scratch/$name-turn.json" -w '%{http_code}' > "$scratch/$name.status"
}
concurrent_turn visitor-b "${vb[0]}" "$scratch/visitor-b-auth.headers" & pid_b=$!
concurrent_turn visitor-c "${vc[0]}" "$scratch/visitor-c-auth.headers" & pid_c=$!
wait "$pid_b"; wait "$pid_c"
statuses="$(sort "$scratch/visitor-b.status" "$scratch/visitor-c.status" | paste -sd, -)"
test "$statuses" = "200,429"
cp "$scratch/visitor-b-turn.json" "$evidence/visitor-b-turn.json"
cp "$scratch/visitor-c-turn.json" "$evidence/visitor-c-turn.json"
printf 'visitor concurrent statuses=%s\n' "$statuses" > "$evidence/visitor-concurrency.txt"

# Fixture only: establish a real LENT transaction for the registered reader.
dc exec -T database psql -U library -d library -v ON_ERROR_STOP=1 \
  -v reader_id="$reader_id" <<'SQL' > "$evidence/renewal-fixture.txt"
INSERT INTO book_title(id,title,price_cents) OVERRIDING SYSTEM VALUE VALUES (2001,'Integration Systems',5000);
INSERT INTO book_copy(id,title_id,copy_no,status) OVERRIDING SYSTEM VALUE VALUES (3001,2001,'INT-3001','LENT');
INSERT INTO borrow_transaction(id,reader_id,title_id,copy_id,status,reservation_date,due_date)
OVERRIDING SYSTEM VALUE VALUES (4001,:reader_id,2001,3001,'LENT',current_date-5,current_date+2);
SELECT 'fixture-ready';
SQL

request account-ai-start POST /api/v1/ai/sessions 200 -H 'Idempotency-Key: integration-account-ai' \
  -H "@$scratch/reader-auth.headers"
account_session="$(json_at "['data']['sessionId']" < "$scratch/account-ai-start.json")"
request l2-prefill POST "/api/v1/ai/sessions/$account_session/messages" 200 \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: integration-account-turn' \
  -H "@$scratch/reader-auth.headers" --data '{"message":"帮我预填续借"}'
assert_json "$scratch/l2-prefill.json" 'value["data"]["actions"][0]["level"] == "L2" and value["data"]["actions"][0]["prefill"]["txId"] == "4001"'

due_date="$(date -u -d '+10 days' +%F)"
request renewal-submit POST /api/v1/loans/4001/renewals 200 \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: integration-renewal-submit' \
  -H "@$scratch/reader-auth.headers" --data "{\"requestedDueDate\":\"$due_date\"}"
assert_json "$scratch/renewal-submit.json" 'value["data"]["outcome"] == "RENEWED" and value["data"]["txId"] == "4001"'

# Recreate the application/model containers while preserving PostgreSQL, then observe prior facts through HTTP.
dc stop backend frontend
dc up -d --wait --wait-timeout 180
address="$(dc port frontend 80)"; base="http://$address"
request events-after-restart GET "/api/v1/ai/sessions/$visitor_a_id/events?limit=100" 200 \
  -H "@$scratch/visitor-a-auth.headers"
assert_json "$scratch/events-after-restart.json" '[item for item in value["data"]["items"] if item["type"] == "MODEL_RESPONSE"] != []'
request quota-after-restart POST "/api/v1/ai/sessions/$visitor_a_id/messages" 429 \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: integration-after-restart' \
  -H "@$scratch/visitor-a-auth.headers" --data '{"message":"查询开放馆藏"}'
assert_json "$scratch/quota-after-restart.json" 'value["code"] == "MLS-AI-002" and value["data"]["remaining"] == 0'

request model-stats GET /model-stub/stats 200
assert_json "$scratch/model-stats.json" 'value["invalidThinking"] == 0 and value["requests"] >= 5 and value["renewalToolCalls"] == 1'

# The same scanner runs again from cleanup after logs and result metadata are written.
scripts/check-sensitive-evidence.sh "$evidence" "$secrets"
printf 'HTTP integration passed: proxy, anonymous ownership, global quota race, L2 renewal, restart durability.\n' \
  | tee "$evidence/summary.txt"
