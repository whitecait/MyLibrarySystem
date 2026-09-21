#!/usr/bin/env bash
# Payment-batch egress audit — continuous in-window egress recording
# (acceptance plan 2026-09-19 §2.9 / §4.11; R08 rewrite 2026-09-21).
#
# Replaces the earlier one-shot check that read the Internal flag of the
# FIRST project network at start and close only. The audit now samples the
# whole window:
#
#   start   record a baseline sampling round and launch a background sampler
#           that repeats the round every <interval> seconds (default 10;
#           4th argument or EGRESS_AUDIT_INTERVAL overrides). The sampler PID
#           is written to <out-dir>/sampler.pid and its log to
#           <out-dir>/sampler.log; both paths are recorded in
#           <out-dir>/start.json together with the network isolation
#           snapshot. Refuses to start when the project has neither running
#           containers nor networks (project-name typo guard).
#   sample  run one sampling round now: enumerate every running container of
#           the compose project (label filter
#           label=com.docker.compose.project=<project>), per container
#           enumerate its TCP connections (`docker exec <c> ss -tnp`; when ss
#           is unavailable, parse /proc/net/tcp + /proc/net/tcp6 and resolve
#           owning processes via the /proc/*/fd socket inodes), and record
#           every connection whose REMOTE address lies OUTSIDE loopback and
#           the private ranges 10/8, 172.16/12, 192.168/16. Independently
#           inspect ALL networks attached to the project's containers (not
#           just the first) and record their isolation state. Appends JSONL
#           lines (ts/container/remote/remotePort/state/process/method) to
#           <out-dir>/samples.jsonl; each round also appends one "sample"
#           line carrying the container list and per-network Internal flags.
#   close   stop the sampler, take a final round, and write
#           <out-dir>/report.json summarizing the window: start/end,
#           sampling rounds, external connection count (acceptance
#           criterion: 0) and the anomaly list. Exit 0 only on a passing
#           window (count 0, at least one round, no anomalies); exit 1
#           otherwise.
#
# REGISTERED BOUNDARY (honest scope statement)
#   The enumeration above sees connections originated INSIDE the project's
#   containers only. Calls issued by the HOST browser (Playwright driving
#   the UI from the host during payment E2E) are NOT visible to
#   `docker exec ss` or the containers' /proc/net/tcp. For W2 the host-side
#   gap is covered by supplementary means, not by this script: Env-W network
#   isolation (compose.payment-egress.yaml makes the project networks
#   internal, so no external payment route exists at all) plus page-side
#   network-idle evidence captured during the browser run.
#
#   bash scripts/payment-egress-audit.sh start  <project> <out-dir> [interval-s]
#   bash scripts/payment-egress-audit.sh sample <project> <out-dir>
#   bash scripts/payment-egress-audit.sh close  <project> <out-dir>
#
# EGRESS_AUDIT_DOCKER overrides the docker CLI (default: `docker`, falling
# back to `docker.exe` for the Windows-side Docker Desktop CLI).
set -Eeuo pipefail
umask 077
cd "$(dirname "$0")/.."

usage="usage: payment-egress-audit.sh start|sample|close <project> <out-dir> [interval-s]"
mode="${1:?${usage}}"
case "$mode" in
  start|sample|close|sampleloop) ;;
  *) echo "unknown mode: $mode (expected start|sample|close)" >&2; exit 1 ;;
esac
project="${2:?${usage}}"
out_dir="${3:?${usage}}"
if ! [[ "$project" =~ ^[A-Za-z0-9][A-Za-z0-9_-]*$ ]]; then
  echo "invalid project name: $project" >&2
  exit 1
fi
interval="${4:-${EGRESS_AUDIT_INTERVAL:-10}}"
if ! [[ "$interval" =~ ^[0-9]+$ ]] || (( interval == 0 )); then
  echo "invalid interval: $interval (positive integer seconds)" >&2
  exit 1
fi
mkdir -p "$out_dir"

docker_bin="${EGRESS_AUDIT_DOCKER:-}"
if [[ -z "$docker_bin" ]]; then
  if command -v docker >/dev/null 2>&1; then docker_bin="docker"
  elif command -v docker.exe >/dev/null 2>&1; then docker_bin="docker.exe"
  else echo "no docker CLI on PATH (tried docker, docker.exe)" >&2; exit 1
  fi
fi

samples_file="$out_dir/samples.jsonl"
seq_file="$out_dir/sample.seq"
pid_file="$out_dir/sampler.pid"

# dock runs the resolved docker CLI; tr strips the CRLF that the Windows
# docker.exe emits when its stdout is a pipe.
dock() { "$docker_bin" "$@" | tr -d '\r'; }

jesc() { # minimal JSON string escape for the flat values recorded here
  local s="${1//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}

utcnow() { date -u +%Y-%m-%dT%H:%M:%SZ; }

append_jsonl() { printf '%s\n' "$1" >> "$samples_file"; }

next_seq() {
  local n=0
  if [[ -f "$seq_file" ]]; then
    n="$(cat "$seq_file" 2>/dev/null || printf 0)"
  fi
  case "$n" in ''|*[!0-9]*) n=0 ;; esac
  n=$((n + 1))
  printf '%s\n' "$n" > "$seq_file"
  printf '%s' "$n"
}

# remote_excluded <addr> — exit 0 when the remote must NOT be counted as
# external: loopback (127/8, ::, ::1) and the private ranges 10/8,
# 172.16/12, 192.168/16 (the R08 exclusion definition). Everything else —
# including CGNAT-style addresses such as 100.64.0.1 — counts as external.
remote_excluded() {
  local addr="$1"
  case "$addr" in
    ''|127.*|::1|::) return 0 ;;
  esac
  if [[ "$addr" == *.* && "$addr" != *:* ]]; then
    local a b
    IFS=. read -r a b _ <<< "$addr"
    if [[ "${a:-}" =~ ^[0-9]+$ && "${b:-}" =~ ^[0-9]+$ ]]; then
      if (( a == 10 )); then return 0; fi
      if (( a == 172 && b >= 16 && b <= 31 )); then return 0; fi
      if (( a == 192 && b == 168 )); then return 0; fi
    fi
    return 1
  fi
  return 1 # remaining IPv6 addresses count as external
}

hex_to_ipv4() { # /proc/net/tcp stores IPv4 little-endian: 0100007F -> 127.0.0.1
  local h="$1"
  printf '%d.%d.%d.%d' "0x${h:6:2}" "0x${h:4:2}" "0x${h:2:2}" "0x${h:0:2}"
}

hex_to_ipv6() { # 32 hex chars, four little-endian words -> full-form address
  local h="$1" out="" i w
  for (( i = 0; i < 32; i += 8 )); do
    w="${h:i:8}"
    out+="${w:6:2}${w:4:2}${w:2:2}${w:0:2}"
    if (( i < 24 )); then out+=":"; fi
  done
  printf '%s' "$out"
}

tcp_state_name() {
  case "$1" in
    01) printf ESTAB ;;
    02) printf SYN_SENT ;;
    03) printf SYN_RECV ;;
    04) printf FIN_WAIT1 ;;
    05) printf FIN_WAIT2 ;;
    06) printf TIME_WAIT ;;
    07) printf CLOSE ;;
    08) printf CLOSE_WAIT ;;
    09) printf LAST_ACK ;;
    0A) printf LISTEN ;;
    0B) printf CLOSING ;;
    *)  printf "ST_%s" "$1" ;;
  esac
}

record_external() { # <seq> <ts> <container> <remote> <port> <state> <process> <method>
  append_jsonl "{\"kind\":\"connection\",\"seq\":$1,\"ts\":\"$2\",\"project\":\"$(jesc "$project")\",\"container\":\"$(jesc "$3")\",\"remote\":\"$(jesc "$4")\",\"remotePort\":$5,\"state\":\"$(jesc "$6")\",\"process\":\"$(jesc "$7")\",\"method\":\"$8\"}"
}

parse_ss_output() { # <container> <seq> <ts>; stdin: `ss -tnp` output
  local cname="$1" seq="$2" ts="$3"
  local line st rq sq laddr peer rest addr port process
  while IFS= read -r line; do
    if [[ -z "$line" ]]; then continue; fi
    st=""; addr=""; port=""; process=""
    read -r st rq sq laddr peer rest <<< "$line" || true
    case "$peer" in *:*) ;; *) continue ;; esac
    addr="${peer%:*}"
    port="${peer##*:}"
    if ! [[ "$port" =~ ^[0-9]+$ ]]; then continue; fi # header / *:* listen rows
    addr="${addr#[}"; addr="${addr%%]*}"; addr="${addr%%\%*}"
    if [[ "$rest" == *'users:(('* ]]; then
      process="$(printf '%s' "$rest" | sed -n 's/.*("\([^"]*\)".*/\1/p')"
    fi
    if ! remote_excluded "$addr"; then
      record_external "$seq" "$ts" "$cname" "$addr" "$port" "$st" "$process" "ss"
    fi
  done
}

container_proc_map() { # <cid>: "inode pid cmdline" per socket fd (best effort)
  dock exec "$1" sh -c '
    for f in /proc/[0-9]*/fd/*; do
      t=$(readlink "$f" 2>/dev/null) || continue
      case $t in
        socket:*) ;;
        *) continue ;;
      esac
      p=${f#/proc/}; p=${p%%/*}
      cmd=$(tr "\0" " " <"/proc/$p/cmdline" 2>/dev/null | cut -c1-80)
      ino=$(printf "%s" "$t" | tr -cd "0-9")
      echo "$ino $p $cmd"
    done' 2>/dev/null | tr -d '\r' || true
}

parse_proc_output() { # <container> <seq> <ts> <proc-map>; stdin: /proc/net/tcp[6]
  local cname="$1" seq="$2" ts="$3" map="$4"
  local -A inode_pid=() inode_cmd=()
  local line ino pid cmd
  while read -r ino pid cmd; do
    if [[ -n "${ino:-}" ]]; then
      inode_pid["$ino"]="$pid"
      inode_cmd["$ino"]="${cmd:-}"
    fi
  done <<< "$map"
  local hexaddr hexport sthex addr port stname process
  while IFS= read -r line; do
    if ! [[ "$line" =~ ^[0-9]+: ]]; then continue; fi
    # fields: sl local rem st tx:rx tr:tm retrnsmt uid timeout inode
    set -- $line
    hexaddr="$2"; hexport="$3"; sthex="$4"; ino="${10}"
    if [[ "$sthex" == "0A" ]]; then continue; fi # LISTEN
    if (( ${#hexaddr} == 8 )); then
      addr="$(hex_to_ipv4 "$hexaddr")"
    else
      addr="$(hex_to_ipv6 "$hexaddr")"
    fi
    port=$(( 16#$hexport ))
    stname="$(tcp_state_name "$sthex")"
    process=""
    pid="${inode_pid[$ino]:-}"
    if [[ -n "$pid" ]]; then
      process="(pid=${pid}) ${inode_cmd[$ino]:-}"
      process="${process:0:96}"
    fi
    if ! remote_excluded "$addr"; then
      record_external "$seq" "$ts" "$cname" "$addr" "$port" "$stname" "$process" "proc-net-tcp"
    fi
  done
}

container_connections() { # <cid> <cname> <seq> <ts>; appends records, prints method
  local cid="$1" cname="$2" seq="$3" ts="$4"
  local ss_raw="" ss_rc=0
  ss_raw="$(dock exec "$cid" ss -tnp 2>/dev/null)" || ss_rc=$?
  if (( ss_rc == 0 )); then
    parse_ss_output "$cname" "$seq" "$ts" <<< "$ss_raw"
    printf 'ss'
    return 0
  fi
  local proc_raw=""
  proc_raw="$(dock exec "$cid" sh -c 'cat /proc/net/tcp /proc/net/tcp6' 2>/dev/null)" || true
  if [[ -n "$proc_raw" ]]; then
    parse_proc_output "$cname" "$seq" "$ts" "$(container_proc_map "$cid")" <<< "$proc_raw"
    printf 'proc-net-tcp'
    return 0
  fi
  return 1
}

collect_network_ids() { # stdout: unique network IDs of the project (attached
                        # networks of all containers; name filter as fallback)
  local cid
  local cids=""
  cids="$(dock ps --filter "label=com.docker.compose.project=${project}" --format '{{.ID}}')" || return 1
  if [[ -z "$cids" ]]; then
    dock network ls --filter "name=${project}" --format '{{.ID}}' || true
    return 0
  fi
  for cid in $cids; do
    dock inspect "$cid" --format '{{range $k, $v := .NetworkSettings.Networks}}{{$v.NetworkID}}{{"\n"}}{{end}}' || true
  done | sort -u
}

# collect_networks_json — sets NETS_JSON (JSON array) and NETS_BAD (space-
# separated names of networks whose Internal flag is not true).
NETS_JSON="[]"
NETS_BAD=""
collect_networks_json() {
  NETS_JSON="[]"
  NETS_BAD=""
  local ids nid net_line name internal subnets s sub_json joined
  local parts=()
  ids="$(collect_network_ids)" || return 1
  for nid in $ids; do
    net_line="$(dock network inspect "$nid" --format '{{.Name}}|{{.Internal}}|{{range .IPAM.Config}}{{.Subnet}} {{end}}' || true)"
    if [[ -z "$net_line" ]]; then continue; fi
    name="${net_line%%|*}"; net_line="${net_line#*|}"
    internal="${net_line%%|*}"; subnets="${net_line#*|}"
    if [[ "$internal" != "true" ]]; then NETS_BAD+="${name} "; fi
    sub_json=""
    for s in $subnets; do
      if [[ -n "$sub_json" ]]; then sub_json+=","; fi
      sub_json+="\"$(jesc "$s")\""
    done
    parts+=("{\"name\":\"$(jesc "$name")\",\"internal\":${internal:-false},\"subnets\":[${sub_json:-}]}")
  done
  if (( ${#parts[@]} > 0 )); then
    joined=""
    local p
    for p in "${parts[@]}"; do
      if [[ -n "$joined" ]]; then joined+=","; fi
      joined+="$p"
    done
    NETS_JSON="[${joined}]"
  fi
  return 0
}

run_sample_round() { # one round; always appends the sample summary line
  local seq ts cids cid cname method containers_json="" methods_csv=""
  seq="$(next_seq)"
  ts="$(utcnow)"
  if ! cids="$(dock ps --filter "label=com.docker.compose.project=${project}" --format '{{.ID}} {{.Names}}')"; then
    append_jsonl "{\"kind\":\"anomaly\",\"seq\":${seq},\"ts\":\"${ts}\",\"type\":\"docker-query-failed\",\"detail\":\"docker ps failed\"}"
    return 0
  fi
  if [[ -z "$cids" ]]; then
    append_jsonl "{\"kind\":\"anomaly\",\"seq\":${seq},\"ts\":\"${ts}\",\"type\":\"no-containers\",\"detail\":\"no running container carries label com.docker.compose.project=${project}\"}"
  fi
  local nets_json="[]"
  if collect_networks_json; then
    nets_json="$NETS_JSON"
    local bad
    for bad in $NETS_BAD; do
      append_jsonl "{\"kind\":\"anomaly\",\"seq\":${seq},\"ts\":\"${ts}\",\"type\":\"network-not-internal\",\"detail\":\"network ${bad} has Internal != true\"}"
    done
  else
    append_jsonl "{\"kind\":\"anomaly\",\"seq\":${seq},\"ts\":\"${ts}\",\"type\":\"docker-query-failed\",\"detail\":\"network enumeration failed\"}"
  fi
  while read -r cid cname; do
    if [[ -z "$cid" ]]; then continue; fi
    if [[ -n "$containers_json" ]]; then containers_json+=","; fi
    containers_json+="\"$(jesc "$cname")\""
    if method="$(container_connections "$cid" "$cname" "$seq" "$ts")"; then
      if [[ -n "$methods_csv" ]]; then methods_csv+=","; fi
      methods_csv+="${cname}:${method}"
    else
      append_jsonl "{\"kind\":\"anomaly\",\"seq\":${seq},\"ts\":\"${ts}\",\"type\":\"container-enum-failed\",\"detail\":\"$(jesc "$cname"): ss and /proc/net/tcp unavailable\"}"
    fi
  done <<< "$cids"
  append_jsonl "{\"kind\":\"sample\",\"seq\":${seq},\"ts\":\"${ts}\",\"project\":\"$(jesc "$project")\",\"containers\":[${containers_json}],\"networks\":${nets_json},\"methods\":\"${methods_csv}\"}"
  return 0
}

cmd_start() {
  local old
  if [[ -f "$pid_file" ]]; then
    old="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$old" ]] && kill -0 "$old" 2>/dev/null; then
      echo "sampler already running (pid ${old}, pid file ${pid_file}); close it first" >&2
      exit 1
    fi
  fi
  local n_containers any_net
  n_containers="$(dock ps --filter "label=com.docker.compose.project=${project}" --format '{{.ID}}' | wc -l | tr -d '[:space:]')" || n_containers=""
  any_net="$(dock network ls --filter "name=${project}" --format '{{.ID}}' | head -n 1 || true)"
  if [[ "${n_containers:-0}" == "0" && -z "$any_net" ]]; then
    echo "no containers and no networks found for project ${project}; refusing to audit" >&2
    exit 1
  fi
  run_sample_round # baseline round (seq 1)
  local nets_json="[]"
  if collect_networks_json; then nets_json="$NETS_JSON"; fi
  local sampler_log="$out_dir/sampler.log"
  touch "$sampler_log"
  nohup bash "$0" sampleloop "$project" "$out_dir" "$interval" >> "$sampler_log" 2>&1 &
  local spid=$!
  printf '%s\n' "$spid" > "$pid_file"
  cat > "$out_dir/start.json" <<JSON
{
  "kind": "payment-egress-audit-start",
  "project": "$(jesc "$project")",
  "utcTime": "$(utcnow)",
  "intervalSeconds": ${interval},
  "samplerPid": ${spid},
  "samplerLog": "$(jesc "$sampler_log")",
  "samplesFile": "$(jesc "$samples_file")",
  "networks": ${nets_json},
  "coverageBoundary": "container-side TCP only: docker exec ss -tnp (fallback /proc/net/tcp[6]); host browser-side calls are NOT visible to this mechanism",
  "w2Supplement": "host-browser side is covered by Env-W network isolation (compose.payment-egress.yaml, internal networks) plus page-side network-idle evidence"
}
JSON
  echo "audit started: project=${project} interval=${interval}s sampler_pid=${spid}"
  echo "  pid file:   ${pid_file}"
  echo "  sampler log:${sampler_log}"
  echo "  samples:    ${samples_file}"
}

cmd_sample() {
  run_sample_round
  echo "sampled one round into ${samples_file}"
}

cmd_sampleloop() { # internal: background body launched by start
  trap 'exit 0' TERM INT
  while true; do
    run_sample_round || true
    sleep "$interval" &
    wait "$!" || true
  done
}

cmd_close() {
  local sampler_stop_state="no-pid-file" spid=""
  if [[ -f "$pid_file" ]]; then
    spid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$spid" ]] && kill -0 "$spid" 2>/dev/null; then
      kill "$spid" 2>/dev/null || true
      local i
      for i in 1 2 3 4 5 6 7 8 9 10; do
        if ! kill -0 "$spid" 2>/dev/null; then break; fi
        sleep 0.3
      done
      if kill -0 "$spid" 2>/dev/null; then
        kill -9 "$spid" 2>/dev/null || true
        sampler_stop_state="killed-hard"
      else
        sampler_stop_state="stopped"
      fi
    else
      sampler_stop_state="already-dead"
    fi
    rm -f "$pid_file"
  fi
  if [[ "$sampler_stop_state" != "stopped" && "$sampler_stop_state" != "killed-hard" ]]; then
    append_jsonl "{\"kind\":\"anomaly\",\"seq\":$(next_seq),\"ts\":\"$(utcnow)\",\"type\":\"sampler-not-running-at-close\",\"detail\":\"sampler state at close: ${sampler_stop_state}\"}"
  fi
  run_sample_round # final round records close-time isolation and connections
  local rounds=0 ext=0 w_start="" w_end="" anomalies_csv=""
  if [[ -f "$samples_file" ]]; then
    rounds="$(grep -cF '"kind":"sample"' "$samples_file" || true)"
    ext="$(grep -cF '"kind":"connection"' "$samples_file" || true)"
    w_start="$(grep -F '"kind":"sample"' "$samples_file" | head -n 1 | sed -n 's/.*"ts":"\([^"]*\)".*/\1/p' || true)"
    w_end="$(tail -n 1 "$samples_file" | sed -n 's/.*"ts":"\([^"]*\)".*/\1/p' || true)"
    anomalies_csv="$(grep -F '"kind":"anomaly"' "$samples_file" | paste -sd ',' - || true)"
  fi
  if (( rounds == 0 )); then
    append_jsonl "{\"kind\":\"anomaly\",\"seq\":$(next_seq),\"ts\":\"$(utcnow)\",\"type\":\"no-samples\",\"detail\":\"no completed sampling rounds in ${samples_file}\"}"
    anomalies_csv="$(grep -F '"kind":"anomaly"' "$samples_file" | paste -sd ',' - || true)"
  fi
  local verdict="pass"
  if (( ext != 0 )); then verdict="fail"; fi
  if (( rounds == 0 )); then verdict="fail"; fi
  if [[ -n "$anomalies_csv" ]]; then verdict="fail"; fi
  local anomalies_json="[]"
  if [[ -n "$anomalies_csv" ]]; then anomalies_json="[${anomalies_csv}]"; fi
  # report the interval actually configured at start (close may be invoked
  # without the 4th argument, so the default would otherwise be reported)
  local reported_interval="$interval"
  if [[ -f "$out_dir/start.json" ]]; then
    local iv
    iv="$(sed -n 's/.*"intervalSeconds": \([0-9][0-9]*\).*/\1/p' "$out_dir/start.json" | head -n 1 || true)"
    if [[ -n "$iv" ]]; then reported_interval="$iv"; fi
  fi
  cat > "$out_dir/report.json" <<JSON
{
  "kind": "payment-egress-audit-report",
  "project": "$(jesc "$project")",
  "generatedAt": "$(utcnow)",
  "window": { "start": "${w_start}", "end": "${w_end}" },
  "intervalSeconds": ${reported_interval},
  "samplingRounds": ${rounds},
  "externalConnections": ${ext},
  "acceptanceCriterion": "externalConnections must be 0 for the whole payment window",
  "anomalies": ${anomalies_json},
  "verdict": "${verdict}",
  "samplerStop": "${sampler_stop_state}",
  "coverageBoundary": "container-side TCP only: docker exec ss -tnp (fallback /proc/net/tcp[6]); host browser-side calls are NOT visible to this mechanism",
  "w2Supplement": "host-browser side is covered by Env-W network isolation (compose.payment-egress.yaml, internal networks) plus page-side network-idle evidence",
  "evidenceFiles": { "samples": "samples.jsonl", "start": "start.json", "samplerLog": "sampler.log" }
}
JSON
  echo "report: ${out_dir}/report.json"
  echo "  window: ${w_start} .. ${w_end}  rounds=${rounds} interval=${reported_interval}s"
  echo "  externalConnections=${ext} verdict=${verdict} samplerStop=${sampler_stop_state}"
  if [[ -n "$anomalies_csv" ]]; then
    echo "  anomalies:"
    printf '    %s\n' "$anomalies_csv" | tr ',' '\n'
  fi
  if [[ "$verdict" == "pass" ]]; then
    exit 0
  fi
  exit 1
}

case "$mode" in
  start) cmd_start ;;
  sample) cmd_sample ;;
  close) cmd_close ;;
  sampleloop) cmd_sampleloop ;;
esac
