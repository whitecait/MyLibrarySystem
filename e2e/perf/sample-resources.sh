#!/usr/bin/env bash
# Resource sampler for the M6 performance protocol (RA-0013 §11 saturation
# criteria; six dimensions: CPU, memory, GC, thread pool, connection pool,
# database lock waits). Samples every INTERVAL seconds until SIGTERM:
#   docker-stats.csv    per-container CPU% and memory (docker stats)
#   metrics-samples.log backend /actuator/prometheus extracts (GC, pools)
#   db-samples.csv      lock waiters + deadlocks (pg_locks/pg_stat_database)
#
# Attempt isolation: <out-dir> must be a fresh per-attempt directory (one is
# created per run-protocol.sh invocation). The sampler refuses to start in a
# directory that already holds samples, so one invocation can never append
# into another invocation's series. In the AT-0016 phase-4 evidence this
# guard did not exist and only the CSVs were rebuilt per invocation while
# metrics-samples.log was append-only, which mixed an abandoned/interrupted
# attempt into the next attempt's metrics (see
# artifacts/m6-acceptance/phase4/at-0016/CONTAMINATION-NOTE.md).
#
#   bash e2e/perf/sample-resources.sh <compose-project> <out-dir> [interval-s]
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

project="${1:?usage: sample-resources.sh <compose-project> <out-dir> [interval-s]}"
out_dir="${2:?usage: sample-resources.sh <compose-project> <out-dir> [interval-s]}"
interval="${3:-5}"
mkdir -p "$out_dir"

compose() {
  # Same pinned overlay set + optional ENV_FILE as run-protocol.sh: exec
  # needs the base file's required vars (DB_PASSWORD...) to pass validation.
  docker compose -p "$project" \
    -f compose.yaml -f compose.observability.yaml -f compose.perf-quota.yaml \
    ${ENV_FILE:+--env-file "$ENV_FILE"} "$@"
}

for f in docker-stats.csv db-samples.csv metrics-samples.log; do
  if [[ -e "${out_dir}/${f}" ]]; then
    echo "sample-resources: ${out_dir}/${f} already exists; refusing to append into another invocation's samples — pass a fresh per-attempt directory" >&2
    exit 3
  fi
done

echo "timestamp,container,cpu_pct,mem_used,mem_limit" > "$out_dir/docker-stats.csv"
echo "timestamp,lock_waiters,deadlocks" > "$out_dir/db-samples.csv"
: > "$out_dir/metrics-samples.log"

while true; do
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' \
    | grep "^${project}-" | while IFS=$'\t' read -r name cpu mem; do
        mem_used="${mem%%/*}"
        mem_limit="${mem##*/}"
        printf '%s,%s,%s,%s,%s\n' "$ts" "$name" "$cpu" "$mem_used" "$mem_limit"
      done >> "$out_dir/docker-stats.csv"

  compose exec -T database psql -U library -d library -tA -c \
    "SELECT (SELECT count(*) FROM pg_locks WHERE NOT granted), (SELECT deadlocks FROM pg_stat_database WHERE datname='library');" \
    2>/dev/null | { IFS='|' read -r waiters deadlocks || true; printf '%s,%s,%s\n' "$ts" "${waiters:-}" "${deadlocks:-}" >> "$out_dir/db-samples.csv"; } || true

  {
    echo "# SAMPLE $ts"
    compose exec -T backend curl -s http://localhost:8080/actuator/prometheus 2>/dev/null \
      | grep -E '^(jvm_gc_pause_seconds|hikaricp_connections_(active|max)|tomcat_threads_(busy|config_max))' || true
  } >> "$out_dir/metrics-samples.log" 2>/dev/null || true

  sleep "$interval" &
  wait "$!" || true
done
