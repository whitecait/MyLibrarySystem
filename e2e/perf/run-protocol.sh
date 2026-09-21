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
# Repeat at least three times per candidate; saturation re-runs count as
# independent attempts. Prerequisites: stack up with compose.yaml +
# compose.observability.yaml + compose.perf-quota.yaml; dataset loaded
# (e2e/data/load-dataset.sh) and queries copied to e2e/perf/out/; K6_IMAGE_TAG
# pinned. The host must run nothing beyond the environment containers during
# the window (resource snapshots before/after are part of the evidence).
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

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
for set in hot tail; do
  [[ -f "e2e/perf/out/queries-${set}.json" ]] || { echo "preflight: missing e2e/perf/out/queries-${set}.json (run the dataset generator first)" >&2; exit 2; }
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

json_str() { # minimal JSON string escaping for the controlled values below
  local s="$1"; s=${s//\\/\\\\}; s=${s//\"/\\\"}; s=${s//$'\t'/ }; s=${s//$'\n'/ }
  printf '"%s"' "$s"
}

attempt_write() { # attempt_write <ended-token> <valid-token> <basis-token>
  # Tokens are raw JSON values: null / true / false / a quoted string (built
  # with json_str). Called once at attempt start (all null) and once more on
  # the single exit path (finalize) so attempt.json always reflects reality.
  {
    printf '{"attempt_id":%s,"run_label":%s,"candidate_commit":%s,"working_tree_dirty":%s,' \
      "$(json_str "$attempt_id")" "$(json_str "$run")" "$(json_str "$candidate_commit")" "$dirty"
    printf '"script_versions":{"run-protocol.sh":%s,"sample-resources.sh":%s,"check-saturation.mjs":%s},' \
      "$(json_str "$proto_sha")" "$(json_str "$sampler_sha")" "$(json_str "$sat_sha")"
    printf '"compose":{"project":%s,"files":["compose.yaml","compose.observability.yaml","compose.perf-quota.yaml"],"profile":"perf","env_file":%s,"resolved_config":"compose-resolved.yaml"},' \
      "$(json_str "$project")" "$(json_str "${ENV_FILE:-<unset>}")"
    printf '"started_at":%s,"ended_at":%s,"valid":%s,"basis":%s,' \
      "$(json_str "$started_at")" "$1" "$2" "$3"
    printf '"note":"valid=true means the protocol ran to completion inside this one attempt; whether the attempt is adopted as acceptance evidence is a separate decision recorded by the acceptance summary. An attempt.json left without valid=true marks an incomplete attempt: never merge its files with another attempt."}\n'
  } > "${attempt_dir}/attempt.json"
}
attempt_write null null null

# Single exit path: close attempt.json according to what actually happened.
completed="false"
basis_final=""
err_info="interrupted before completion (no failing command recorded)"
stage="init"
sampler_pid=""
finalize() {
  local code=$?
  [[ -z "$sampler_pid" ]] || kill "$sampler_pid" 2>/dev/null || true
  if [[ "$completed" == "true" ]]; then
    attempt_write "\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"" true "$(json_str "$basis_final")"
  else
    attempt_write "\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"" false \
      "$(json_str "attempt invalid: ${err_info}; files in this attempt dir are partial evidence of one attempt and must never be read as one series with another attempt's files")"
  fi
  exit "$code"
}
on_err() { err_info="failed at stage [${stage}] while running [${BASH_COMMAND}] (run-protocol.sh:${LINENO})"; }
trap on_err ERR
trap finalize EXIT

# Freeze the resolved compose configuration inside the attempt dir so the
# compose section of attempt.json and the actually measured stack cannot
# drift apart.
stage="compose config render"
compose config > "${attempt_dir}/compose-resolved.yaml"

# k6 reaches the stack through the project network (frontend Nginx entry).
export BASE_URL="http://frontend:80"
export RUN_ID="run${run}-$(date -u +%Y%m%dT%H%M%S)"

echo "frontend entry: ${BASE_URL} (from inside the compose network)"
echo "attempt dir: ${attempt_dir}"
docker stats --no-stream > "${attempt_dir}/resources-before.txt" 2>/dev/null || true

bash e2e/perf/sample-resources.sh "$project" "$attempt_dir" 5 &
sampler_pid=$!

k6run() { # k6run <scenario.js> <label> [env...]
  local script="$1" label="$2"; shift 2
  stage="k6 ${label}"
  echo "[$(date -u +%H:%M:%S)] k6 ${label}"
  env "$@" docker compose "${compose_base[@]}" --profile perf run --rm \
    k6 run --summary-export "/scripts/out/${label}-summary.json" "/scripts/scenarios/${script}" \
    > "${attempt_dir}/${label}-stdout.log" 2>&1
  # Snapshot this attempt's summary out of the shared export dir immediately:
  # e2e/perf/out is overwritten by every later scenario and attempt, so a
  # summary left only there is no longer attributable to this attempt.
  cp -f "e2e/perf/out/${label}-summary.json" "${attempt_dir}/${label}-summary.json"
}

# 1) warmup 5m (excluded from statistics)
k6run warmup.js warmup RUN_ID="$RUN_ID" BASE_URL="$BASE_URL"

# 2) steady mixed load 10m
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

# 4) peak 10m + recovery 10m
k6run peak.js peak BASE_URL="$BASE_URL"
k6run recovery.js recovery BASE_URL="$BASE_URL"

stage="resources after"
docker stats --no-stream > "${attempt_dir}/resources-after.txt" 2>/dev/null || true
kill "$sampler_pid" 2>/dev/null || true
sampler_pid=""
sleep 2

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

basis_final="protocol completed inside this attempt: warmup, steady, retrieval cold+hot for hot and tail sets, peak and recovery all ran; resources-before.txt, resources-after.txt, eight k6 stdout logs and ${sat_note}"
completed="true"
stage="done"
echo "attempt ${attempt_id} complete; evidence in ${attempt_dir}"
echo "gate: every k6 summary must show thresholds OK and unexpected_failures==0; saturation verdict must be clean"
