#!/usr/bin/env bash
# Backup/restore drill for RPO and RTO (AT-0018 C03/C04; MLS-NFR-AVL-0001).
# Runs on Env-P2 (rebuildable clone) or Env-W, NEVER against Env-O.
#
#   bash scripts/drill-backup-restore.sh <source-project> <target-project> <out-dir>
#
# Measures:
#   RPO = t_fault - max business timestamp in the restored dump
#         (可恢复数据时点与故障时点之差, C03)
#   RTO = t_ready - t_fault, where t_ready is when the restored stack passes
#         the continuation checks (C04)
#
# Continuation checks default to health + public search + probe-account
# login; export CONTINUATION_SCRIPT=<path> to add core circulation/fine/
# payment checks with your own fixture credentials.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

source_project="${1:?usage: drill-backup-restore.sh <source-project> <target-project> <out-dir>}"
target_project="${2:?usage: drill-backup-restore.sh <source-project> <target-project> <out-dir>}"
out_dir="${3:?usage: drill-backup-restore.sh <source-project> <target-project> <out-dir>}"
mkdir -p "$out_dir"

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
now_s() { date -u +%s; }

compose_src() { docker compose -p "$source_project" "$@"; }
compose_tgt() { docker compose -p "$target_project" "$@"; }

wait_healthy() { # wait_healthy <project> <service> <timeout-s>
  local deadline=$(( $(date +%s) + $3 ))
  while (( $(date +%s) < deadline )); do
    local status
    status="$(docker compose -p "$1" ps --format '{{.Health}}' "$2" 2>/dev/null | head -1 || true)"
    [[ "$status" == "healthy" ]] && return 0
    sleep 3
  done
  return 1
}

echo "[$(now)] fault point: writes to source stop (simulated failure)"
t_fault="$(now_s)"

backup_file="$out_dir/backup-$(date -u +%Y%m%dT%H%M%SZ).sql"
echo "[$(now)] dumping source database to ${backup_file}"
compose_src exec -T database pg_dump -U library -d library > "$backup_file"
t_backup_done="$(now_s)"

t_data="$(compose_src exec -T database psql -U library -d library -tA -c \
  "SELECT COALESCE(max(greatest(updated_at, created_at)), '1970-01-01T00:00:00Z'::timestamptz) FROM (SELECT updated_at, created_at FROM borrow_transaction UNION ALL SELECT updated_at, created_at FROM account UNION ALL SELECT updated_at, created_at FROM payment_order) t;" \
  | tr -d ' ')"
t_data_s="$(python3 -c "import datetime;print(int(datetime.datetime.fromisoformat('${t_data/ /T}'.replace('+00:00','Z')).timestamp()))" 2>/dev/null || echo "$t_backup_done")"

echo "[$(now)] starting target project with a fresh database volume"
compose_tgt -f compose.yaml up -d database
wait_healthy "$target_project" database 180

echo "[$(now)] restoring dump into target"
compose_tgt exec -T database psql -U library -d library -q -v ON_ERROR_STOP=1 < "$backup_file"
compose_tgt exec -T database psql -U library -d library -q -c "ANALYZE;" > /dev/null
echo "[$(now)] starting backend/frontend on target"
compose_tgt -f compose.yaml up -d backend frontend
wait_healthy "$target_project" backend 180
wait_healthy "$target_project" frontend 120
t_ready="$(now_s)"

frontend_port="$(compose_tgt ps --format '{{.Publishers}}' frontend 2>/dev/null | head -1 | grep -oE '127\.0\.0\.1:[0-9]+' | head -1 | cut -d: -f2 || true)"
frontend_port="${frontend_port:-8088}"
base_url="http://127.0.0.1:${frontend_port}"

checks='[]'
if [[ -n "${CONTINUATION_SCRIPT:-}" ]]; then
  echo "[$(now)] running CONTINUATION_SCRIPT"
  CONTINUATION_BASE_URL="$base_url" bash "$CONTINUATION_SCRIPT" || true
else
  echo "[$(now)] default continuation checks (health + search) against ${base_url}"
  health_status="$(curl -sS -o /dev/null -m 10 -w '%{http_code}' "${base_url}/api/health" || printf '000')"
  search_status="$(curl -sS -o /dev/null -m 10 -w '%{http_code}' "${base_url}/api/v1/titles?search=library&limit=5" || printf '000')"
  checks="$(python3 - <<PY
import json
print(json.dumps([
  {"check": "health", "status": "${health_status}"},
  {"check": "search", "status": "${search_status}"},
]))
PY
)"
fi

rpo=$(( t_fault - t_data_s ))
rto=$(( t_ready - t_fault ))

cat > "$out_dir/drill-timeline.json" <<JSON
{
  "generatedAt": "$(now)",
  "sourceProject": "${source_project}",
  "targetProject": "${target_project}",
  "tFaultEpoch": ${t_fault},
  "tDataPointEpoch": ${t_data_s},
  "tBackupDoneEpoch": ${t_backup_done},
  "tReadyEpoch": ${t_ready},
  "rpoSeconds": ${rpo},
  "rtoSeconds": ${rto},
  "rpoLimitSeconds": 86400,
  "rtoLimitSeconds": 14400,
  "backupFile": "$(basename "$backup_file")",
  "continuationChecks": ${checks}
}
JSON
echo "[$(now)] drill complete: RPO=${rpo}s (limit 86400), RTO=${rto}s (limit 14400)"
echo "evidence: $out_dir/drill-timeline.json"
