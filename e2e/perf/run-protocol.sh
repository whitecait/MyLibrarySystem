#!/usr/bin/env bash
# One independent performance-protocol ATTEMPT (RA-0013 §11 / acceptance plan
# §4.2). Every invocation creates its own attempt directory
# (attempt-<UTC timestamp>-<pid>-run<N>-<random>) under the evidence root and
# writes ALL of its evidence there; nothing is ever appended into, or reused
# from, a directory created by another invocation. Each attempt dir carries
# attempt.json recording the candidate commit, script versions, compose
# configuration, start/end times and a validity verdict with its basis. An
# attempt whose attempt.json does not end with valid=true is incomplete
# evidence, even if some stages ran; whether a completed attempt is ADOPTED
# as acceptance evidence is a separate decision recorded by the acceptance
# summary.
#
# Background: the AT-0016 phase-4 evidence got contaminated because every
# ROUND reused one fixed run-N directory while sample-resources.sh appended
# metrics-samples.log across invocations, so an abandoned/interrupted attempt
# leaked into the next attempt's files (see
# artifacts/m6-acceptance/phase4/at-0016/CONTAMINATION-NOTE.md). The fresh
# per-attempt directory below is the structural fix; sample-resources.sh
# refuses to write into a directory that already holds samples.
#
#   RUN=1 bash e2e/perf/run-protocol.sh <compose-project> <evidence-root>
#
# Protocol window (2026-09-22 reduced set, lingzz ruling): warmup 60s, steady
# 300s, retrieval four sub-scenarios 180s each, peak 120s ramp + 180s hold,
# recovery 300s — about 28 minutes of measured wall clock per attempt. The
# reduction moved the durations only: the response-class thresholds
# (lib/common.js), the 60-second saturation judgement window and the
# sampling/valid-sample semantics are unchanged, and a class below the
# 100-valid-sample floor is registered samples_insufficient without
# invalidating the attempt (collect-verdicts.mjs). How many attempts a
# candidate needs is registered in docs/agents/M6-ACCEPTANCE-PLAN-20260919.md,
# not decided here.
#
# Legacy protocol asks for three attempts; P0 approved one full P4 attempt
# and removal of P99 assertions. Set PERF_PERCENTILE_SCOPE=p95 explicitly
# for that approved run; numeric P95 limits and saturation rules are unchanged.
# Saturation re-runs count as
# independent attempts. Prerequisites: stack up with compose.yaml +
# compose.observability.yaml + compose.perf-quota.yaml; dataset loaded
# (e2e/data/load-dataset.sh) and queries copied to e2e/perf/out/; K6_IMAGE_TAG
# pinned. The host must run nothing beyond the environment containers during
# the window (resource snapshots before/after are part of the evidence).
#
# Window environment evidence (D6/D7 of the W2 fix batch): the protocol owns
# the window's docker environment/quota collection (plan 4.8) instead of
# leaving it to the round driver. It starts scripts/collect-env-evidence.sh
# detached (nohup/setsid, PID file, done marker) before the first k6 scenario
# and stops it after the last one, so the collector cannot be cut off by the
# round driver exiting (the W2a defect: SIGHUP left env-snapshot.json,
# container-quotas.txt and the after-inventory missing). The outcome is
# recorded in attempt.json under "window_env_collector". Knobs:
# ENV_COLLECT=0 skips the collector, ENV_COLLECT_POLL_SECONDS (default 5400)
# bounds its poll window, ENV_COLLECT_STOP_WAIT_SECONDS (default 60) bounds the
# wait for its done marker. Everything that can render environment values —
# ${attempt_dir}/compose-resolved.yaml above all — is written through
# scripts/redact-evidence-values.sh against the value set built from ENV_FILE
# and the repository .env files, and the attempt tree is scanned with
# scripts/check-sensitive-evidence.sh before the attempt is declared complete:
# a residual credential value invalidates the attempt (the W2a defect: a
# rendered config written verbatim can carry DB_PASSWORD).
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
source e2e/perf/lib/window-env-collector.sh

project="${1:?usage: RUN=n run-protocol.sh <compose-project> <evidence-root>}"
evidence="${2:?usage: RUN=n run-protocol.sh <compose-project> <evidence-root>}"
run="${RUN:?set RUN=n (independent attempt number)}"

# The k6 service exists only in compose.perf-quota.yaml (profile [perf]) and
# the quota ceiling must actually be part of the measured stack, so every
# compose call pins the full overlay set explicitly. ENV_FILE (optional)
# carries the required DB_PASSWORD / AI_VISITOR_COOKIE_SIGNING_KEY etc.;
# without it `compose config` fails variable validation on the base file.
compose_base=(-p "$project"
  -f compose.yaml -f compose.observability.yaml -f compose.perf-quota.yaml
  ${ENV_FILE:+--env-file "$ENV_FILE"})
compose() { docker compose "${compose_base[@]}" --profile perf "$@"; }

# Preflight (before any attempt directory exists): fail fast on wiring
# mistakes instead of mid-protocol.
services="$(compose config --services 2>/dev/null || true)"
if ! grep -qx k6 <<<"$services"; then
  echo "preflight: k6 service not found under the pinned overlay set (compose.perf-quota.yaml, profile perf). Check ENV_FILE for required vars." >&2
  exit 2
fi
for f in warmup steady retrieval peak recovery; do
  [[ -f "e2e/perf/scenarios/${f}.js" ]] || { echo "preflight: missing e2e/perf/scenarios/${f}.js" >&2; exit 2; }
done
[[ -f e2e/perf/lib/sanitize-k6-summary.mjs ]] || {
  echo "preflight: missing e2e/perf/lib/sanitize-k6-summary.mjs (summary evidence cannot be made credential-free)" >&2
  exit 2
}
for set in hot tail; do
  [[ -f "e2e/perf/out/queries-${set}.json" ]] || { echo "preflight: missing e2e/perf/out/queries-${set}.json (run the dataset generator first)" >&2; exit 2; }
done

# A hard interruption can leave k6's raw --summary-export file in the shared
# output mount. Remove only the exact names owned by this protocol before the
# next attempt starts; query fixtures (queries-hot.json / queries-tail.json)
# and every other output are outside this allow-list. A path that cannot be
# removed is a hard preflight failure, because proceeding would make a stale
# raw summary indistinguishable from the next attempt's output.
protocol_summary_labels=(
  warmup steady
  retrieval-cold-hot retrieval-hot-hot retrieval-cold-tail retrieval-hot-tail
  peak recovery
)
for label in "${protocol_summary_labels[@]}"; do
  stale_summary="e2e/perf/out/${label}-summary.json"
  if [[ -e "$stale_summary" || -L "$stale_summary" ]]; then
    if ! rm -f -- "$stale_summary"; then
      echo "preflight: stale protocol summary cannot be removed: ${stale_summary}; refusing to run" >&2
      exit 2
    fi
    echo "preflight: removed stale protocol summary ${stale_summary}"
  fi
done

# --- attempt identity: timestamp + unique attempt id, fresh directory ------
attempt_id="$(date -u +%Y%m%dT%H%M%SZ)-p$$-run${run}-${RANDOM}"
attempt_dir="${evidence}/attempt-${attempt_id}"
mkdir -p "$attempt_dir"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

candidate_commit="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
dirty="false"; [[ -n "$(git status --porcelain 2>/dev/null)" ]] && dirty="true"
sha() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1 || echo unknown; }
proto_sha="$(sha e2e/perf/run-protocol.sh)"
sampler_sha="$(sha e2e/perf/sample-resources.sh)"
sat_sha="$(sha e2e/perf/check-saturation.mjs)"
sanitizer_sha="$(sha e2e/perf/lib/sanitize-k6-summary.mjs)"

json_str() { # minimal JSON string escaping for the controlled values below
  local s="$1"; s=${s//\\/\\\\}; s=${s//\"/\\\"}; s=${s//$'\t'/ }; s=${s//$'\n'/ }
  printf '"%s"' "$s"
}

json_number_or_str() { # json_number_or_str <value>: a numeric token when it is
  # all digits, otherwise a quoted string — an env-var typo lands in attempt.json
  # as a visible string instead of producing an unparseable file.
  if [[ "$1" =~ ^[0-9]+$ ]]; then
    printf '%s' "$1"
  else
    json_str "$1"
  fi
}

# --- window env collector state (D6) + redaction value set (D7) --------------
# Both are declared before the first attempt.json write so the attempt file is
# truthful from its first byte; both are updated as the protocol advances.
env_collector_enabled="false"
env_collector_dir=""
env_collector_status="disabled"
secret_values_file=""

env_collector_json() { # env_collector_json <status-token> -> JSON object
  local status="$1" dir_json="null"
  if [[ -n "$env_collector_dir" ]]; then
    dir_json="$(json_str "$env_collector_dir")"
  fi
  printf '{"enabled":%s,"status":%s,"dir":%s,"poll_seconds":%s,"stop_wait_seconds":%s}' \
    "$env_collector_enabled" "$(json_str "$status")" "$dir_json" \
    "$(json_number_or_str "${ENV_COLLECT_POLL_SECONDS:-5400}")" \
    "$(json_number_or_str "${ENV_COLLECT_STOP_WAIT_SECONDS:-60}")"
}

attempt_write() { # attempt_write <ended-token> <valid-token> <basis-token> <env-collector-token>
  # Tokens are raw JSON values, spliced in verbatim: null / true / false for the
  # first two, an already quoted string (json_str) for the basis, and a JSON
  # object (env_collector_json) for the window env collector. Called once at
  # attempt start (all null) and once more on the single exit path (finalize)
  # so attempt.json always reflects reality.
  {
    printf '{"attempt_id":%s,"run_label":%s,"candidate_commit":%s,"working_tree_dirty":%s,' \
      "$(json_str "$attempt_id")" "$(json_str "$run")" "$(json_str "$candidate_commit")" "$dirty"
    printf '"script_versions":{"run-protocol.sh":%s,"sample-resources.sh":%s,"check-saturation.mjs":%s,"sanitize-k6-summary.mjs":%s},' \
      "$(json_str "$proto_sha")" "$(json_str "$sampler_sha")" "$(json_str "$sat_sha")" "$(json_str "$sanitizer_sha")"
    printf '"compose":{"project":%s,"files":["compose.yaml","compose.observability.yaml","compose.perf-quota.yaml"],"profile":"perf","env_file":%s,"resolved_config":"compose-resolved.yaml"},' \
      "$(json_str "$project")" "$(json_str "${ENV_FILE:-<unset>}")"
    printf '"window_env_collector":%s,' "$4"
    printf '"started_at":%s,"ended_at":%s,"valid":%s,"basis":%s,' \
      "$(json_str "$started_at")" "$1" "$2" "$3"
    printf '"percentile_scope":%s,' "$(json_str "${PERF_PERCENTILE_SCOPE:-full}")"
    printf '"note":"valid=true means the protocol ran to completion inside this one attempt; whether the attempt is adopted as acceptance evidence is a separate decision recorded by the acceptance summary. An attempt.json left without valid=true marks an incomplete attempt: never merge its files with another attempt."}\n'
  } > "${attempt_dir}/attempt.json"
}
attempt_write null null null "$(env_collector_json "pending")"

# Single exit path: close attempt.json according to what actually happened.
completed="false"
basis_final=""
err_info="interrupted before completion (no failing command recorded)"
stage="init"
sampler_pid=""
k6_summary_source=""
error_detail_override=""
cleanup_failures=""
record_cleanup_failure() {
  local detail="$1"
  if [[ -n "$cleanup_failures" ]]; then
    cleanup_failures+="; "
  fi
  cleanup_failures+="$detail"
}
finalize() {
  local code=$?
  [[ -z "$sampler_pid" ]] || kill "$sampler_pid" 2>/dev/null || true
  if [[ -n "$k6_summary_source" ]]; then
    if ! rm -f -- "$k6_summary_source"; then
      echo "warning: finalize could not remove raw k6 summary ${k6_summary_source}" >&2
      record_cleanup_failure "raw summary cleanup failed: ${k6_summary_source}"
    else
      k6_summary_source=""
    fi
  fi
  if [[ -n "$secret_values_file" ]]; then
    if ! rm -f -- "$secret_values_file"; then
      echo "warning: finalize could not remove temporary secret value set" >&2
      record_cleanup_failure "temporary secret value-set cleanup failed: ${secret_values_file}"
    else
      secret_values_file=""
    fi
  fi
  # Whatever happened, the window collector must not outlive the attempt's
  # window: the stop file ends its poll loop (the poll deadline is the second
  # bound, so a forgotten call site cannot leave a poller running).
  if [[ -n "$env_collector_dir" ]]; then
    : >> "$env_collector_dir/collector.stop" 2>/dev/null || true
    if [[ "$completed" != "true" && "$env_collector_status" == "started" ]]; then
      env_collector_status="aborted-with-attempt:stop-file-touched"
    fi
  fi
  if [[ -n "$cleanup_failures" ]]; then
    completed="false"
    if [[ -n "$err_info" && "$err_info" != "interrupted before completion (no failing command recorded)" ]]; then
      err_info+="; ${cleanup_failures}"
    else
      err_info="$cleanup_failures"
    fi
    if [[ "$code" -eq 0 ]]; then
      code=1
    fi
  fi
  if [[ "$completed" == "true" ]]; then
    attempt_write "\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"" true "$(json_str "$basis_final")" \
      "$(env_collector_json "$env_collector_status")"
  else
    attempt_write "\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"" false \
      "$(json_str "attempt invalid: ${err_info}; files in this attempt dir are partial evidence of one attempt and must never be read as one series with another attempt's files")" \
      "$(env_collector_json "$env_collector_status")"
  fi
  exit "$code"
}
on_err() {
  if [[ -n "$error_detail_override" ]]; then
    err_info="$error_detail_override"
  else
    err_info="failed at stage [${stage}] while running [${BASH_COMMAND}] (run-protocol.sh:${LINENO})"
  fi
}
trap on_err ERR
trap finalize EXIT

# Freeze the resolved compose configuration inside the attempt dir so the
# compose section of attempt.json and the actually measured stack cannot
# drift apart. The render carries the stack's environment values (DB_PASSWORD
# and friends), so it is filtered through the value set while it is written:
# nothing unredacted reaches the file, not even transiently.
stage="compose config render"
secret_values_file="$(mktemp)"
env_files_for_secrets=()
if [[ -n "${ENV_FILE:-}" ]]; then
  env_files_for_secrets+=("$ENV_FILE")
fi
for candidate in .env .env.observation .env.perf; do
  if [[ -f "$candidate" ]]; then
    env_files_for_secrets+=("$candidate")
  fi
done
if (( ${#env_files_for_secrets[@]} == 0 )); then
  err_info="no env file to derive the secret-value set from (ENV_FILE unset and no .env/.env.observation/.env.perf present), so the resolved compose config cannot be redacted"
  exit 2
fi
if ! bash scripts/collect-evidence-secret-values.sh "${env_files_for_secrets[@]}" "$secret_values_file"; then
  err_info="building the secret-value set failed; refusing to write a resolved compose config that cannot be redacted"
  exit 2
fi
if [[ ! -s "$secret_values_file" ]]; then
  err_info="the secret-value set derived from ${env_files_for_secrets[*]} is empty; refusing to write a resolved compose config that cannot be redacted"
  exit 2
fi
compose config | bash scripts/redact-evidence-values.sh --stdin "$secret_values_file" \
  > "${attempt_dir}/compose-resolved.yaml"
resolved_scan_status=0
bash scripts/check-sensitive-evidence.sh "${attempt_dir}/compose-resolved.yaml" "$secret_values_file" \
  || resolved_scan_status=$?
case "$resolved_scan_status" in
  0)
    ;;
  1)
    err_info="the rendered compose config still carries a credential value after redaction (scan of ${attempt_dir}/compose-resolved.yaml reported a hit); the attempt is invalid and its evidence must not be used"
    exit 1
    ;;
  *)
    err_info="the redaction self-check of ${attempt_dir}/compose-resolved.yaml could not run (scanner exit ${resolved_scan_status}); the attempt is invalid rather than unverified-but-claimed-clean"
    exit 2
    ;;
esac

# k6 reaches the stack through the project network (frontend Nginx entry).
export BASE_URL="http://frontend:80"
export RUN_ID="run${run}-$(date -u +%Y%m%dT%H%M%S)"

echo "frontend entry: ${BASE_URL} (from inside the compose network)"
echo "attempt dir: ${attempt_dir}"
docker stats --no-stream > "${attempt_dir}/resources-before.txt" 2>/dev/null || true

# Window environment/quota evidence (plan 4.8): started detached before any
# load, so it survives this script exiting and the round driver exiting with
# it. A start failure does not abort the attempt — it is recorded as
# start-failed in attempt.json and reported loudly — but the attempt then
# carries no window env evidence, which the acceptance summary must see.
stage="window env collector start"
if [[ "${ENV_COLLECT:-1}" == "1" ]]; then
  env_collector_enabled="true"
  env_collector_status="pending"
  if window_env_collector_start "$project" "$attempt_dir" "${ENV_COLLECT_POLL_SECONDS:-5400}"; then
    env_collector_dir="$WINDOW_ENV_COLLECTOR_DIR"
    env_collector_status="started"
    echo "window env collector: detached, dir=${env_collector_dir}, poll=${ENV_COLLECT_POLL_SECONDS:-5400}s"
  else
    env_collector_status="start-failed"
    echo "warning: the window env collector did not start; this attempt will carry no env-snapshot.json/container-quotas.txt" >&2
  fi
else
  env_collector_status="disabled-by-env"
  echo "window env collector: disabled (ENV_COLLECT=${ENV_COLLECT:-unset})"
fi

bash e2e/perf/sample-resources.sh "$project" "$attempt_dir" 5 &
sampler_pid=$!

k6run() { # k6run <scenario.js> <label> [env...]
  local script="$1" label="$2"; shift 2
  stage="k6 ${label}"
  echo "[$(date -u +%H:%M:%S)] k6 ${label}"
  k6_summary_source="e2e/perf/out/${label}-summary.json"
  env "$@" docker compose "${compose_base[@]}" --profile perf run --rm \
    k6 run --summary-export "/scripts/out/${label}-summary.json" "/scripts/scenarios/${script}" \
    > "${attempt_dir}/${label}-stdout.log" 2>&1
  # k6 puts setup()'s return value under setup_data in --summary-export. The
  # scenarios need those bearer tokens at runtime, but they are not metrics
  # and must never enter long-lived evidence. Sanitize into the attempt tree
  # before the raw shared output is removed; collect-verdicts/reconcile read
  # the resulting metric-only summary and therefore keep their contracts.
  node e2e/perf/lib/sanitize-k6-summary.mjs "$k6_summary_source" \
    > "${attempt_dir}/${label}-summary.json"
  if ! rm -f -- "$k6_summary_source"; then
    error_detail_override="raw k6 summary cleanup failed: ${k6_summary_source}; attempt invalid and the file may require manual removal"
    err_info="$error_detail_override"
    return 1
  fi
  k6_summary_source=""
}

# 1) warmup 60s (excluded from statistics)
k6run warmup.js warmup RUN_ID="$RUN_ID" BASE_URL="$BASE_URL"

# 2) steady mixed load 300s
k6run steady.js steady RUN_ID="$RUN_ID" BASE_URL="$BASE_URL"

# 3) retrieval专项: for each set, cold (database restart) then hot back-to-back
for set in hot tail; do
  stage="cold cache restart (${set})"
  echo "[$(date -u +%H:%M:%S)] cold cache: restarting database"
  compose restart database > /dev/null 2>&1
  for _ in $(seq 1 60); do
    status="$(compose ps --format '{{.Health}}' database | head -1 || true)"
    [[ "$status" == "healthy" ]] && break
    sleep 2
  done
  k6run retrieval.js "retrieval-cold-${set}" RETRIEVAL_SET="$set" BASE_URL="$BASE_URL"
  k6run retrieval.js "retrieval-hot-${set}" RETRIEVAL_SET="$set" BASE_URL="$BASE_URL"
done

# 4) peak 180s hold after a 120s ramp + recovery 300s
k6run peak.js peak BASE_URL="$BASE_URL"
k6run recovery.js recovery BASE_URL="$BASE_URL"

stage="resources after"
docker stats --no-stream > "${attempt_dir}/resources-after.txt" 2>/dev/null || true
kill "$sampler_pid" 2>/dev/null || true
sampler_pid=""
sleep 2

# The load window is over: end the detached env collector through its stop file
# and wait (bounded) for its done marker. `done:<exit_code>` is the only outcome
# that means the window env evidence is complete; anything else is recorded
# verbatim in attempt.json rather than glossed over.
if [[ -n "$env_collector_dir" ]]; then
  stage="window env collector stop"
  env_collector_status="$(window_env_collector_stop "$env_collector_dir" "${ENV_COLLECT_STOP_WAIT_SECONDS:-60}")"
  echo "window env collector: ${env_collector_status} (dir=${env_collector_dir})"
  if [[ "$env_collector_status" != "done:0" ]]; then
    echo "warning: window env evidence is not complete (collector ${env_collector_status}); check ${env_collector_dir}" >&2
  fi
fi

stage="saturation check"
sat_note="saturation check FAILED (no verdict written)"
node e2e/perf/check-saturation.mjs "$attempt_dir" --out "${attempt_dir}/saturation-verdict.json" \
  && sat_note="saturation-verdict.json written" || true

# Verdict summarization points at e2e/perf/collect-verdicts.mjs when it
# exists. Its contract: `node e2e/perf/collect-verdicts.mjs <evidence-dir>`
# aggregates every <evidence-dir>/run-N/*-summary.json + saturation-verdict.json
# and writes verdicts.json / verdicts-summary.md at that dir. Each attempt
# snapshots its own summaries into its attempt dir, so an orchestrator can
# stage adopted attempts as run-N dirs and this call summarizes them; with no
# run-N dirs present yet (or a not-green verdict) it exits nonzero, which is
# reported here but is not an attempt failure — attempt validity in
# attempt.json records protocol completion, not the acceptance gate.
if [[ -f e2e/perf/collect-verdicts.mjs ]]; then
  stage="summary collector"
  node e2e/perf/collect-verdicts.mjs "$evidence" \
    || echo "warning: collect-verdicts.mjs exited nonzero for ${evidence} (no run-N dirs staged yet, or gate not green)" >&2
fi

# Final redaction self-check over the whole attempt tree (D7): the attempt is
# declared complete only when its tree carries no credential value. A scan that
# could not run leaves the tree unverified, which is not the same as clean.
stage="attempt tree secret scan"
tree_scan_status=0
bash scripts/check-sensitive-evidence.sh "$attempt_dir" "$secret_values_file" || tree_scan_status=$?
case "$tree_scan_status" in
  0)
    tree_scan_note="attempt tree secret scan: clean"
    ;;
  1)
    err_info="credential value found in ${attempt_dir} (scanner exit 1); the attempt is invalid and its evidence must not be used"
    exit 1
    ;;
  *)
    err_info="the attempt tree secret scan could not run (scanner exit ${tree_scan_status}); the attempt is invalid rather than unverified-but-claimed-clean"
    exit 2
    ;;
esac

basis_final="protocol completed inside this attempt: warmup, steady, retrieval cold+hot for hot and tail sets, peak and recovery all ran; resources-before.txt, resources-after.txt, eight k6 stdout logs and ${sat_note}; window env collector: ${env_collector_status}; ${tree_scan_note}"
completed="true"
stage="done"
echo "attempt ${attempt_id} complete; evidence in ${attempt_dir}"
echo "gate: every k6 summary must show thresholds OK and unexpected_failures==0; saturation verdict must be clean"
