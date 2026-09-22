#!/usr/bin/env bash
# Post-hoc judgement gates for ONE performance-protocol attempt directory
# (the attempt-<UTC>-p<pid>-run<N>-<rand>/ that e2e/perf/run-protocol.sh
# writes). Every gate below is the command the protocol or its round driver
# already uses; this wrapper only fixes their order, their inputs and the
# aggregate exit code, and writes one judgement record per attempt.
#
#   bash e2e/perf/judge-attempt.sh <attempt-dir> [--reconcile-dir DIR] \
#     [--reconcile-labels steady,peak] [--out DIR]
#
# Gates (all four must pass for exit 0):
#   1. attempt.json validity. The file must parse and carry valid===true;
#      run-protocol.sh writes valid=false for an attempt that did not run to
#      completion inside one directory (its attempt_write), and such an
#      attempt's files are partial evidence of one attempt.
#   2. collect-verdicts.mjs. Its input contract is <evidence-dir>/run-N/
#      (collect-verdicts.mjs usage line), while run-protocol.sh writes
#      attempt-<...>-run<N>-<rand>/. This script therefore stages a run-N view
#      of the attempt dir under <out>/verdicts/ by hard links (ln -f: same
#      inodes, no second copy of the material) and runs the collector against
#      that view. A missing protocol summary or saturation verdict is a failed
#      gate, not a smaller collection.
#   3. check-saturation.mjs <attempt-dir> --out <out>/check-saturation-verdict.json
#      — the invocation run-protocol.sh itself makes (see its "saturation
#      check" stage). The checker judges the round: a saturated window or an
#      unevaluated dimension (collection gap) exits nonzero.
#   4. reconcile.mjs, once per window label, with the argument shapes of the
#      AT-0016 round driver: --summary/--window-start/--window-end/--prom-json/
#      --request-log/--out. Its window bounds and side captures are produced by
#      the round driver under <reconcile-dir> (default <attempt-dir>/reconcile):
#      windows.json {"<label>":{"start_utc":...,"end_utc":...}},
#      prom-<label>.json (saved Prometheus response), frontend-access.log.
#      reconcile.mjs exits 0 with an "对账不完整" note when a side is absent, so
#      this script requires both side inputs up front and rejects a report that
#      still says 对账不完整 after the run.
#
# attempt.json's window_env_collector status is copied into the record but is
# NOT a gate here; its completeness is judged at round/acceptance level.
#
# Exit codes:
#   0 = every gate passed;
#   1 = every gate ran and at least one failed;
#   2 = usage or input error (no attempt dir, no attempt.json, no window bounds
#       or missing reconciliation side captures), i.e. a gate could not run.
# Any nonzero means "not all post-hoc gates passed for this attempt".
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

usage() {
  cat >&2 <<'USAGE'
usage: bash e2e/perf/judge-attempt.sh <attempt-dir> [options]

  --reconcile-dir DIR     dir holding windows.json, prom-<label>.json and
                          frontend-access.log (default <attempt-dir>/reconcile)
  --reconcile-labels LIST comma-separated window labels to reconcile
                          (default steady,peak)
  --out DIR               dir for this judgement's artifacts
                          (default <attempt-dir>/judge)
USAGE
}

attempt_dir=""
reconcile_dir=""
reconcile_labels="steady,peak"
out_dir=""
while (( $# > 0 )); do
  case "$1" in
    --reconcile-dir) reconcile_dir="${2:?--reconcile-dir needs a value}"; shift 2 ;;
    --reconcile-labels) reconcile_labels="${2:?--reconcile-labels needs a value}"; shift 2 ;;
    --out) out_dir="${2:?--out needs a value}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "judge-attempt: unknown option $1" >&2; usage; exit 2 ;;
    *)
      if [[ -n "$attempt_dir" ]]; then
        echo "judge-attempt: more than one attempt directory given ('$attempt_dir', '$1')" >&2
        exit 2
      fi
      attempt_dir="$1"; shift ;;
  esac
done

if [[ -z "$attempt_dir" ]]; then usage; exit 2; fi
if [[ ! -d "$attempt_dir" ]]; then
  echo "judge-attempt: attempt dir does not exist: ${attempt_dir}" >&2
  exit 2
fi
attempt_dir="$(cd "$attempt_dir" && pwd)"
out_dir="${out_dir:-${attempt_dir}/judge}"
reconcile_dir="${reconcile_dir:-${attempt_dir}/reconcile}"
G="${out_dir}/gates"
mkdir -p "$G"

if ! command -v node >/dev/null 2>&1; then
  echo "judge-attempt: node is required for the verdict gates" >&2
  exit 2
fi

record="${out_dir}/judgement-record.txt"
{
  echo "# performance attempt judgement"
  echo "# attempt dir: ${attempt_dir}"
  echo "# judged at (UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# judge script: e2e/perf/judge-attempt.sh sha256 $(sha256sum e2e/perf/judge-attempt.sh | cut -d' ' -f1)"
} > "$record"

# --- gate 1: attempt.json validity -------------------------------------------
# The node step extracts the attempt-identity fields into attempt-validity.json,
# prints the run label (used to name the staged run-N view) and exits 0 only for
# valid===true. An unparseable file exits 3 and is a gate failure.
attempt_gate=2
run_label=""
if [[ ! -f "${attempt_dir}/attempt.json" ]]; then
  echo "attempt.json missing in ${attempt_dir}" > "${out_dir}/attempt-validity.txt"
  echo "attempt-json: attempt.json missing" >> "$record"
else
  if run_label="$(node -e '
    const fs = require("fs")
    const [p, outDir] = process.argv.slice(1)
    let a
    try {
      a = JSON.parse(fs.readFileSync(p, "utf8"))
    } catch (e) {
      fs.writeFileSync(`${outDir}/attempt-validity.txt`, `attempt.json unparseable: ${e.message}\n`)
      process.exit(3)
    }
    const fields = {
      attempt_id: a.attempt_id ?? null,
      run_label: a.run_label ?? null,
      candidate_commit: a.candidate_commit ?? null,
      working_tree_dirty: a.working_tree_dirty ?? null,
      started_at: a.started_at ?? null,
      ended_at: a.ended_at ?? null,
      valid: a.valid ?? null,
      basis: a.basis ?? null,
      window_env_collector: a.window_env_collector ?? null,
    }
    fs.writeFileSync(`${outDir}/attempt-validity.json`, JSON.stringify(fields, null, 2) + "\n")
    fs.writeFileSync(`${outDir}/attempt-validity.txt`,
      `valid=${fields.valid}\nrun_label=${fields.run_label}\nwindow_env_collector=${JSON.stringify(fields.window_env_collector)}\n`)
    process.stdout.write(String(a.run_label ?? ""))
    process.exit(a.valid === true ? 0 : 1)
  ' "${attempt_dir}/attempt.json" "$out_dir" 2> "${out_dir}/attempt-validity.err")"; then
    attempt_gate=0
    echo "attempt-json: valid=true (run_label=${run_label})" >> "$record"
  else
    attempt_gate=$?
    echo "attempt-json: not valid=true (gate exit ${attempt_gate}); see ${out_dir}/attempt-validity.txt" >> "$record"
  fi
fi
echo "$attempt_gate" > "$G/attempt-json.exitcode"

# --- gate 2: collect-verdicts.mjs over a staged run-N view --------------------
# Same label allow-list as run-protocol.sh's protocol_summary_labels.
protocol_summary_labels=(
  warmup steady
  retrieval-cold-hot retrieval-hot-hot retrieval-cold-tail retrieval-hot-tail
  peak recovery
)
if [[ "$run_label" =~ ^[0-9]+$ ]]; then run_view="${out_dir}/verdicts/run-${run_label}"; else run_view="${out_dir}/verdicts/run-unknown"; fi
mkdir -p "$run_view"
staging_missing=()
linked=0
for label in "${protocol_summary_labels[@]}"; do
  src="${attempt_dir}/${label}-summary.json"
  if [[ ! -f "$src" ]]; then
    staging_missing+=("${label}-summary.json")
    continue
  fi
  if ln -f "$src" "${run_view}/${label}-summary.json" 2>/dev/null; then
    linked=$(( linked + 1 ))
  else
    staging_missing+=("${label}-summary.json (hard link failed: ${run_view} is not on the same filesystem as ${attempt_dir})")
  fi
done
if [[ -f "${attempt_dir}/saturation-verdict.json" ]]; then
  if ! ln -f "${attempt_dir}/saturation-verdict.json" "${run_view}/saturation-verdict.json" 2>/dev/null; then
    staging_missing+=("saturation-verdict.json (hard link failed)")
  fi
else
  staging_missing+=("saturation-verdict.json")
fi
cat > "${out_dir}/verdicts/README.txt" <<'TXT'
# verdicts/ — 本轮 attempt 的判定暂存视图，不是第二次尝试

e2e/perf/collect-verdicts.mjs 的输入契约是 <evidence-dir>/run-N/，而
e2e/perf/run-protocol.sh 落盘的目录名是 attempt-<UTC>-p<pid>-run<N>-<rand>
（每次调用一个隔离目录，防止跨尝试污染）。run-<N>/ 里的文件用硬链接
（ln -f，同一 inode、不复制字节）指向本轮 attempt 目录内的 8 个
*-summary.json 与 saturation-verdict.json，只为满足上述契约。

run-<N>/ 的内容与本轮 attempt 目录是同一批文件；它们不是两轮材料，不得
当作两次独立尝试计数。
TXT

collect_gate=2
if (( ${#staging_missing[@]} > 0 )); then
  {
    echo "staging incomplete — the attempt dir is missing protocol outputs, so collect-verdicts.mjs cannot judge this attempt:"
    printf '  - %s\n' "${staging_missing[@]}"
  } > "$G/collect-verdicts.stdout"
  collect_gate=1
  echo "collect-verdicts: staging incomplete (${#staging_missing[@]} item(s) missing); see $G/collect-verdicts.stdout" >> "$record"
else
  if node e2e/perf/collect-verdicts.mjs "$out_dir/verdicts" > "$G/collect-verdicts.stdout" 2>&1; then
    collect_gate=0
  else
    collect_gate=$?
  fi
  echo "collect-verdicts: staged ${linked}/8 summaries + saturation verdict, gate exit ${collect_gate}" >> "$record"
fi
echo "$collect_gate" > "$G/collect-verdicts.exitcode"

# --- gate 3: check-saturation.mjs --------------------------------------------
sat_gate=2
if node e2e/perf/check-saturation.mjs "$attempt_dir" --out "${out_dir}/check-saturation-verdict.json" \
     > "$G/check-saturation.stdout" 2>&1; then
  sat_gate=0
else
  sat_gate=$?
fi
echo "$sat_gate" > "$G/check-saturation.exitcode"
echo "check-saturation: gate exit ${sat_gate}" >> "$record"

# --- gate 4: reconcile.mjs per window label ----------------------------------
windows_file="${reconcile_dir}/windows.json"
reconcile_gate=2
: > "$G/reconcile.stdout"
if [[ ! -f "$windows_file" ]]; then
  echo "window bounds missing: ${windows_file} — the round driver must capture k6 window start/end before the reconciliation can be judged" | tee -a "$G/reconcile.stdout"
else
  reconcile_gate=0
  for label in ${reconcile_labels//,/ }; do
    start=""; end=""
    if ! bounds="$(node -e '
      const fs = require("fs")
      const [p, label] = process.argv.slice(1)
      const w = JSON.parse(fs.readFileSync(p, "utf8"))
      const e = w[label]
      if (!e || !e.start_utc || !e.end_utc) process.exit(4)
      process.stdout.write(`${e.start_utc}\t${e.end_utc}`)
    ' "$windows_file" "$label" 2>/dev/null)"; then
      echo "reconcile-${label}: missing/invalid window bounds in ${windows_file} (gate exit 2)" >> "$G/reconcile.stdout"
      reconcile_gate=2
      continue
    fi
    IFS=$'\t' read -r start end <<<"$bounds"
    prom_json="${reconcile_dir}/prom-${label}.json"
    request_log="${reconcile_dir}/frontend-access.log"
    side_missing=()
    [[ -f "$prom_json" ]] || side_missing+=("${prom_json}")
    [[ -f "$request_log" ]] || side_missing+=("${request_log}")
    if (( ${#side_missing[@]} > 0 )); then
      {
        echo "reconcile-${label}: reconciliation side input(s) missing — a k6-only report is not a three-way reconciliation (gate exit 2):"
        printf '  - %s\n' "${side_missing[@]}"
      } >> "$G/reconcile.stdout"
      echo "2" > "$G/reconcile-${label}.exitcode"
      reconcile_gate=2
      continue
    fi
    report="${out_dir}/reconcile-${label}.md"
    rc=0
    node e2e/perf/reconcile.mjs \
      --summary "${attempt_dir}/${label}-summary.json" \
      --window-start "$start" --window-end "$end" \
      --prom-json "$prom_json" \
      --request-log "$request_log" \
      --out "$report" \
      > "$G/reconcile-${label}.stdout" 2>&1 || rc=$?
    if [[ "$rc" -eq 0 ]] && grep -q '对账不完整' "$report" 2>/dev/null; then
      rc=1
      echo "reconcile-${label}: report says 对账不完整 although both side inputs were present (gate exit 1)" >> "$G/reconcile.stdout"
    fi
    echo "$rc" > "$G/reconcile-${label}.exitcode"
    echo "reconcile-${label}: window ${start}~${end}, gate exit ${rc}" >> "$G/reconcile.stdout"
    if [[ "$rc" != "0" ]]; then
      if [[ "$rc" -eq 2 ]]; then reconcile_gate=2; else reconcile_gate=1; fi
    fi
  done
fi
echo "$reconcile_gate" > "$G/reconcile.exitcode"
echo "reconcile: gate exit ${reconcile_gate}" >> "$record"

# --- aggregate ---------------------------------------------------------------
worst=0
for g in "$attempt_gate" "$collect_gate" "$sat_gate" "$reconcile_gate"; do
  if [[ "$g" -eq 2 ]]; then worst=2; elif [[ "$g" -ne 0 && "$worst" -ne 2 ]]; then worst=1; fi
done

{
  echo "# performance attempt judgement gates"
  echo "# attempt dir: ${attempt_dir}"
  echo "# judged at (UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  for g in attempt-json collect-verdicts check-saturation reconcile; do
    printf '%-18s exit=%s\n' "$g" "$(cat "$G/${g}.exitcode" 2>/dev/null || echo n/a)"
  done
  printf '%-18s exit=%s\n' "aggregate" "$worst"
} > "$G/gates-summary.txt"
cat "$G/gates-summary.txt"
{
  echo "aggregate exit=${worst} ($([[ "$worst" -eq 0 ]] && echo 'all gates passed' || echo 'not all gates passed'))"
} >> "$record"

exit "$worst"
