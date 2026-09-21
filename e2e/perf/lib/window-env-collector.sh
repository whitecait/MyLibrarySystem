#!/usr/bin/env bash
# Window environment-evidence collector lifecycle for the performance protocol
# (D6 of the W2 M6 fix batch). Sourced by e2e/perf/run-protocol.sh; not meant to
# be executed directly.
#
# Why this exists: the W2a window ran scripts/collect-env-evidence.sh as an
# ordinary child of the round driver. When the driver exited, the collector was
# cut off by SIGHUP, so env-snapshot.json, container-quotas.txt and the after-
# inventory were never written and had to be recomputed from the raw inspects
# afterwards. Starting it detached (--detach) removes that failure mode; a
# stop file plus the poll deadline bound how long it can run, and a done marker
# makes "the window evidence is complete" a fact the round can check instead of
# a file that may or may not be there.
#
# Contract (all functions are safe under `set -Eeuo pipefail`; none of them
# aborts the caller — they report through their return status or their printed
# token):
#   window_env_collector_start <compose-project> <attempt-dir> [poll-seconds]
#       Starts the collector detached, writing into <attempt-dir>/env-collector.
#       ENV_FILE, when set, is passed as an additional --secret-env-file so the
#       redaction value set covers the env file the stack was started with.
#       Returns 0 and sets WINDOW_ENV_COLLECTOR_DIR / WINDOW_ENV_COLLECTOR_STOP_FILE;
#       returns 1 (message on stderr) when the detached collector did not come
#       up. The collector's own stdout is forwarded to stderr so a round log
#       keeps the PID line without polluting the caller's stdout.
#       Call it as a plain command (e.g. `if window_env_collector_start ...`),
#       never inside a command substitution: the directory is reported through
#       those variables, and a subshell would discard them.
#   window_env_collector_stop <collector-dir> [wait-seconds]
#       Creates the stop file and waits up to <wait-seconds> for
#       collector-done.txt. Prints exactly one token: `done:<exit_code>`,
#       `timeout`, or `absent` when no collector dir was given. Always returns 0.
set -Eeuo pipefail

WINDOW_ENV_COLLECTOR_DIR=""
WINDOW_ENV_COLLECTOR_STOP_FILE=""

window_env_collector_start() {
  local project="$1" attempt_dir="$2" poll_seconds="${3:-5400}"
  local dir="$attempt_dir/env-collector"
  local stderr_log="$attempt_dir/env-collector-start.stderr"
  if [ -z "$project" ] || [ -z "$attempt_dir" ]; then
    echo "window_env_collector_start: project and attempt dir are required" >&2
    return 1
  fi
  local -a args=(--project "$project" --out "$dir" --poll-seconds "$poll_seconds" --detach)
  # Value set for the source-level redaction: the env file the stack was started
  # with (ENV_FILE) plus the repository's own .env files. Over-redaction is the
  # safe bias here — a value set that misses the stack's env file would leave
  # credentials in the container inspect output.
  local -a wanted=() added=()
  if [ -n "${ENV_FILE:-}" ]; then
    wanted+=("$ENV_FILE")
  fi
  wanted+=(.env .env.observation .env.perf)
  local f g duplicate
  for f in "${wanted[@]}"; do
    if [ ! -f "$f" ]; then
      continue
    fi
    duplicate="false"
    for g in "${added[@]}"; do
      if [ "$g" = "$f" ]; then
        duplicate="true"
      fi
    done
    if [ "$duplicate" = "true" ]; then
      continue
    fi
    args+=(--secret-env-file "$f")
    added+=("$f")
  done
  # The collector's stdout (the PID line) is forwarded to the caller's stderr;
  # its own stderr is kept in the attempt dir for a failed start to be readable.
  if ! bash scripts/collect-env-evidence.sh "${args[@]}" 3>&2 1>&3 2>>"$stderr_log"; then
    echo "window env collector did not start (see $stderr_log)" >&2
    return 1
  fi
  WINDOW_ENV_COLLECTOR_DIR="$dir"
  WINDOW_ENV_COLLECTOR_STOP_FILE="$dir/collector.stop"
  return 0
}

window_env_collector_stop() {
  local dir="$1" wait_seconds="${2:-60}" waited=0
  local stop_file done_file exit_code
  if [ -z "$dir" ] || [ ! -d "$dir" ]; then
    printf 'absent\n'
    return 0
  fi
  stop_file="${WINDOW_ENV_COLLECTOR_STOP_FILE:-$dir/collector.stop}"
  : >> "$stop_file" || true
  done_file="$dir/collector-done.txt"
  while [ "$waited" -lt "$wait_seconds" ]; do
    if [ -s "$done_file" ]; then
      exit_code="$(sed -n 's/^exit_code=\([0-9]\{1,\}\).*/\1/p' "$done_file" | head -1)"
      printf 'done:%s\n' "${exit_code:-unknown}"
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  if [ -s "$done_file" ]; then
    exit_code="$(sed -n 's/^exit_code=\([0-9]\{1,\}\).*/\1/p' "$done_file" | head -1)"
    printf 'done:%s\n' "${exit_code:-unknown}"
    return 0
  fi
  printf 'timeout\n'
  return 0
}
