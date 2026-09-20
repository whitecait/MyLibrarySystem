#!/usr/bin/env bash
# One independent performance-protocol pass (RA-0013 §11 / acceptance plan
# §4.2). Repeat at least three times per candidate (RUN=1,2,3) and report all
# results; saturation re-runs count as independent runs.
#
#   RUN=1 bash e2e/perf/run-protocol.sh <compose-project> <evidence-dir>
#
# Prerequisites: stack up with compose.yaml + compose.observability.yaml +
# compose.perf-quota.yaml; dataset loaded (e2e/data/load-dataset.sh) and
# queries copied to e2e/perf/out/; K6_IMAGE_TAG pinned. The host must run
# nothing beyond the environment containers during the window (resource
# snapshots before/after are part of the evidence).
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

project="${1:?usage: RUN=n run-protocol.sh <compose-project> <evidence-dir>}"
evidence="${2:?usage: RUN=n run-protocol.sh <compose-project> <evidence-dir>}"
run="${RUN:?set RUN=n (independent run number)}"
mkdir -p "$evidence/run-${run}"

# The k6 service exists only in compose.perf-quota.yaml (profile [perf]) and
# the quota ceiling must actually be part of the measured stack, so every
# compose call pins the full overlay set explicitly. ENV_FILE (optional)
# carries the required DB_PASSWORD / AI_VISITOR_COOKIE_SIGNING_KEY etc.;
# without it `compose config` fails variable validation on the base file.
compose_base=(-p "$project"
  -f compose.yaml -f compose.observability.yaml -f compose.perf-quota.yaml
  ${ENV_FILE:+--env-file "$ENV_FILE"})
compose() { docker compose "${compose_base[@]}" --profile perf "$@"; }
k6run() { # k6run <scenario.js> <label> [env...]
  local script="$1" label="$2"; shift 2
  echo "[$(date -u +%H:%M:%S)] k6 ${label}"
  env "$@" docker compose "${compose_base[@]}" --profile perf run --rm \
    k6 run --summary-export "/scripts/out/${label}-summary.json" "/scripts/scenarios/${script}" \
    > "$evidence/run-${run}/${label}-stdout.log" 2>&1
}

# Preflight: fail fast on wiring mistakes instead of mid-protocol.
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

# k6 reaches the stack through the project network (frontend Nginx entry).
export BASE_URL="http://frontend:80"
export RUN_ID="run${run}-$(date -u +%Y%m%dT%H%M%S)"

echo "frontend entry: ${BASE_URL} (from inside the compose network)"
docker stats --no-stream > "$evidence/run-${run}/resources-before.txt" 2>/dev/null || true

bash e2e/perf/sample-resources.sh "$project" "$evidence/run-${run}" 5 &
SAMPLER_PID=$!
trap 'kill "$SAMPLER_PID" 2>/dev/null || true' EXIT

# 1) warmup 5m (excluded from statistics)
k6run warmup.js warmup RUN_ID="$RUN_ID" BASE_URL="$BASE_URL"

# 2) steady mixed load 10m
k6run steady.js steady RUN_ID="$RUN_ID" BASE_URL="$BASE_URL"

# 3) retrieval专项: for each set, cold (database restart) then hot back-to-back
for set in hot tail; do
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

docker stats --no-stream > "$evidence/run-${run}/resources-after.txt" 2>/dev/null || true
kill "$SAMPLER_PID" 2>/dev/null || true
sleep 2
node e2e/perf/check-saturation.mjs "$evidence/run-${run}" --out "$evidence/run-${run}/saturation-verdict.json" || true

echo "run ${run} complete; evidence in $evidence/run-${run}"
echo "gate: every k6 summary must show thresholds OK and unexpected_failures==0; saturation verdict must be clean"
