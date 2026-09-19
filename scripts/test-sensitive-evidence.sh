#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."

scanner="scripts/check-sensitive-evidence.sh"
fixture="artifacts/sensitive-evidence-test-$$"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture"

canary='integration-sensitive-canary-value'
secret_file="$fixture-secret.txt"
trap 'rm -rf "$fixture"; rm -f "$secret_file"' EXIT
printf '%s\n' "$canary" > "$secret_file"
printf '{"accessToken":"%s"}\n' "$canary" > "$fixture/ignored-login.json"
if "$scanner" "$fixture" "$secret_file" >/dev/null 2>&1; then
  echo 'scanner accepted a sensitive value in ignored evidence' >&2
  exit 1
fi

printf '{"accessToken":"[REDACTED]"}\n' > "$fixture/ignored-login.json"
"$scanner" "$fixture" "$secret_file"
printf 'sensitive evidence scanner test passed\n'
