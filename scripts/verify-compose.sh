#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null
docker info >/dev/null
# Each invocation owns two unique projects. Never reuse a developer project/volume.
run_id="$(date -u +%Y%m%d%H%M%S)-$$"
project_a="mls-check-${run_id}-a"
project_b="mls-check-${run_id}-b"
project="$project_a"
export DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
export AI_VISITOR_COOKIE_SIGNING_KEY="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
export FRONTEND_PORT=0
# Ignore any local .env; all inputs used by this check are defined above.
dc() { docker compose --env-file /dev/null -p "$project" -f compose.yaml "$@"; }
evidence="${EVIDENCE_DIR:-$PWD/artifacts/compose-$run_id}"
mkdir -p "$evidence"
evidence="$(cd "$evidence" && pwd)"
cleanup() {
  result=$?
  trap - EXIT
  set +e
  for project in "$project_a" "$project_b"; do
    dc logs --no-color 2>&1 | python3 -c 'import os,sys; data=sys.stdin.read(); data=data.replace(os.environ["DB_PASSWORD"], "[REDACTED]"); data=data.replace(os.environ["AI_VISITOR_COOKIE_SIGNING_KEY"], "[REDACTED]"); sys.stdout.write(data)' > "$evidence/$project.log"
    dc ps -a > "$evidence/$project-services.txt" 2>&1
    dc down --volumes --remove-orphans --timeout 20 >> "$evidence/cleanup.log" 2>&1
    cleanup_result=$?
    if (( cleanup_result != 0 && result == 0 )); then result=$cleanup_result; fi
  done
  printf 'exit_code=%s\n' "$result" > "$evidence/result.txt"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
{
  git rev-parse HEAD
  docker version
  docker compose version
} > "$evidence/environment.txt"
dc config --quiet
bash scripts/test-database-readiness.sh > "$evidence/readiness-regression.txt" 2>&1
dc build --pull
sql() { dc exec -T database psql -U library -d library -v ON_ERROR_STOP=1 -Atc "$1"; }
check() {
  phase="$1"
  dc ps --format json > "$evidence/$phase-services.json"
  dc images --format json > "$evidence/$phase-images.json"
  for container_id in $(dc ps -q); do
    docker inspect --format '{{.Name}} {{.Image}} {{.Config.Image}}' "$container_id"
    image_ref="$(docker inspect --format '{{.Config.Image}}' "$container_id")"
    docker image inspect --format '{{json .RepoDigests}} {{.Id}}' "$image_ref"
  done > "$evidence/$phase-image-digests.txt"
  for service in database backend frontend; do
    container_id="$(dc ps -q "$service")"
    test "$(docker inspect --format '{{.State.Health.Status}}' "$container_id")" = healthy
  done
  address="$(dc port frontend 80)"
  curl --fail --silent --show-error --connect-timeout 3 --max-time 15 "http://$address/" > "$evidence/$phase-index.html"
  # Check that the delivered HTML references a real JS bundle containing the state page.
  asset="$(python3 -c 'import re,sys; print(re.search(r"src=\"([^\"]+\.js)\"",open(sys.argv[1]).read())[1])' "$evidence/$phase-index.html")"
  curl --fail --silent --show-error --connect-timeout 3 --max-time 15 "http://$address$asset" > "$evidence/$phase-app.js"
  python3 -c 'import sys; assert "工程基础准备中" in open(sys.argv[1]).read()' "$evidence/$phase-app.js"
  curl --fail --silent --show-error --connect-timeout 3 --max-time 15 "http://$address/api/health" > "$evidence/$phase-health.json"
  python3 -c 'import json,sys; assert json.load(open(sys.argv[1])) == {"status":"UP"}' "$evidence/$phase-health.json"
  test "$(sql 'SELECT 1')" = 1
  test "$(sql "SELECT count(*) FROM flyway_schema_history WHERE script = 'R__infrastructure_probe.sql' AND success")" = 1
  test "$(sql 'SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success')" = 9
  test "$(sql \"SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL AND success\")" = "1,2,3,4,5,6,7,8,9"
  sql 'SELECT installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success FROM flyway_schema_history ORDER BY installed_rank' > "$evidence/$phase-history.txt"
  sql 'SHOW server_version' > "$evidence/$phase-postgres-version.txt"
  printf '%s: three healthy services; page, proxy, SQL and migration assertions passed\n' "$phase"
}
dc up -d --wait --wait-timeout 180
check first-empty
# Recreate application containers with the same named database volume.
dc down --timeout 20
dc up -d --wait --wait-timeout 180
check retained-volume
cmp "$evidence/first-empty-history.txt" "$evidence/retained-volume-history.txt"
project="$project_b"
dc config --quiet
# Use the very same built images for the second empty database.
dc up --no-build -d --wait --wait-timeout 180
check second-empty
printf 'Compose verification passed. Evidence: %s\n' "$evidence"
