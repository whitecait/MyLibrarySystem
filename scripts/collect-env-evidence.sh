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
#
# Usage:
#   bash scripts/collect-env-evidence.sh --project <compose-project> \
#        [--out <dir>] [--poll-seconds <n>] [--poll-interval <n>]
#
#   --project        compose project name; containers are filtered by
#                    label com.docker.compose.project=<name>
#   --out            output directory (default
#                    artifacts/m6-acceptance/env-evidence/<project>-<UTCstamp>)
#   --poll-seconds   poll duration; 0 (default) = single before/after capture
#   --poll-interval  seconds between poll passes (default 10)
#
# Poll mode exists for short-lived containers: during an AT-0016 scenario the
# k6 container lives for minutes and is absent from a point-in-time list —
# which is exactly how the earlier evidence ended up with six quota entries
# for a seven-service stack. Run the collector with --poll-seconds spanning
# the load window; every container id first seen under the project label is
# inspected once and kept, so the k6 inspect lands beside the long-running
# six, with the capture phase recorded in raw/container-capture-order.txt.
#
# Exit codes: 0 ok; 2 docker CLI missing / daemon unreachable / python3
# missing; 3 output evidence already exists (refuses to overwrite — pick a
# fresh --out); 64 usage error.
#
# Read-only by construction: the collector only runs version/context/info/ps/
# stats/system df/inspect. It never starts, stops or reconfigures anything.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

project="" out="" poll_seconds=0 poll_interval=10
while [ $# -gt 0 ]; do
  case "$1" in
    --project) project="${2:?--project needs a value}"; shift 2 ;;
    --out) out="${2:?--out needs a value}"; shift 2 ;;
    --poll-seconds) poll_seconds="${2:?--poll-seconds needs a value}"; shift 2 ;;
    --poll-interval) poll_interval="${2:?--poll-interval needs a value}"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done
[ -n "$project" ] || {
  echo "usage: bash scripts/collect-env-evidence.sh --project <compose-project> [--out <dir>] [--poll-seconds <n>] [--poll-interval <n>]" >&2
  exit 64
}

command -v python3 >/dev/null 2>&1 || { echo "python3 not found in PATH" >&2; exit 2; }
command -v docker >/dev/null 2>&1 || { echo "docker CLI not found in PATH" >&2; exit 2; }
docker version >/dev/null 2>&1 || { echo "docker daemon unreachable (docker version failed)" >&2; exit 2; }

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
if [ -z "$out" ]; then
  out="artifacts/m6-acceptance/env-evidence/${project}-${stamp}"
fi
if [ -e "$out/env-snapshot.json" ]; then
  echo "refusing to overwrite existing evidence: $out/env-snapshot.json — pick a fresh --out" >&2
  exit 3
fi
mkdir -p "$out/raw" "$out/container-inspects"
log_file="$out/run-log.txt"
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$log_file"; }

log "collect-env-evidence start: project=$project out=$out poll_seconds=$poll_seconds poll_interval=$poll_interval"

raw="$out/raw"

# run_save <file> <command...>: run the command, store stdout+stderr in
# raw/<file>. Auxiliary evidence must not abort the run on failure, so a
# failing command keeps its error output on disk and only logs a warning.
run_save() {
  local f="$1"; shift
  if "$@" > "$raw/$f" 2>&1; then
    log "saved raw/$f"
  else
    log "WARN: command failed, error output kept in raw/$f: $*"
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
} > "$raw/host-spec.txt"
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
    docker inspect "$id" > "$inspect_dir/$id.json"
    printf '%s\t%s\n' "$phase" "$id" >> "$order_file"
    log "captured container ${id:0:12} (phase=$phase)"
  done < <(docker ps -aq --filter "label=com.docker.compose.project=$project")
}
capture_new_containers before

# --- 5. poll mode: catch short-lived containers (k6) -------------------------
if [ "$poll_seconds" -gt 0 ]; then
  log "poll mode: capturing for ${poll_seconds}s, interval ${poll_interval}s"
  deadline=$(( $(date +%s) + poll_seconds ))
  while :; do
    capture_new_containers poll
    [ "$(date +%s)" -ge "$deadline" ] && break
    sleep "$poll_interval"
  done
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
log "collect-env-evidence done: $out/env-snapshot.json"
echo "env evidence written to $out (env-snapshot.json, container-quotas.txt, container-inspects/, raw/)"
echo "$summary"
