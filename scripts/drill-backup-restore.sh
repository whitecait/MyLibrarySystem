#!/usr/bin/env bash
# Backup/restore drill for RPO and RTO (AT-0018 C03/C04; MLS-NFR-AVL-0001).
# Intended host: Env-P2 (rebuildable clone) or Env-W. Env-O (compose project
# m6-observation, published ports 8091/19091/13001) is refused IN CODE — by
# project name, by resolved published ports, and by docker volume labels —
# before anything is measured. (2026-09-21 R09 rework: before this rework the
# "NEVER against Env-O" rule existed only in this comment.)
#
#   bash scripts/drill-backup-restore.sh \
#     --source <compose-project> --target <compose-project> --out <dir> \
#     [--env-file <file>] [--search-keyword <word>]
#
# Measurement order — every step (command + key output + timepoint) is
# recorded in <out>/drill-timeline.json:
#   1. pg_dump the source database               -> temporary restore input;
#      only its bytes/hash metadata and a structure-sanitized evidence copy
#      are retained (the raw dump is removed by the EXIT trap on normal exits;
#      SIGKILL limits are documented below)
#   2. `compose stop backend frontend` on the SOURCE project (real stop, not a
#      marker); t_fault is taken after the stop returns
#   3. wipe the target project's volumes (`down -v`), start its database,
#      restore the dump with psql -v ON_ERROR_STOP=1
#   4. t_data = max business timestamp read back FROM THE RESTORED TARGET DB
#      as a raw epoch (pre-R09 defect: it was read from the live source DB,
#      and a parse failure was silently replaced by the backup-finish time)
#   5. start target backend/frontend, run continuation checks (below)
#   6. t_ready is taken only AFTER every check passed (pre-R09 defect: it was
#      recorded before the checks ran)
#
# Continuation checks — defaults, all must pass; any failure (or any missing
# measurement, e.g. an unparseable t_data) aborts the drill with a non-zero
# exit and no substitute values (pre-R09 defects: `|| true` on the custom
# script, health/search status codes never asserted):
#   health             GET  /api/health                          -> 2xx
#   search             GET  /api/v1/titles?q=<keyword>&limit=50  -> 2xx + non-empty items,
#                      at least one of them availability=AVAILABLE (the write
#                      path reserves THAT title; no AVAILABLE title means the
#                      write path cannot be proven and the drill fails)
#   login              POST /api/v1/auth/sessions                -> 2xx + accessToken
#   reservation-write  POST /api/v1/reservations (business write)-> 2xx + txId,
#                      then cancelled (second asserted business write)
# Extra checks: point CONTINUATION_SCRIPT at a script; it receives
# CONTINUATION_BASE_URL / CONTINUATION_TOKEN / CONTINUATION_ACCOUNT and its
# failure fails the drill (results land in continuation-custom.log).
#
# Required credentials: DRILL_ACCOUNT / DRILL_PASSWORD name a READER account
# that exists in the SOURCE database (provision it before drilling).
#
# Guard rails (exit code 3 on violation):
#   - --source/--target must not be the Env-O project name (m6-observation);
#   - the target frontend port must not be 8091/19091/13001 (Env-O ports);
#   - the target database volume must not exist labeled m6-observation; an
#     existing target volume labeled by any OTHER project is also refused;
#   - the target frontend port must be free on the host (except containers of
#     the target project itself, which `down -v` removes); a live source
#     frontend publishing the same port is refused;
#   - after the target stack starts, its ACTUAL published port must equal the
#     resolved one and must not be an Env-O port.
#
# Host ports are read from the daemon — `docker inspect .NetworkSettings.Ports`
# rendered as JSON and parsed with python3 — because neither of the two
# formatter fields that look obvious actually yields a "IP:PORT" pair here:
# the Docker CLI formatter has no `.Publishers` field (it fails with rc=1,
# "can't evaluate field Publishers in type *formatter.ContainerContext"), and
# Compose v5 renders `{{.Publishers}}` as a Go struct (`[{127.0.0.1 80 18091
# tcp}]`), not as text. Both were used before this rework: the first made the
# port-holder scan fail silently (empty output was read as "port free"), the
# second could never be parsed (so the drill died before t_ready). `docker ps`
# is still used for the container LIST, with only the documented `.Names`,
# `.ID` and `.Label` fields. A read whose emptiness would be read as "check
# passed" (the port-holder scan) is FATAL on failure (exit 1): the drill never
# records `port free` from a check it could not run.
# Configuration (exit code 2): FRONTEND_PORT must resolve from the shell
#   environment or the env file — no default port is guessed (a guessed port
#   could collide with the source stack).
#
# Exit codes: 0 drill passed (RPO/RTO within limits); 1 drill/measurement
# failure (including a port read that could not be performed); 2 usage or
# credential errors; 3 Env-O/conflict guard rejection.
#
# `|| true` appears in exactly three places — the health poll and the two
# target-frontend reads whose emptiness is fatal right below (see the comment
# at each site) — where it only tolerates the pipe-close status of a status
# read cut short by `head -1`; every value captured there is strictly
# validated immediately after, and no check result or measurement is ever
# replaced or swallowed.
#
# The drill wipes the TARGET project's compose volumes before restoring —
# only pass a throwaway/clone project as --target. The SOURCE stack is left
# stopped after the drill; restart it yourself:
#   docker compose -p <source> up -d
set -Eeuo pipefail
cd "$(dirname "$0")/.."

ENV_O_PROJECT="m6-observation"
ENV_O_PORTS="8091 19091 13001"
RPO_LIMIT_SECONDS=86400
RTO_LIMIT_SECONDS=14400

usage() {
  cat <<'EOF'
Usage:
  bash scripts/drill-backup-restore.sh
      --source <compose-project>   source stack whose database is dumped and
                                   whose backend/frontend are stopped (must
                                   not be m6-observation)
      --target <compose-project>   restore target; its volumes ARE WIPED
                                   (down -v) before the restore; must differ
                                   from --source and must not be
                                   m6-observation
      --out <dir>                  fresh evidence directory (an existing
                                   drill-timeline.json there is refused)
    [ --env-file <file> ]           env file for the target stack up
                                   (default: .env); FRONTEND_PORT must resolve
                                   from the environment or this file — no
                                   default port is guessed — and must not be
                                   an Env-O port (8091/19091/13001)
    [ --search-keyword <word> ]     keyword for the search / write-path check
                                   (default: library)

Environment:
  DRILL_ACCOUNT / DRILL_PASSWORD   required; READER account present in the
                                   source database
  CONTINUATION_SCRIPT              optional extra check script; gets
                                   CONTINUATION_BASE_URL, CONTINUATION_TOKEN,
                                   CONTINUATION_ACCOUNT; failure fails the drill

Exit codes: 0 pass; 1 drill/measurement failure; 2 usage/credential;
3 Env-O/conflict guard rejection.
EOF
}

usage_error() { # usage_error <message>
  printf 'usage error: %s\n\n' "$1" >&2
  usage >&2
  # record the real cause for the timeline the EXIT trap writes (no-op before
  # verdict_reasons is initialized — argument errors may fire that early)
  verdict_reasons="${verdict_reasons:-}${verdict_reasons:+$'\n'}usage error: $1"
  exit 2
}

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
now_s() { date -u +%s; }

# ---------------------------------------------------------------- arguments --
source_project="" target_project="" out_dir="" env_file=".env" search_keyword="library"
print_help=0
initial_argc=$#
while (( $# )); do
  case "$1" in
    --source)          [[ $# -ge 2 ]] || usage_error "--source needs a value";  source_project="$2"; shift 2 ;;
    --source=*)        source_project="${1#*=}"; shift ;;
    --target)          [[ $# -ge 2 ]] || usage_error "--target needs a value";  target_project="$2"; shift 2 ;;
    --target=*)        target_project="${1#*=}"; shift ;;
    --out)             [[ $# -ge 2 ]] || usage_error "--out needs a value";     out_dir="$2"; shift 2 ;;
    --out=*)           out_dir="${1#*=}"; shift ;;
    --env-file)        [[ $# -ge 2 ]] || usage_error "--env-file needs a value"; env_file="$2"; shift 2 ;;
    --env-file=*)      env_file="${1#*=}"; shift ;;
    --search-keyword)  [[ $# -ge 2 ]] || usage_error "--search-keyword needs a value"; search_keyword="$2"; shift 2 ;;
    --search-keyword=*) search_keyword="${1#*=}"; shift ;;
    --help|-h)         print_help=1; shift ;;
    *)                 usage_error "unknown argument: $1" ;;
  esac
done

if (( print_help == 1 )); then
  usage
  exit 0
fi
if (( initial_argc == 0 )); then
  printf 'usage error: no arguments; --source/--target/--out are required\n\n' >&2
  usage >&2
  exit 2
fi

[[ -n "$source_project" ]] || usage_error "--source is required"
[[ -n "$target_project" ]] || usage_error "--target is required"
[[ -n "$out_dir" ]]        || usage_error "--out is required"
[[ "$source_project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || usage_error "--source must match [a-z0-9][a-z0-9_-]*"
[[ "$target_project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || usage_error "--target must match [a-z0-9][a-z0-9_-]*"
[[ "$source_project" != "$target_project" ]]     || usage_error "--source and --target must differ"
[[ -n "$search_keyword" ]] || usage_error "--search-keyword must not be empty"
[[ -r "$env_file" ]] || usage_error "--env-file '$env_file' is not a readable file"

mkdir -p "$out_dir"
if [[ -e "$out_dir/drill-timeline.json" ]]; then
  usage_error "'$out_dir/drill-timeline.json' already exists; use a fresh --out directory (existing evidence is never overwritten)"
fi

for tool in docker python3 curl sha256sum; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'preflight error: required tool not found: %s\n' "$tool" >&2
    exit 1
  fi
done

# ------------------------------------------------------------- timeline I/O --
steps_log="$out_dir/drill-steps.jsonl"
checks_log="$out_dir/drill-checks.jsonl"
: > "$steps_log"
: > "$checks_log"

verdict_reasons=""
add_reason() { verdict_reasons+="${verdict_reasons:+$'\n'}$1"; }

record_step() { # record_step <name> <status> <command> <detail>
  STEPS_LOG="$steps_log" \
  STEP_NAME="$1" STEP_STATUS="$2" STEP_CMD="$3" STEP_DETAIL="$4" \
  STEP_EPOCH="$(now_s)" STEP_ISO="$(now)" \
  python3 - <<'PY' || printf 'warning: failed to record step %s\n' "$1" >&2
import json, os
rec = {
    "name": os.environ["STEP_NAME"],
    "status": os.environ["STEP_STATUS"],
    "command": os.environ["STEP_CMD"],
    "detail": os.environ["STEP_DETAIL"],
    "epoch": int(os.environ["STEP_EPOCH"]),
    "at": os.environ["STEP_ISO"],
}
with open(os.environ["STEPS_LOG"], "a", encoding="utf-8") as f:
    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
PY
}

record_check() { # record_check <check> <0|1 ok> <detail>
  CHECKS_LOG="$checks_log" \
  CHK_NAME="$1" CHK_OK="$2" CHK_DETAIL="$3" CHK_AT="$(now)" \
  python3 - <<'PY' || printf 'warning: failed to record check %s\n' "$1" >&2
import json, os
rec = {
    "check": os.environ["CHK_NAME"],
    "ok": os.environ["CHK_OK"] == "1",
    "detail": os.environ["CHK_DETAIL"],
    "at": os.environ["CHK_AT"],
}
with open(os.environ["CHECKS_LOG"], "a", encoding="utf-8") as f:
    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
PY
}

timeline_written=0
write_timeline() { # write_timeline <status> <verdict> ; scalars via globals
  local tl_ok=0
  TIMELINE_OUT="$out_dir/drill-timeline.json" \
  TIMELINE_STEPS="$steps_log" TIMELINE_CHECKS="$checks_log" \
  RUN_GENERATED_AT="$(now)" RUN_STATUS="${1:-failed}" RUN_VERDICT="${2:-fail}" \
  RUN_REASONS="$verdict_reasons" \
  RUN_SOURCE_PROJECT="$source_project" RUN_TARGET_PROJECT="$target_project" \
  RUN_ENV_FILE="$env_file" RUN_SEARCH_KEYWORD="$search_keyword" \
  RUN_T_BACKUP_DONE="${t_backup_done:-}" RUN_T_FAULT="${t_fault:-}" \
  RUN_T_DATA="${t_data:-}" RUN_T_READY="${t_ready:-}" \
  RUN_RPO="${rpo:-}" RUN_RTO="${rto:-}" \
  RUN_RPO_LIMIT="$RPO_LIMIT_SECONDS" RUN_RTO_LIMIT="$RTO_LIMIT_SECONDS" \
  RUN_BACKUP_RESTORE_FILE="${backup_restore_file:-}" \
  RUN_BACKUP_RESTORE_KIND="${backup_restore_file_kind:-}" \
  RUN_BACKUP_RESTORE_BYTES="${backup_bytes:-}" \
  RUN_BACKUP_RESTORE_SHA="${backup_sha:-}" \
  RUN_BACKUP_RAW_RETAINED="${backup_raw_retained:-false}" \
  RUN_BACKUP_EVIDENCE_FILE="${backup_evidence_file:-}" \
  RUN_BACKUP_EVIDENCE_BYTES="${backup_evidence_bytes:-}" \
  RUN_BACKUP_EVIDENCE_SHA="${backup_evidence_sha:-}" \
  RUN_BACKUP_EVIDENCE_METADATA="${backup_evidence_metadata:-}" \
  RUN_BACKUP_SNAPSHOT_REDACTED="${backup_snapshot_fields_redacted:-}" \
  RUN_BASE_URL="${base_url:-}" RUN_FRONTEND_PORT="${frontend_port:-}" \
  RUN_CUSTOM_SCRIPT="${CONTINUATION_SCRIPT:-}" RUN_CUSTOM_RC="${custom_rc:-}" \
  python3 - <<'PY' && tl_ok=1
import json, os, tempfile

def read_jsonl(path):
    out = []
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    out.append(json.loads(line))
                except ValueError:
                    out.append({"unparsed": line})
    except OSError:
        pass
    return out

def num(name):
    v = (os.environ.get(name) or "").strip()
    return int(v) if v.isdigit() else None

def st(name):
    return os.environ.get(name) or None

def boolv(name):
    return (os.environ.get(name) or "").strip().lower() == "true"

steps = read_jsonl(os.environ["TIMELINE_STEPS"])
checks = read_jsonl(os.environ["TIMELINE_CHECKS"])
obj = {
    "schema": "drill-timeline/2",
    "generatedAt": os.environ["RUN_GENERATED_AT"],
    "status": os.environ["RUN_STATUS"],
    "verdict": os.environ["RUN_VERDICT"],
    "verdictReasons": [r for r in os.environ["RUN_REASONS"].split("\n") if r],
    "sourceProject": os.environ["RUN_SOURCE_PROJECT"],
    "targetProject": os.environ["RUN_TARGET_PROJECT"],
    "envFile": os.environ["RUN_ENV_FILE"],
    "searchKeyword": os.environ["RUN_SEARCH_KEYWORD"],
    "tBackupDoneEpoch": num("RUN_T_BACKUP_DONE"),
    "tFaultEpoch": num("RUN_T_FAULT"),
    "tDataPointEpoch": num("RUN_T_DATA"),
    "tReadyEpoch": num("RUN_T_READY"),
    "rpoSeconds": num("RUN_RPO"),
    "rtoSeconds": num("RUN_RTO"),
    "rpoLimitSeconds": int(os.environ["RUN_RPO_LIMIT"]),
    "rtoLimitSeconds": int(os.environ["RUN_RTO_LIMIT"]),
    # The restore input is temporary and is never a path to retained evidence.
    # Its hash/bytes identify the exact input used for the measurement.
    "backupRestoreFile": st("RUN_BACKUP_RESTORE_FILE"),
    "backupRestoreFileKind": st("RUN_BACKUP_RESTORE_KIND") or "temporary-unretained",
    "backupRestoreBytes": num("RUN_BACKUP_RESTORE_BYTES"),
    "backupRestoreSha256": st("RUN_BACKUP_RESTORE_SHA"),
    "backupRawRetained": boolv("RUN_BACKUP_RAW_RETAINED"),
    # These fields identify the separate, structure-sanitized evidence copy.
    "backupEvidenceFile": st("RUN_BACKUP_EVIDENCE_FILE"),
    "backupEvidenceBytes": num("RUN_BACKUP_EVIDENCE_BYTES"),
    "backupEvidenceSha256": st("RUN_BACKUP_EVIDENCE_SHA"),
    "backupEvidenceMetadataFile": st("RUN_BACKUP_EVIDENCE_METADATA"),
    "backupEvidenceFormat": "postgres-copy-structured-sanitized",
    "backupSnapshotFieldsRedacted": num("RUN_BACKUP_SNAPSHOT_REDACTED"),
    "baseUrl": st("RUN_BASE_URL"),
    "frontendPort": st("RUN_FRONTEND_PORT"),
    "sourceLeftStopped": True,
    "customContinuationScript": st("RUN_CUSTOM_SCRIPT"),
    "customContinuationExit": num("RUN_CUSTOM_RC"),
    "continuationChecks": checks,
    "steps": steps,
}
timeline_out = os.environ["TIMELINE_OUT"]
timeline_dir = os.path.dirname(timeline_out) or "."
fd, tmp = tempfile.mkstemp(
    prefix=f".{os.path.basename(timeline_out)}.", suffix=".tmp", dir=timeline_dir
)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    # The final path is owned by this drill and may be intentionally rewritten
    # by die()/on_exit after a cleanup failure; the temp path is unique per
    # write, so stale fixed-name temp files cannot be overwritten.
    os.replace(tmp, timeline_out)
except Exception:
    try:
        os.unlink(tmp)
    except FileNotFoundError:
        pass
    raise
PY
  if (( tl_ok == 1 )); then
    timeline_written=1
  else
    printf 'error: failed to write %s\n' "$out_dir/drill-timeline.json" >&2
  fi
  return 0
}

die() { # die <message>  (drill/measurement failure, exit 1)
  printf '[%s] DRILL FAILED: %s\n' "$(now)" "$1" >&2
  add_reason "$1"
  record_step "failure" "failed" "" "$1"
  write_timeline "failed" "fail"
  exit 1
}

guard_fail() { # guard_fail <message>  (Env-O/conflict rejection, exit 3)
  printf '[%s] GUARD REJECTED: %s\n' "$(now)" "$1" >&2
  add_reason "guard rejection: $1"
  record_step "precheck" "failed" "" "$1"
  write_timeline "failed" "fail"
  exit 3
}

fail_check() { # fail_check <check-name> <detail>  (continuation failure, exit 1)
  record_check "$1" 0 "$2"
  printf '[%s] continuation check failed: %s: %s\n' "$(now)" "$1" "$2" >&2
  add_reason "continuation check '$1' failed: $2"
  write_timeline "failed" "fail"
  exit 1
}

SCRATCH_FILES=()
SCRATCH_DIRS=()
SCRATCH_FILE_RESULT=""
scratch_dir=""
scratch_seq=0
scratch_file() { # sets SCRATCH_FILE_RESULT to a pre-registered private path
  local f
  f="$scratch_dir/scratch-$scratch_seq"
  scratch_seq=$((scratch_seq + 1))
  SCRATCH_FILES+=("$f")
  ( umask 077; : > "$f" ) || return 1
  SCRATCH_FILE_RESULT="$f"
}

on_exit() {
  local rc=$?
  local f d cleanup_failed=0 cleanup_count=0
  for f in "${SCRATCH_FILES[@]:-}"; do
    [[ -n "$f" ]] || continue
    [[ -e "$f" ]] || continue
    cleanup_count=$((cleanup_count + 1))
    if ! rm -f -- "$f"; then
      cleanup_failed=1
    fi
  done
  for d in "${SCRATCH_DIRS[@]:-}"; do
    [[ -n "$d" && -d "$d" ]] || continue
    cleanup_count=$((cleanup_count + 1))
    if ! rmdir -- "$d"; then
      cleanup_failed=1
    fi
  done
  if (( cleanup_failed == 1 )); then
    add_reason "scratch cleanup failed for one or more temporary files"
    # Cleanup failure means the raw input may still exist.  The timeline must
    # not assert false even when the failing item was a credential scratch
    # file or the private directory itself.
    backup_raw_retained="true"
    backup_restore_file_kind="temporary-cleanup-failed"
    record_step "failure" "failed" "temporary scratch cleanup" \
      "cleanup failed for at least one of ${cleanup_count} temporary file(s)"
    # A successful drill cannot remain a pass when its raw/credential scratch
    # cleanup failed.  Rewrite the timeline as failed while preserving all
    # measurements and the original reason rows.
    timeline_written=0
    write_timeline "failed" "fail"
    rc=1
  fi
  if (( timeline_written == 0 )); then
    add_reason "drill aborted unexpectedly (exit $rc) before writing a timeline"
    write_timeline "failed" "fail"
    if (( timeline_written == 0 )); then
      printf 'error: no drill-timeline.json could be written\n' >&2
    else
      printf '[%s] partial failed timeline written\n' "$(now)" >&2
    fi
  fi
  exit "$rc"
}
trap on_exit EXIT

# Use a private, project-pair-specific directory.  Its path is registered
# before mkdir/file creation, so a normal signal between creation and cleanup
# cannot lose the path.  A SIGKILL can still leave the directory behind; a
# later run refuses only this exact source/target prefix and never deletes
# another process's files.
scratch_tmp_root="${TMPDIR:-/tmp}"
scratch_prefix="$scratch_tmp_root/mls-drill-backup-${source_project}-${target_project}-"
stale_scratch_count="$(SCRATCH_TMP_ROOT="$scratch_tmp_root" SCRATCH_PREFIX="$(basename "$scratch_prefix")" python3 - <<'PY'
import os
from pathlib import Path

root = Path(os.environ["SCRATCH_TMP_ROOT"])
prefix = os.environ["SCRATCH_PREFIX"]
try:
    print(sum(1 for entry in root.iterdir() if entry.name.startswith(prefix)))
except OSError as exc:
    raise SystemExit(f"cannot inspect scratch root: {exc}")
PY
)" || die "cannot inspect temporary backup scratch root"
if [[ ! "$stale_scratch_count" =~ ^0$ ]]; then
  die "refusing to start: ${stale_scratch_count} stale scratch path(s) match this source/target raw-backup prefix; inspect and remove only after confirming ownership"
fi
scratch_dir="${scratch_prefix}${BASHPID}-${RANDOM}"
SCRATCH_DIRS+=("$scratch_dir")
if ! ( umask 077; mkdir -- "$scratch_dir" ); then
  die "cannot create private temporary backup scratch directory"
fi
compose_src() { docker compose -p "$source_project" "$@"; }
compose_tgt() { docker compose -p "$target_project" --env-file "$env_file" -f compose.yaml "$@"; }

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

container_host_ports() { # container_host_ports <container-ref> -> published HOST ports, one per line
  # Daemon-side fact via `docker inspect` JSON (same source `docker port` reads);
  # rc=1 on any failure so callers can distinguish "no host port" from "could
  # not read". See the header for why no formatter field is used here.
  local ref="$1" pubjson
  pubjson="$(docker inspect --format '{{json .NetworkSettings.Ports}}' "$ref")" || return 1
  CONTAINER_PORTS_JSON="$pubjson" python3 - <<'PY'
import json, os, sys

try:
    ports = json.loads(os.environ["CONTAINER_PORTS_JSON"])
except ValueError:
    sys.exit("docker inspect returned unparsable .NetworkSettings.Ports JSON")
for bindings in (ports or {}).values():
    for binding in (bindings or []):
        host_port = (binding or {}).get("HostPort")
        if host_port:
            print(host_port)
PY
}

http_call() { # http_call <body-out-file> <curl args...> ; echoes http code (000 on transport failure)
  local body_file="$1"; shift
  curl -sS -m 15 -o "$body_file" -w '%{http_code}' "$@" 2>/dev/null || printf '000'
}

# ------------------------------------------------------- recorded start row --
record_step "start" "ok" "bash scripts/drill-backup-restore.sh --source $source_project --target $target_project --out $out_dir --env-file $env_file --search-keyword $search_keyword" "DRILL_ACCOUNT=${DRILL_ACCOUNT:-<unset>}"

# ------------------------------------------------- Env-O name-level rejection --
if [[ "$source_project" == "$ENV_O_PROJECT" ]]; then
  guard_fail "--source '$source_project' is the Env-O observation project; drilling against Env-O is forbidden (stopping its stack would interrupt the observation evidence)"
fi
if [[ "$target_project" == "$ENV_O_PROJECT" ]]; then
  guard_fail "--target '$target_project' is the Env-O observation project; the drill wipes target volumes and Env-O must never be touched"
fi

# ------------------------------------------- target port resolution + guard --
# same precedence as docker compose: shell environment beats the env file
frontend_port=""
if [[ -n "${FRONTEND_PORT:-}" ]]; then
  frontend_port="$FRONTEND_PORT"
elif grep -qE '^FRONTEND_PORT=' "$env_file"; then
  frontend_port="$(grep -E '^FRONTEND_PORT=' "$env_file" | tail -1 | cut -d= -f2 | tr -d '[:space:]')"
fi
if [[ -z "$frontend_port" ]]; then
  usage_error "FRONTEND_PORT could not be resolved from the environment or '$env_file'; set it explicitly for the drill target (no default port is guessed — a guessed port could collide with the source stack)"
fi
if [[ ! "$frontend_port" =~ ^[0-9]+$ ]]; then
  usage_error "resolved FRONTEND_PORT '$frontend_port' (env-file $env_file) is not numeric"
fi
for p in $ENV_O_PORTS; do
  if [[ "$frontend_port" == "$p" ]]; then
    guard_fail "target FRONTEND_PORT $frontend_port is an Env-O port (Env-O uses: $ENV_O_PORTS); choose a dedicated port for the drill target in $env_file"
  fi
done
record_step "precheck" "ok" "resolve FRONTEND_PORT from environment/env-file" \
  "target project=$target_project frontend_port=$frontend_port env_file=$env_file target_volume=${target_project}_database"

# -------------------------------------------------------- credential checks --
if [[ -z "${DRILL_ACCOUNT:-}" || -z "${DRILL_PASSWORD:-}" ]]; then
  die "DRILL_ACCOUNT/DRILL_PASSWORD are required (a READER account that exists in the source database); continuation checks cannot run without them"
fi
if [[ -n "${CONTINUATION_SCRIPT:-}" && ! -r "$CONTINUATION_SCRIPT" ]]; then
  die "CONTINUATION_SCRIPT '$CONTINUATION_SCRIPT' is not readable"
fi

# --------------------------------------------------- docker-dependent checks --
if ! docker info >/dev/null 2>&1; then
  die "docker daemon unreachable; preflight cannot verify guard rails"
fi

src_ps_out=""
src_ps_out="$(compose_src ps 2>/dev/null)" || src_ps_out=""
src_db_cid=""
src_db_cid="$(compose_src ps -q database 2>/dev/null)" || src_db_cid=""
if [[ -z "$src_db_cid" ]]; then
  die "source project '$source_project' has no running database container; cannot take the backup. 'docker compose -p $source_project ps': ${src_ps_out:-<empty>}"
fi
record_step "precheck" "ok" "docker compose -p $source_project ps -q database" "source database running; ps output: $(printf '%s' "$src_ps_out" | tr '\n' ';' | cut -c1-400)"

# live source frontend port must not collide with the target port.
# An empty container id means "source frontend not running" (verified: `compose
# ps -q` exits 0 with empty output for a service that is not up) = nothing
# published = no conflict; a non-zero compose status is fatal (never a silent
# skip of the guard).
if ! src_frontend_cid="$(compose_src ps -q frontend 2>/dev/null)"; then
  die "docker compose -p $source_project ps -q frontend failed; the source/target port-conflict guard cannot be evaluated"
fi
src_frontend_cid="${src_frontend_cid%%$'\n'*}"
src_frontend_pub=""
if [[ -n "$src_frontend_cid" ]]; then
  if ! src_frontend_ports="$(container_host_ports "$src_frontend_cid")"; then
    die "could not read the published ports of the source frontend container $src_frontend_cid via docker inspect; the source/target port-conflict guard cannot be evaluated"
  fi
  while IFS= read -r hp; do
    [[ -n "$hp" ]] || continue
    if [[ "$hp" == "$frontend_port" ]]; then
      src_frontend_pub="$hp"
    fi
  done <<< "$src_frontend_ports"
fi
if [[ -n "$src_frontend_pub" && "$src_frontend_pub" == "$frontend_port" ]]; then
  guard_fail "source frontend already publishes $src_frontend_pub, which equals the resolved target port; pick a different FRONTEND_PORT for the target in $env_file"
fi

# target port must be free on the host, except the target project's own
# containers (those are removed by `down -v` right before the restore).
# Every read here is fatal on failure: an empty result means "no holder", so a
# read that did not run must never be mistaken for a free port.
scratch_file
scan_err="$SCRATCH_FILE_RESULT"
if ! ps_scan_out="$(docker ps --format '{{.Names}}\t{{.ID}}\t{{.Label "com.docker.compose.project"}}' 2>"$scan_err")"; then
  die "docker ps failed while checking whether target port $frontend_port is free: $(tail -1 "$scan_err")"
fi
port_holders=""
scanned=0
while IFS=$'\t' read -r cname cid cproj; do
  [[ -n "$cname" ]] || continue
  scanned=$(( scanned + 1 ))
  [[ "$cproj" == "$target_project" ]] && continue
  if ! holder_ports="$(container_host_ports "$cid")"; then
    die "could not read the published ports of running container $cname ($cid) via docker inspect; refusing to record 'port $frontend_port free' from a check that could not run"
  fi
  while IFS= read -r hp; do
    [[ -n "$hp" ]] || continue
    if [[ "$hp" == "$frontend_port" ]]; then
      port_holders+="${cname}(project=${cproj:-none}) "
    fi
  done <<< "$holder_ports"
done <<< "$ps_scan_out"
if [[ -n "$port_holders" ]]; then
  guard_fail "target port $frontend_port is already published by: ${port_holders%; }"
fi

# volume-level Env-O rejection + ambiguous-ownership rejection
tgt_volume="${target_project}_database"
if docker volume inspect "$tgt_volume" >/dev/null 2>&1; then
  vol_label="$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$tgt_volume")"
  if [[ "$vol_label" == "$ENV_O_PROJECT" ]]; then
    guard_fail "target volume $tgt_volume is labeled com.docker.compose.project=$ENV_O_PROJECT (Env-O); refusing to wipe it"
  fi
  if [[ "$vol_label" != "$target_project" ]]; then
    guard_fail "target volume $tgt_volume exists but is labeled '$vol_label' (expected '$target_project'); refusing to wipe an ambiguously owned volume"
  fi
fi
while IFS= read -r v; do
  [[ -z "$v" ]] && continue
  if [[ "$v" == "$tgt_volume" ]]; then
    guard_fail "Env-O volume set (label com.docker.compose.project=$ENV_O_PROJECT) contains $v; refusing"
  fi
done < <(docker volume ls --format '{{.Name}}' --filter "label=com.docker.compose.project=$ENV_O_PROJECT")
record_step "precheck" "ok" "docker ps + docker inspect .NetworkSettings.Ports scan + docker volume label checks" \
  "port $frontend_port free (running containers scanned: $scanned); volume $tgt_volume not Env-O-owned; source frontend published port: ${src_frontend_pub:-<none>}"

# ============================ measurement phase =============================

# 1) backup — the recoverable data point.  The raw pg_dump is a scratch input
# for restore only.  The path is registered before the redirection creates the
# file, so every normal exit path removes it.
backup_file="$scratch_dir/raw-backup.sql"
SCRATCH_FILES+=("$backup_file")
backup_restore_file=""
backup_restore_file_kind="temporary-unretained"
backup_raw_retained="false"
backup_evidence_file=""
backup_evidence_metadata=""
backup_evidence_bytes=""
backup_evidence_sha=""
backup_snapshot_fields_redacted=""
record_step "backup" "running" "docker compose -p $source_project exec -T database pg_dump -U library -d library" "-> temporary restore input (raw dump is not retained)"
compose_src exec -T database pg_dump -U library -d library > "$backup_file" \
  || die "pg_dump of source database failed"
backup_bytes="$(wc -c < "$backup_file")"
if (( backup_bytes == 0 )); then
  die "pg_dump produced an empty file; backup is not a recoverable point"
fi
backup_sha="$(sha256sum "$backup_file" | cut -d' ' -f1)"
t_backup_done="$(now_s)"
record_step "backup" "ok" "docker compose -p $source_project exec -T database pg_dump -U library -d library" \
  "temporary input bytes=$backup_bytes sha256=$backup_sha t_backup_done=$t_backup_done"

# Keep only a structure-sanitized copy in the evidence directory.  The
# sanitizer identifies the target COPY block and column from its header and
# replaces the complete response_snapshot field; it does not search for a
# token-shaped substring.  A sanitizer failure is a drill failure, and the
# raw temporary input is still removed by on_exit.
backup_evidence_file="$out_dir/backup-sanitized.sql"
backup_evidence_metadata="$out_dir/backup-sanitization.json"
record_step "backup-sanitize" "running" \
  "python3 scripts/sanitize-idempotency-snapshots.py <temporary restore input> $backup_evidence_file" \
  "rawRetained=false"
if ! python3 scripts/sanitize-idempotency-snapshots.py "$backup_file" \
  "$backup_evidence_file" --metadata "$backup_evidence_metadata" \
  > "$out_dir/backup-sanitization.stdout.log" 2>&1; then
  die "structured backup evidence sanitization failed; raw restore input will be removed"
fi
if [[ ! -s "$backup_evidence_file" || ! -s "$backup_evidence_metadata" ]]; then
  die "structured backup evidence sanitization produced missing/empty output"
fi
backup_evidence_bytes="$(wc -c < "$backup_evidence_file")"
backup_evidence_sha="$(sha256sum "$backup_evidence_file" | cut -d' ' -f1)"
backup_snapshot_fields_redacted="$(python3 - "$backup_evidence_metadata" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    value = json.load(f).get("responseSnapshotFieldsRedacted")
if not isinstance(value, int) or value < 0:
    raise SystemExit("invalid responseSnapshotFieldsRedacted metadata")
print(value)
PY
)" || die "structured backup evidence metadata is unreadable"
record_step "backup-sanitize" "ok" \
  "python3 scripts/sanitize-idempotency-snapshots.py <temporary restore input> $backup_evidence_file" \
  "rawRetained=false sanitizedBytes=$backup_evidence_bytes sanitizedSha256=$backup_evidence_sha responseSnapshotFieldsRedacted=$backup_snapshot_fields_redacted"

# 2) real source-write stop, then t_fault
record_step "stop-source" "running" "docker compose -p $source_project stop backend frontend" "stopping source business writes"
compose_src stop backend frontend > "$out_dir/stop-source.log" 2>&1 \
  || die "stopping source backend/frontend failed: $(tail -3 "$out_dir/stop-source.log" | tr '\n' ' ')"
t_fault="$(now_s)"
record_step "stop-source" "ok" "docker compose -p $source_project stop backend frontend" \
  "source writes stopped; t_fault=$t_fault (source stack left stopped for the rest of the drill)"

# 3) fresh target database + restore
record_step "target-fresh-volume" "running" "docker compose -p $target_project --env-file $env_file -f compose.yaml down -v --remove-orphans" "wiping target project volumes"
tgt_down_out=""
tgt_down_out="$(compose_tgt down -v --remove-orphans 2>&1)" \
  || die "wiping target project volumes failed; cannot guarantee a fresh restore volume: $(printf '%s' "$tgt_down_out" | tr '\n' ';' | cut -c1-300)"
record_step "target-fresh-volume" "ok" "docker compose -p $target_project --env-file $env_file -f compose.yaml down -v --remove-orphans" \
  "output: $(printf '%s' "$tgt_down_out" | tr '\n' ';' | cut -c1-400)"

compose_tgt up -d database > "$out_dir/target-db-up.log" 2>&1 \
  || die "starting target database failed: $(tail -3 "$out_dir/target-db-up.log" | tr '\n' ' ')"
if ! wait_healthy "$target_project" database 180; then
  die "target database did not become healthy within 180s"
fi
record_step "target-database-up" "ok" "docker compose -p $target_project up -d database; wait_healthy database 180" "target database healthy"

record_step "restore" "running" "docker compose -p $target_project exec -T database psql -U library -d library -q -v ON_ERROR_STOP=1 < <temporary restore input>" "restoring temporary raw dump"
if ! compose_tgt exec -T database psql -U library -d library -q -v ON_ERROR_STOP=1 < "$backup_file" > "$out_dir/restore.log" 2>&1; then
  die "restore failed (psql ON_ERROR_STOP): $(tail -3 "$out_dir/restore.log" | tr '\n' ' ')"
fi
compose_tgt exec -T database psql -U library -d library -q -c "ANALYZE;" >/dev/null 2>&1 \
  || die "ANALYZE on restored target database failed"
record_step "restore" "ok" "psql -v ON_ERROR_STOP=1 < <temporary restore input>" "restore completed without error; raw input is not retained; log: $(basename "$out_dir")/restore.log"

# Optional data verification runs before application jobs or continuation writes.
# The checker owns its expected fixture snapshot; an unset hook preserves the
# original drill. A failure stops the drill before any continuation is claimed.
if [[ -n "${RESTORE_DATA_CHECK_SCRIPT:-}" ]]; then
  [[ -r "$RESTORE_DATA_CHECK_SCRIPT" ]] || die "RESTORE_DATA_CHECK_SCRIPT is not readable"
  record_step "restore-data-check" "running" "bash $RESTORE_DATA_CHECK_SCRIPT" "before target application startup"
  if ! RESTORE_SOURCE_PROJECT="$source_project" RESTORE_TARGET_PROJECT="$target_project" RESTORE_ENV_FILE="$env_file" \
    bash "$RESTORE_DATA_CHECK_SCRIPT" > "$out_dir/restore-data-check.log" 2>&1; then
    die "restored fixture data check failed; see restore-data-check.log"
  fi
  record_step "restore-data-check" "ok" "bash $RESTORE_DATA_CHECK_SCRIPT" "fixture counts and row relations verified before continuation"
fi

# 4) t_data — read back from the RESTORED target database as a raw epoch
t_data_sql="SELECT extract(epoch FROM COALESCE(max(greatest(updated_at, created_at)), 'epoch'::timestamptz))::bigint FROM (SELECT updated_at, created_at FROM borrow_transaction UNION ALL SELECT updated_at, created_at FROM account UNION ALL SELECT updated_at, created_at FROM payment_order) t;"
t_data_raw=""
t_data_raw="$(compose_tgt exec -T database psql -U library -d library -tA -c "$t_data_sql")" || t_data_raw=""
if [[ ! "$t_data_raw" =~ ^[0-9]+$ ]]; then
  die "t_data measurement missing: restored target database returned '$t_data_raw' instead of an epoch integer (no substitute value is used)"
fi
t_data="$t_data_raw"
if (( t_data == 0 )); then
  die "restored dump contains no business data (max business timestamp is epoch); RPO is not measurable"
fi
now_epoch="$(now_s)"
if (( t_data > now_epoch + 60 )); then
  die "t_data ($t_data) is in the future relative to the host clock; measurement invalid"
fi
if (( t_data > t_backup_done + 60 )); then
  die "t_data ($t_data) is newer than the backup completion time ($t_backup_done); measurement invalid"
fi
record_step "t-data" "ok" "psql -tA -c \"$t_data_sql\" (against restored target database)" \
  "t_data=$t_data (epoch); t_backup_done=$t_backup_done"

# 5) target application stack
compose_tgt up -d backend frontend > "$out_dir/target-app-up.log" 2>&1 \
  || die "starting target backend/frontend failed: $(tail -3 "$out_dir/target-app-up.log" | tr '\n' ' ')"
if ! wait_healthy "$target_project" backend 180; then
  die "target backend did not become healthy within 180s"
fi
if ! wait_healthy "$target_project" frontend 120; then
  die "target frontend did not become healthy within 120s"
fi
record_step "target-stack-up" "ok" "docker compose -p $target_project up -d backend frontend; wait_healthy backend/frontend" "target stack healthy"

# Both reads below tolerate only the pipe-close status of a status read that
# `head -1` cuts short (`|| true`); the captured values are strictly validated
# immediately after — an empty or non-numeric value is a fatal failure and no
# fallback port is ever guessed.
tgt_frontend_cid="$(compose_tgt ps -q frontend 2>/dev/null | head -1 || true)"
if [[ -z "$tgt_frontend_cid" ]]; then
  die "target frontend container is not running (docker compose -p $target_project ps -q frontend returned nothing); cannot verify its published port (no fallback port is guessed)"
fi
frontend_port_actual="$(container_host_ports "$tgt_frontend_cid" 2>/dev/null | head -1 || true)"
if [[ ! "$frontend_port_actual" =~ ^[0-9]+$ ]]; then
  die "could not read a published host port for the target frontend container $tgt_frontend_cid via docker inspect (got '$frontend_port_actual'); no fallback port is guessed"
fi
for p in $ENV_O_PORTS; do
  if [[ "$frontend_port_actual" == "$p" ]]; then
    guard_fail "target frontend came up publishing $frontend_port_actual, an Env-O port (Env-O uses: $ENV_O_PORTS); aborting"
  fi
done
if [[ "$frontend_port_actual" != "$frontend_port" ]]; then
  die "target frontend actually publishes $frontend_port_actual but the precheck resolved $frontend_port from $env_file; port governance inconsistent, aborting"
fi
base_url="http://127.0.0.1:${frontend_port_actual}"
record_step "target-stack-up" "ok" "docker inspect --format '{{json .NetworkSettings.Ports}}' $tgt_frontend_cid (published host port of service frontend)" "base_url=$base_url"

# 6) continuation checks — every one must pass; t_ready only after all of them
run_id="$(date -u +%Y%m%d%H%M%S)-$$"
scratch_file
cred_file="$SCRATCH_FILE_RESULT"
DRILL_ACCOUNT="$DRILL_ACCOUNT" DRILL_PASSWORD="$DRILL_PASSWORD" python3 - > "$cred_file" \
  <<'PY' || die "could not serialize credentials"
import json, os
print(json.dumps({"username": os.environ["DRILL_ACCOUNT"],
                  "password": os.environ["DRILL_PASSWORD"]}))
PY

# 6a. health
health_code="$(http_call /dev/null "${base_url}/api/health")"
if [[ ! "$health_code" =~ ^2 ]]; then
  fail_check "health" "GET ${base_url}/api/health returned HTTP ${health_code}, expected 2xx"
fi
record_check "health" 1 "GET ${base_url}/api/health -> HTTP ${health_code}"

# 6b. search (public portal, contract parameter is q=)
scratch_file
search_body="$SCRATCH_FILE_RESULT"
search_code="$(http_call "$search_body" -G --data-urlencode "q=${search_keyword}" --data-urlencode "limit=50" "${base_url}/api/v1/titles")"
if [[ ! "$search_code" =~ ^2 ]]; then
  fail_check "search" "GET /api/v1/titles?q=${search_keyword} returned HTTP ${search_code}, expected 2xx"
fi
search_pick="$(SEARCH_BODY="$search_body" python3 - <<'PY' || printf 'PARSE_ERROR'
import json, os
try:
    items = json.load(open(os.environ["SEARCH_BODY"], encoding="utf-8"))["data"]["items"]
    pick = next((i for i in items if i.get("availability") == "AVAILABLE"), None)
    if pick is None:
        print("EMPTY" if not items else "NO_AVAILABLE")
    else:
        print(f"{len(items)} {pick['titleId']}")
except Exception:
    print("PARSE_ERROR")
PY
)"
if [[ "$search_pick" == "PARSE_ERROR" || "$search_pick" == "EMPTY" ]]; then
  fail_check "search" "search response unusable (HTTP ${search_code}, items: ${search_pick})"
fi
if [[ "$search_pick" == "NO_AVAILABLE" ]]; then
  fail_check "reservation-create" "precondition unmet: search for '${search_keyword}' returned items but none with availability=AVAILABLE; the write path cannot be proven without a borrowable title"
fi
search_count="$(printf '%s' "$search_pick" | cut -d' ' -f1)"
write_title_id="$(printf '%s' "$search_pick" | cut -d' ' -f2)"
record_check "search" 1 "GET /api/v1/titles?q=${search_keyword}&limit=50 -> HTTP ${search_code}, items=${search_count}, write-path title=${write_title_id}"

# 6c. login
scratch_file
login_body="$SCRATCH_FILE_RESULT"
login_code="$(http_call "$login_body" -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: drill-${run_id}-login" --data-binary "@${cred_file}" \
  "${base_url}/api/v1/auth/sessions")"
if [[ ! "$login_code" =~ ^2 ]]; then
  fail_check "login" "POST /api/v1/auth/sessions returned HTTP ${login_code} for account ${DRILL_ACCOUNT}, expected 2xx"
fi
token="$(LOGIN_BODY="$login_body" python3 - <<'PY' || printf ''
import json, os
try:
    print(json.load(open(os.environ["LOGIN_BODY"], encoding="utf-8"))["data"]["accessToken"])
except Exception:
    pass
PY
)"
if [[ -z "$token" ]]; then
  fail_check "login" "login HTTP ${login_code} but no data.accessToken in the response envelope"
fi
record_check "login" 1 "POST /api/v1/auth/sessions -> HTTP ${login_code}, accessToken issued for ${DRILL_ACCOUNT}"

# 6d. reservation create + cancel (two asserted business writes)
scratch_file
reserve_body="$SCRATCH_FILE_RESULT"
RESERVE_TITLE_ID="$write_title_id" python3 - > "$reserve_body" <<'PY' || die "could not serialize reservation body"
import datetime, json, os
reservation_date = (datetime.date.today() + datetime.timedelta(days=2)).isoformat()
print(json.dumps({"titleId": os.environ["RESERVE_TITLE_ID"],
                  "reservationDate": reservation_date,
                  "pickupSlot": "PM", "channel": "ONLINE",
                  "confirmWaitlist": True}))
PY
scratch_file
create_body="$SCRATCH_FILE_RESULT"
create_code="$(http_call "$create_body" -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${token}" -H "Idempotency-Key: drill-${run_id}-reserve" \
  --data-binary "@${reserve_body}" "${base_url}/api/v1/reservations")"
if [[ ! "$create_code" =~ ^2 ]]; then
  fail_check "reservation-create" "POST /api/v1/reservations (title=${write_title_id}) returned HTTP ${create_code}, expected 2xx"
fi
tx_id="$(CREATE_BODY="$create_body" python3 - <<'PY' || printf ''
import json, os
try:
    data = json.load(open(os.environ["CREATE_BODY"], encoding="utf-8"))["data"]
    outcome = data.get("outcome")
    tx = data.get("txId")
    if tx:
        print(f"{tx} {outcome}")
except Exception:
    pass
PY
)"
if [[ -z "$tx_id" ]]; then
  fail_check "reservation-create" "reservation HTTP ${create_code} but no business transaction (data.txId) was created; write path not proven"
fi
tx_id_value="$(printf '%s' "$tx_id" | cut -d' ' -f1)"
tx_outcome="$(printf '%s' "$tx_id" | cut -d' ' -f2)"
record_check "reservation-create" 1 "POST /api/v1/reservations -> HTTP ${create_code}, outcome=${tx_outcome}, txId=${tx_id_value}"

scratch_file
cancel_body="$SCRATCH_FILE_RESULT"
printf '{"reason":"drill-continuation-release"}' > "$cancel_body"
cancel_code="$(http_call /dev/null -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${token}" -H "Idempotency-Key: drill-${run_id}-cancel" \
  --data-binary "@${cancel_body}" "${base_url}/api/v1/reservations/${tx_id_value}/cancel")"
if [[ ! "$cancel_code" =~ ^2 ]]; then
  fail_check "reservation-cancel" "POST /api/v1/reservations/${tx_id_value}/cancel returned HTTP ${cancel_code}, expected 2xx"
fi
record_check "reservation-cancel" 1 "POST /api/v1/reservations/${tx_id_value}/cancel -> HTTP ${cancel_code}"

# 6e. optional custom continuation script (failure is NOT swallowed)
custom_rc=""
if [[ -n "${CONTINUATION_SCRIPT:-}" ]]; then
  record_step "custom-continuation" "running" "bash $CONTINUATION_SCRIPT" "log: $(basename "$out_dir")/continuation-custom.log"
  if CONTINUATION_BASE_URL="$base_url" CONTINUATION_TOKEN="$token" CONTINUATION_ACCOUNT="$DRILL_ACCOUNT" \
    bash "$CONTINUATION_SCRIPT" > "$out_dir/continuation-custom.log" 2>&1; then
    custom_rc=0
  else
    custom_rc=$?
  fi
  if (( custom_rc != 0 )); then
    die "CONTINUATION_SCRIPT failed with exit ${custom_rc}: $(tail -3 "$out_dir/continuation-custom.log" | tr '\n' ' ')"
  fi
  record_step "custom-continuation" "ok" "bash $CONTINUATION_SCRIPT" "exit ${custom_rc}"
fi

# 7) t_ready — only after every continuation check passed
t_ready="$(now_s)"
record_step "t-ready" "ok" "recorded after all continuation checks passed" "t_ready=$t_ready"

# 8) verdict
rpo=$(( t_fault - t_data ))
rto=$(( t_ready - t_fault ))
if (( rpo < 0 )); then
  die "negative RPO (${rpo}s) — invalid measurement"
fi
verdict="pass"
if (( rpo > RPO_LIMIT_SECONDS )); then
  verdict="fail"
  add_reason "RPO ${rpo}s exceeds limit ${RPO_LIMIT_SECONDS}s"
fi
if (( rto > RTO_LIMIT_SECONDS )); then
  verdict="fail"
  add_reason "RTO ${rto}s exceeds limit ${RTO_LIMIT_SECONDS}s"
fi
write_timeline "completed" "$verdict"
if [[ "$verdict" != "pass" ]]; then
  printf '[%s] DRILL FAILED: verdict=%s\n' "$(now)" "$verdict" >&2
  exit 1
fi

printf '[%s] drill complete: RPO=%ss (limit %ss), RTO=%ss (limit %ss)\n' \
  "$(now)" "$rpo" "$RPO_LIMIT_SECONDS" "$rto" "$RTO_LIMIT_SECONDS"
printf 'source stack %s left STOPPED; restart with: docker compose -p %s up -d\n' \
  "$source_project" "$source_project"
printf 'evidence: %s/drill-timeline.json\n' "$out_dir"
