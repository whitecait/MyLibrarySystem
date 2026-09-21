#!/usr/bin/env bash
# Environment & quota evidence collector for the M6 acceptance windows
# (M6-ACCEPTANCE-PLAN 4.8 host-control evidence; R17 method-gap fix).
#
# A bare project-scoped container list cannot answer the four questions this
# collector covers, one run produces all of them inside --out:
#   1. daemon site & versions: docker context show/ls/inspect, docker version
#      (client+server), docker info, docker compose version;
#   2. host VM specs: CPU/memory/disk observed from the WSL distro side; the
#      daemon-side (Docker Desktop utility VM) CPU/memory figures ride in
#      docker info and are copied into env-snapshot.json;
#   3. whole-daemon container inventory and docker stats before/after the
#      window — the "no external load on the host" disclosure; not scoped to
#      the project on purpose;
#   4. per-container quota inspect for EVERY container carrying the label
#      com.docker.compose.project=<project>, including short-lived ones such
#      as k6 via poll mode (see below).
#
# Files written:
#   raw/                     raw command outputs (versions, context, info,
#                            compose version, host spec, system df, whole-
#                            daemon ps/stats before+after, capture order)
#   container-inspects/      one full docker inspect JSON per captured container
#   container-quotas.txt     one summary line per captured container:
#                            /name image=... NanoCpus=... Memory=... state=... first_seen=...
#   env-snapshot.json        machine-readable aggregation of everything above
#   run-log.txt              timestamped collector log
#   redaction-selfcheck.txt  result of the in-tree secret scan (see "Redaction")
#   collector.pid            PID of the collecting process
#   collector-done.txt       final marker: exit_code, stop_reason, finished_at
#                            and the authoritative post-write scan result; its
#                            presence is the end condition callers wait for
#   collector-console.log    stdout/stderr of a detached collector
#
# Usage:
#   bash scripts/collect-env-evidence.sh --project <compose-project> \
#        [--out <dir>] [--poll-seconds <n>] [--poll-interval <n>] \
#        [--secret-env-file <file>]... [--stop-file <path>] [--detach]
#
#   --project        compose project name; containers are filtered by
#                    label com.docker.compose.project=<name>
#   --out            output directory (default
#                    artifacts/m6-acceptance/env-evidence/<project>-<UTCstamp>)
#   --poll-seconds   poll duration; 0 (default) = single before/after capture
#   --poll-interval  seconds between poll passes (default 10)
#   --secret-env-file
#                    env-style file whose credential values are redacted from
#                    everything this run writes; repeatable. Default: every
#                    .env file at the repository root that exists
#                    (.env, .env.observation, .env.perf).
#   --stop-file      poll mode ends as soon as this path exists (default
#                    <out>/collector.stop). Ignored in single-shot mode.
#   --detach         start the collection as a session-detached background
#                    process and return immediately (see "Detached start")
#
# Detached start (D6 of the W2 fix batch)
# ---------------------------------------
# A collector started as an ordinary child of a round driver dies with that
# driver: the W2a window ended with the collector cut off by SIGHUP, and its
# env-snapshot.json / container-quotas.txt / after-inventory never appeared —
# the missing files had to be recomputed from the raw inspects afterwards.
# `--detach` re-executes this script under `setsid` (falling back to `nohup`
# when setsid is unavailable) with stdin from /dev/null, so it owns a new
# session and cannot be reached by a hangup aimed at the driver's session. The
# child writes:
#   - collector.pid        its own PID, immediately, before any collection
#   - collector-done.txt   on exit, carrying exit_code + stop_reason
# `--detach` returns only after collector.pid holds a live PID (it reports the
# PID on stdout); if that does not happen within 5 seconds it fails with exit 2
# and points at collector-console.log. Callers therefore have two end
# conditions to wait on: the done marker, and, for poll mode, the stop file
# that ends the poll window early. Poll mode is additionally bounded by
# --poll-seconds, so a forgotten call site cannot leave a poller running
# forever. `--detached-child` is the internal flag that tells the re-executed
# copy it is the detached child (it must not detach again).
#
# Redaction (D7 of the W2 fix batch)
# ----------------------------------
# `docker inspect` renders the container environment, which carries the stack's
# credentials (DB_PASSWORD, AI_VISITOR_COOKIE_SIGNING_KEY, ...). Writing that
# verbatim is how the W2a evidence tree ended up with .env credential values in
# six container inspect files that had to be redacted after the fact. This
# collector now redacts at the source: every stream it writes is filtered
# through scripts/redact-evidence-values.sh against the value set built by
# scripts/collect-evidence-secret-values.sh from the --secret-env-file files,
# replacing each literal value with [REDACTED-SECRET]. No value is ever echoed
# by this script. Afterwards it scans the whole output tree with
# scripts/check-sensitive-evidence.sh and records the result in
# redaction-selfcheck.txt; a residual value fails the run with exit 4, so a
# tree that still carries a credential can never be presented as clean.
# The temporary value-set file is created with mktemp and deleted on exit; it
# never lives inside the evidence tree.
#
# Exit codes: 0 ok; 2 setup failure (docker CLI missing / daemon unreachable /
# python3, rg or a scanner script missing / no env file to derive the value set
# from / the value set is empty / the detached collector did not come up);
# 3 output evidence already exists (refuses to overwrite — pick a fresh --out);
# 4 the redaction self-check found a credential value in the output tree, the
# tree must not be used as clean evidence; 64 usage error.
#
# Read-only by construction: the collector only runs version/context/info/ps/
# stats/system df/inspect. It never starts, stops or reconfigures anything.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

project="" out="" poll_seconds=0 poll_interval=10 stop_file="" detach="false" detached_child="false"
secret_env_files=()
while [ $# -gt 0 ]; do
  case "$1" in
    --project) project="${2:?--project needs a value}"; shift 2 ;;
    --out) out="${2:?--out needs a value}"; shift 2 ;;
    --poll-seconds) poll_seconds="${2:?--poll-seconds needs a value}"; shift 2 ;;
    --poll-interval) poll_interval="${2:?--poll-interval needs a value}"; shift 2 ;;
    --secret-env-file) secret_env_files+=("${2:?--secret-env-file needs a value}"); shift 2 ;;
    --stop-file) stop_file="${2:?--stop-file needs a value}"; shift 2 ;;
    --detach) detach="true"; shift ;;
    --detached-child) detached_child="true"; shift ;;
    -h|--help)
      sed -n '/^# Usage:/,/^set -Eeuo/p' "$0" | sed 's/^# \{0,1\}//' | sed '$d'
      exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done
[ -n "$project" ] || {
  echo "usage: bash scripts/collect-env-evidence.sh --project <compose-project> [--out <dir>] [--poll-seconds <n>] [--poll-interval <n>] [--secret-env-file <file>]... [--stop-file <path>] [--detach]" >&2
  exit 64
}

if ! command -v python3 >/dev/null 2>&1; then echo "python3 not found in PATH" >&2; exit 2; fi
if ! command -v docker >/dev/null 2>&1; then echo "docker CLI not found in PATH" >&2; exit 2; fi
if ! command -v rg >/dev/null 2>&1; then
  echo "rg (ripgrep) not found in PATH; the in-tree secret scan cannot run" >&2
  exit 2
fi
if ! docker version >/dev/null 2>&1; then echo "docker daemon unreachable (docker version failed)" >&2; exit 2; fi

redactor="scripts/redact-evidence-values.sh"
scanner="scripts/check-sensitive-evidence.sh"
value_collector="scripts/collect-evidence-secret-values.sh"
for helper in "$redactor" "$scanner" "$value_collector"; do
  [ -f "$helper" ] || { echo "required helper script is missing: $helper" >&2; exit 2; }
done

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
if [ -z "$out" ]; then
  out="artifacts/m6-acceptance/env-evidence/${project}-${stamp}"
fi
if [ -e "$out/env-snapshot.json" ]; then
  echo "refusing to overwrite existing evidence: $out/env-snapshot.json — pick a fresh --out" >&2
  exit 3
fi
[ -n "$stop_file" ] || stop_file="$out/collector.stop"
mkdir -p "$out/raw" "$out/container-inspects"

# The value set is read from the env files named on the command line, or from
# the repository's own .env files when none is named. Everything this script
# writes is filtered through it, so a run with nothing to filter against is a
# setup failure, not a silent no-op.
if (( ${#secret_env_files[@]} == 0 )); then
  for candidate in .env .env.observation .env.perf; do
    if [ -f "$candidate" ]; then
      secret_env_files+=("$candidate")
    fi
  done
fi
(( ${#secret_env_files[@]} > 0 )) || {
  echo "no env file to derive the redaction value set from (looked for .env, .env.observation, .env.perf); pass --secret-env-file" >&2
  exit 2
}

# --- detached start ----------------------------------------------------------
if [ "$detach" = "true" ] && [ "$detached_child" != "true" ]; then
  console_log="$out/collector-console.log"
  child_args=(--project "$project" --out "$out"
    --poll-seconds "$poll_seconds" --poll-interval "$poll_interval"
    --stop-file "$stop_file" --detached-child)
  for env_file in "${secret_env_files[@]}"; do
    child_args+=(--secret-env-file "$env_file")
  done
  : >> "$console_log"
  if command -v setsid >/dev/null 2>&1; then
    # setsid: the child owns a new session, so a SIGHUP aimed at this script's
    # session (the W2a failure) cannot reach it.
    setsid nohup bash "$0" "${child_args[@]}" >>"$console_log" 2>&1 </dev/null &
  else
    nohup bash "$0" "${child_args[@]}" >>"$console_log" 2>&1 </dev/null &
    disown 2>/dev/null || true
  fi
  child_pid=""
  for _ in $(seq 1 20); do
    if [ -s "$out/collector.pid" ]; then
      child_pid="$(cat "$out/collector.pid")"
      break
    fi
    sleep 0.25
  done
  if [ -z "$child_pid" ] || ! kill -0 "$child_pid" 2>/dev/null; then
    echo "detached collector did not report a live PID within 5s; see $console_log" >&2
    exit 2
  fi
  printf 'detached collector pid=%s out=%s console=%s poll_seconds=%s stop_file=%s\n' \
    "$child_pid" "$out" "$console_log" "$poll_seconds" "$stop_file"
  exit 0
fi

log_file="$out/run-log.txt"
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$log_file"; }

secret_values_file="$(mktemp "${TMPDIR:-/tmp}/collect-env-evidence-secrets.XXXXXX")"
chmod 600 "$secret_values_file"
work="$(mktemp -d "${TMPDIR:-/tmp}/collect-env-evidence-work.XXXXXX")"
stop_reason="single-shot"
post_write_scan_exit="not-run"
write_done_marker=1
finish() {
  local code=$?
  rm -f -- "$secret_values_file"
  rm -rf -- "$work"
  if [ "$write_done_marker" = "1" ]; then
    printf 'exit_code=%s stop_reason=%s post_write_scan_exit=%s finished_at=%s\n' \
      "$code" "$stop_reason" "$post_write_scan_exit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      > "$out/collector-done.txt"
  fi
  exit "$code"
}
trap finish EXIT

printf '%s\n' "$$" > "$out/collector.pid"

value_set_status=0
value_set_summary="$(bash "$value_collector" "${secret_env_files[@]}" "$secret_values_file" 2>&1)" || value_set_status=$?
if [ "$value_set_status" -ne 0 ]; then
  echo "building the redaction value set failed (exit ${value_set_status}): ${value_set_summary}" >&2
  exit 2
fi
if [ ! -s "$secret_values_file" ]; then
  echo "the redaction value set is empty (${value_set_summary}); refusing to collect evidence that could not be redacted" >&2
  exit 2
fi

log "collect-env-evidence start: project=$project out=$out poll_seconds=$poll_seconds poll_interval=$poll_interval stop_file=$stop_file detached=$detached_child"
log "redaction value set: ${value_collector} ${secret_env_files[*]} -> ${value_set_summary}"

raw="$out/raw"

# redact_stream: stdin -> stdout, with every credential value replaced by
# [REDACTED-SECRET]. Every write below goes through it, so a value cannot land
# in the evidence tree even transiently.
redact_stream() {
  bash "$redactor" --stdin "$secret_values_file"
}

# run_save <file> <command...>: run the command, store its redacted
# stdout+stderr in raw/<file>. Auxiliary evidence must not abort the run on
# failure, so a failing command keeps its (redacted) error output on disk and
# only logs a warning. A failing redactor does abort: writing unredacted
# output is exactly what this collector must not do.
run_save() {
  local f="$1"; shift
  local status=0
  { "$@" 2>&1 || status=$?; } | redact_stream > "$raw/$f"
  if [ "$status" -eq 0 ]; then
    log "saved raw/$f"
  else
    log "WARN: command failed (exit $status), error output kept in raw/$f: $*"
  fi
}

# --- 1. daemon site & versions ----------------------------------------------
run_save docker-client-version.txt docker version --format '{{.Client.Version}}'
run_save docker-server-version.txt docker version --format '{{.Server.Version}}'
run_save docker-version-full.txt   docker version
run_save docker-context-show.txt   docker context show
run_save docker-context-list.txt   docker context ls
run_save docker-context-inspect.txt bash -c 'docker context inspect "$(docker context show)"'
run_save docker-info.json          docker info --format '{{json .}}'
run_save docker-info-full.txt      docker info
run_save compose-version.txt       docker compose version --short
run_save compose-version-full.txt  docker compose version
run_save docker-system-df.txt      docker system df -v

# --- 2. host VM specs, WSL-distro side ---------------------------------------
# The daemon-side (Docker Desktop utility VM) CPU/memory figures are captured
# via docker info above; this file is the WSL distro observation for contrast.
{
  echo "=== uname ==="
  uname -a
  echo "=== cpu count (nproc) ==="
  nproc
  echo "=== cpu model ==="
  grep -m1 '^model name' /proc/cpuinfo || true
  echo "=== memory (/proc/meminfo MemTotal / MemAvailable) ==="
  grep -E '^Mem(Total|Available)' /proc/meminfo || true
  echo "=== disk (df -h /) ==="
  df -h /
  echo "=== disk (df -h .) ==="
  df -h .
  echo "=== note: daemon-side utility-VM CPU/memory figures are in raw/docker-info-full.txt (NCPU / Total Memory)"
} | redact_stream > "$raw/host-spec.txt"
log "saved raw/host-spec.txt"

# --- 3. whole-daemon inventory & stats, before -------------------------------
run_save ps-all-before.jsonl    docker ps -a --format '{{json .}}'
run_save stats-all-before.jsonl docker stats --no-stream --format '{{json .}}'

# --- 4. project container capture (label-filtered) ---------------------------
inspect_dir="$out/container-inspects"
order_file="$raw/container-capture-order.txt"
capture_new_containers() {
  local phase="$1" id
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    if [ -f "$inspect_dir/$id.json" ]; then continue; fi
    # The inspect carries the container environment: redact while writing.
    docker inspect "$id" | redact_stream > "$inspect_dir/$id.json"
    printf '%s\t%s\n' "$phase" "$id" >> "$order_file"
    log "captured container ${id:0:12} (phase=$phase)"
  done < <(docker ps -aq --filter "label=com.docker.compose.project=$project")
}
capture_new_containers before

# --- 5. poll mode: catch short-lived containers (k6) -------------------------
if [ "$poll_seconds" -gt 0 ]; then
  log "poll mode: capturing for ${poll_seconds}s, interval ${poll_interval}s, stop file ${stop_file}"
  deadline=$(( $(date +%s) + poll_seconds ))
  while :; do
    capture_new_containers poll
    if [ -e "$stop_file" ]; then
      stop_reason="stop-file"
      break
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      stop_reason="poll-deadline"
      break
    fi
    sleep "$poll_interval"
  done
  log "poll mode ended: ${stop_reason}"
fi
capture_new_containers after

# --- 6. whole-daemon inventory & stats, after --------------------------------
run_save ps-all-after.jsonl    docker ps -a --format '{{json .}}'
run_save stats-all-after.jsonl docker stats --no-stream --format '{{json .}}'

# --- 7. assemble env-snapshot.json + container-quotas.txt --------------------
summary="$(python3 - "$out" "$project" "$stamp" "$poll_seconds" "$poll_interval" <<'PYEOF'
import json, os, pathlib, shutil, sys

out = pathlib.Path(sys.argv[1]); project = sys.argv[2]
collected_at = sys.argv[3]
poll_seconds = int(sys.argv[4]); poll_interval = int(sys.argv[5])
raw = out / "raw"; inspects = out / "container-inspects"

def read_text(name):
    p = raw / name
    return p.read_text(encoding="utf-8").strip() if p.exists() else None

def read_jsonl(name):
    p = raw / name
    if not p.exists():
        return []
    items = []
    for line in p.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            items.append(json.loads(line))
        except ValueError:
            pass
    return items

info = {}
p = raw / "docker-info.json"
if p.exists():
    try:
        info = json.loads(p.read_text(encoding="utf-8"))
    except ValueError:
        info = {}

order = {}
p = raw / "container-capture-order.txt"
if p.exists():
    for line in p.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) == 2 and parts[1] not in order:
            order[parts[1]] = parts[0]

project_containers = []
for f in sorted(inspects.glob("*.json")):
    try:
        doc = json.loads(f.read_text(encoding="utf-8"))[0]
    except Exception:
        continue
    cfg = doc.get("Config") or {}
    hc = doc.get("HostConfig") or {}
    state = doc.get("State") or {}
    full_id = doc.get("Id") or ""
    labels = {k: v for k, v in (cfg.get("Labels") or {}).items()
              if k.startswith("com.docker.compose.")}
    project_containers.append({
        "id": full_id[:12],
        "inspect_file": str(f.relative_to(out)),
        "name": (doc.get("Name") or "").lstrip("/"),
        "image": cfg.get("Image"),
        "state": state.get("Status"),
        "created_at": doc.get("Created"),
        "started_at": state.get("StartedAt"),
        "finished_at": state.get("FinishedAt"),
        "nano_cpus": hc.get("NanoCpus"),
        "cpu_period": hc.get("CpuPeriod"),
        "cpu_quota": hc.get("CpuQuota"),
        "cpuset_cpus": hc.get("CpusetCpus"),
        "memory_bytes": hc.get("Memory"),
        "memory_swap_bytes": hc.get("MemorySwap"),
        "compose_labels": labels,
        "first_seen_phase": order.get(full_id, "before"),
    })

(out / "container-quotas.txt").write_text(
    "".join(
        "/{name} image={image} NanoCpus={nc} Memory={mem} state={state} first_seen={phase}\n".format(
            name=c["name"], image=c["image"], nc=c["nano_cpus"],
            mem=c["memory_bytes"], state=c["state"], phase=c["first_seen_phase"],
        )
        for c in project_containers
    ),
    encoding="utf-8",
)

def labels_parse(s):
    d = {}
    for part in (s or "").split(","):
        if "=" in part:
            k, v = part.split("=", 1)
            d[k.strip()] = v.strip()
    return d

def inventory(rows):
    return [{
        "name": (r.get("Names") or "").lstrip("/"),
        "image": r.get("Image"),
        "state": r.get("State"),
        "status": r.get("Status"),
        "compose_project": labels_parse(r.get("Labels")).get("com.docker.compose.project"),
    } for r in rows]

ps_before = read_jsonl("ps-all-before.jsonl")
ps_after = read_jsonl("ps-all-after.jsonl")

memtotal_kb = None
try:
    for line in pathlib.Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
        if line.startswith("MemTotal:"):
            memtotal_kb = int(line.split()[1])
            break
except OSError:
    pass
cpu_model = None
try:
    for line in pathlib.Path("/proc/cpuinfo").read_text(encoding="utf-8").splitlines():
        if line.startswith("model name"):
            cpu_model = line.split(":", 1)[1].strip()
            break
except OSError:
    pass
du = shutil.disk_usage("/")

snapshot = {
    "schema": "m6-env-snapshot/v1",
    "collected_at_utc": collected_at,
    "collector": "scripts/collect-env-evidence.sh",
    "project_label": "com.docker.compose.project=" + project,
    "docker": {
        "context_current": read_text("docker-context-show.txt"),
        "client_version": read_text("docker-client-version.txt"),
        "server_version": read_text("docker-server-version.txt"),
        "compose_version": read_text("compose-version.txt"),
        "info": {k: info.get(k) for k in (
            "ServerVersion", "OperatingSystem", "OSType", "KernelVersion",
            "Architecture", "NCPU", "MemTotal", "DockerRootDir",
            "Driver", "CgroupDriver", "CgroupVersion")},
        "note": ("docker.info NCPU/MemTotal are the daemon-side (Docker Desktop "
                 "utility VM) specs; docker.context identifies the daemon site"),
    },
    "host_vm": {
        "side": "WSL distro observation; daemon-side utility-VM specs are under docker.info",
        "uname": " ".join(os.uname()),
        "cpu_count": os.cpu_count(),
        "cpu_model": cpu_model,
        "memtotal_bytes": memtotal_kb * 1024 if memtotal_kb is not None else None,
        "root_disk_total_bytes": du.total,
        "root_disk_free_bytes": du.free,
        "raw_ref": "raw/host-spec.txt",
    },
    "daemon_containers": {
        "purpose": ("whole-daemon inventory before/after the window — external-load "
                    "disclosure (plan 4.8); deliberately NOT project-scoped"),
        "before": inventory(ps_before),
        "before_count": len(ps_before),
        "after": inventory(ps_after),
        "after_count": len(ps_after),
    },
    "docker_stats": {
        "before": read_jsonl("stats-all-before.jsonl"),
        "after": read_jsonl("stats-all-after.jsonl"),
    },
    "project_containers": project_containers,
    "project_container_count": len(project_containers),
    "poll": {
        "enabled": poll_seconds > 0,
        "seconds": poll_seconds,
        "interval_seconds": poll_interval,
        "phases_seen": sorted({c["first_seen_phase"] for c in project_containers}),
        "note": ("poll mode captures each container id first seen while polling "
                 "(short-lived k6); single-shot mode records before/after only"),
    },
    "raw_files": sorted(pp.name for pp in raw.iterdir()),
}

(out / "env-snapshot.json").write_text(
    json.dumps(snapshot, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

print("project_containers={pc} daemon_before={b} daemon_after={a} poll_enabled={pe}".format(
    pc=len(project_containers), b=len(ps_before), a=len(ps_after), pe=poll_seconds > 0))
PYEOF
)"
log "snapshot assembled: $summary"

# --- 8. redaction sweep over the finished tree --------------------------------
# Every write above was already filtered; the sweep is the second line of
# defence (it also covers files assembled by the python step and the log).
sweep_log="$work/redaction-sweep.txt"
sweep_status=0
bash "$redactor" "$secret_values_file" "$out" > "$sweep_log" 2>&1 || sweep_status=$?
sed 's/^/redact: /' "$sweep_log" | tee -a "$log_file"
if [ "$sweep_status" -ne 0 ]; then
  log "ERROR: redaction sweep failed (exit $sweep_status); see the lines above"
  echo "redaction sweep failed (exit $sweep_status); refusing to present this tree as clean evidence" >&2
  exit 4
fi

# --- 9. redaction self-check --------------------------------------------------
# Scan the tree with the same value set. The second scan runs after this file
# is written and is the authoritative one; its exit code also lands in
# collector-done.txt.
scan1="$work/selfcheck-1.log"
scan1_status=0
bash "$scanner" "$out" "$secret_values_file" > "$scan1" 2>&1 || scan1_status=$?
scan1_hits="$(grep -c '^hit: ' "$scan1" 2>/dev/null || true)"
{
  echo "redaction self-check: ${scanner} over ${out}"
  echo "value_set_files: ${secret_env_files[*]}"
  echo "value_set_size: $(wc -l < "$secret_values_file" | tr -d ' ')"
  echo "scan_1_exit: ${scan1_status} (0 = clean, 1 = hit, other = the scan did not run)"
  echo "scan_1_hits: ${scan1_hits:-0}"
  echo "note: the authoritative scan runs after this file is written; its exit code is recorded as"
  echo "      post_write_scan_exit in collector-done.txt. Neither file carries a credential value."
} > "$out/redaction-selfcheck.txt"
if [ "$scan1_status" -ne 0 ]; then
  {
    echo "scan_1_hit_lines (path, line, length and first/last-character mask only):"
    grep '^hit: ' "$scan1" || true
  } >> "$out/redaction-selfcheck.txt"
fi

scan2="$work/selfcheck-2.log"
scan2_status=0
bash "$scanner" "$out" "$secret_values_file" > "$scan2" 2>&1 || scan2_status=$?
post_write_scan_exit="$scan2_status"
if [ "$scan2_status" -ne 0 ]; then
  echo "redaction self-check FAILED (scan exit $scan2_status) for $out:" >&2
  grep '^hit: ' "$scan2" >&2 || true
  printf 'scan_2_exit: %s (FAIL: the tree still carried a value; the tree must not be used)\n' "$scan2_status" \
    >> "$out/redaction-selfcheck.txt"
  exit 4
fi
log "redaction self-check: tree scanned clean (scan exits ${scan1_status}/${scan2_status})"
log "collect-env-evidence done: $out/env-snapshot.json"
echo "env evidence written to $out (env-snapshot.json, container-quotas.txt, container-inspects/, raw/)"
echo "$summary"
