#!/usr/bin/env bash
# Self-test for scripts/check-sensitive-evidence.sh and
# scripts/check-sensitive-evidence-deep.sh. Each case asserts one expected
# exit code (plus, where noted, one required output line). Fixtures are
# rebuilt in a fresh mktemp directory on every run, so the suite is
# repeatable.
#
# Output: one block per case with the exact command, the actual and expected
# exit code, and PASS/FAIL/SKIP. The suite exits 0 only when every
# non-skipped case passes; a SKIP states its reason.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

base_scanner="scripts/check-sensitive-evidence.sh"
deep_scanner="scripts/check-sensitive-evidence-deep.sh"

need() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || {
      echo "required tool missing from PATH: $tool" >&2
      exit 1
    }
  done
}
need rg python3 awk mktemp find sed wc grep id bash

work="$(mktemp -d)"
trap 'chmod -R u+rwx "$work" >/dev/null 2>&1 || true; rm -rf "$work" || true' EXIT

canary='integration-sensitive-canary-value'
secret_file="$work/secret.txt"
printf '%s\n' "$canary" > "$secret_file"

pass=0
fail=0
skip=0

# run_case ID DESCRIPTION EXPECTED_EXIT MUST_CONTAIN CMD...
#   Runs CMD..., captures combined output, asserts the exit code equals
#   EXPECTED_EXIT and, when MUST_CONTAIN is non-empty, that the output
#   contains that literal string.
run_case() {
  local id="$1" desc="$2" expected="$3" must_contain="$4"
  shift 4
  local out_file="$work/case-$id.log"
  local actual=0
  "$@" >"$out_file" 2>&1 || actual=$?
  local ok=1
  [[ "$actual" == "$expected" ]] || ok=0
  if [[ -n "$must_contain" ]] && ! grep -qF -- "$must_contain" "$out_file"; then
    ok=0
  fi
  if [[ "$ok" == "1" ]]; then
    pass=$((pass + 1))
    echo "CASE $id PASS: $desc"
    echo "  command: $*"
    echo "  exit: $actual (expected $expected)"
    [[ -z "$must_contain" ]] || echo "  output contains: $must_contain"
  else
    fail=$((fail + 1))
    echo "CASE $id FAIL: $desc" >&2
    echo "  command: $*" >&2
    echo "  exit: $actual (expected $expected)" >&2
    [[ -z "$must_contain" ]] || echo "  output must contain: $must_contain" >&2
    echo "  captured output:" >&2
    sed 's/^/    /' "$out_file" >&2
  fi
}

skip_case() {
  local id="$1" desc="$2" reason="$3"
  skip=$((skip + 1))
  echo "CASE $id SKIP: $desc"
  echo "  reason: $reason"
}

# --- fixtures (rebuilt every run) ---
redacted_dir="$work/redacted-evidence"
mkdir -p "$redacted_dir"
printf '{"accessToken":"[REDACTED]"}\n' > "$redacted_dir/ignored-login.json"

hit_dir="$work/hit-evidence"
mkdir -p "$hit_dir"
printf '{"accessToken":"%s"}\n' "$canary" > "$hit_dir/ignored-login.json"

corrupt_dir="$work/corrupt-evidence"
mkdir -p "$corrupt_dir"
printf 'this file is not a zip archive\n' > "$corrupt_dir/broken.zip"

zip_src="$work/zip-src"
mkdir -p "$zip_src"
printf '{"accessToken":"%s"}\n' "$canary" > "$zip_src/login.json"
printf '{"accessToken":"[REDACTED]"}\n' > "$zip_src/clean.json"

make_zip() {
  # make_zip TARGET MEMBER - build a valid zip at TARGET holding the single
  # file MEMBER (a plain name resolved inside $zip_src), then verify it.
  local target="$1" member="$2"
  (cd "$zip_src" && python3 -m zipfile -c "$target" "$member")
  python3 -m zipfile -t "$target" >/dev/null
}

# Restricted PATH dirs: symlinks to exactly the tools the scanners need.
restricted_bin="$work/restricted-bin"      # rg deliberately absent
no_unzip_bin="$work/no-unzip-bin"          # unzip deliberately absent
stub_bin="$work/stub-bin"                  # unzip provided by a python3 stub
mkdir -p "$restricted_bin" "$no_unzip_bin" "$stub_bin"
for tool in bash mktemp awk rm; do
  ln -s "$(command -v "$tool")" "$restricted_bin/$tool"
done
for tool in bash dirname mktemp awk rm find sed mkdir wc rg; do
  ln -s "$(command -v "$tool")" "$no_unzip_bin/$tool"
done
for tool in bash dirname mktemp awk rm find sed mkdir wc rg python3; do
  ln -s "$(command -v "$tool")" "$stub_bin/$tool"
done
cat > "$stub_bin/unzip" <<'EOF'
#!/usr/bin/env bash
# Test stub: maps the exact unzip call shape used by
# check-sensitive-evidence-deep.sh (`unzip -qq -o ARCHIVE -d DEST`) onto
# python3 -m zipfile. Not a general unzip replacement.
set -Eeuo pipefail
archive=""
dest=""
while (( $# > 0 )); do
  case "$1" in
    -*) ;;
    *) if [[ -z "$archive" ]]; then archive="$1"; else dest="$1"; fi ;;
  esac
  shift
done
if [[ -z "$archive" || -z "$dest" ]]; then
  echo 'stub unzip: expected `unzip -qq -o ARCHIVE -d DEST`' >&2
  exit 2
fi
exec python3 -m zipfile -e "$archive" "$dest"
EOF
chmod +x "$stub_bin/unzip"

# --- cases ---
run_case T0a "base scanner passes bash -n" 0 "" bash -n "$base_scanner"
run_case T0b "deep scanner passes bash -n" 0 "" bash -n "$deep_scanner"

run_case T1 "missing rg is a setup error, never a clean scan" 2 "" \
  env PATH="$restricted_bin" bash "$base_scanner" "$redacted_dir" "$secret_file"

if [[ "$(id -u)" == "0" ]]; then
  skip_case T2 "unreadable evidence directory fails the scan" "running as root; a chmod 000 directory would still be readable"
else
  unreadable_dir="$work/unreadable-evidence"
  mkdir -p "$unreadable_dir"
  chmod 000 "$unreadable_dir"
  run_case T2 "unreadable evidence directory fails the scan" 3 "rg failed while scanning" \
    bash "$base_scanner" "$unreadable_dir" "$secret_file"
  chmod 700 "$unreadable_dir"
fi

run_case T3 "corrupt zip fails the deep scan and is listed" 3 "$corrupt_dir/broken.zip" \
  bash "$deep_scanner" "$corrupt_dir" "$secret_file"

unzip_blocked_dir="$work/unzip-blocked-evidence"
mkdir -p "$unzip_blocked_dir"
make_zip "$unzip_blocked_dir/bundle.zip" clean.json
run_case T4 "unavailable unzip fails the deep scan and lists the archive" 3 "$unzip_blocked_dir/bundle.zip" \
  env PATH="$no_unzip_bin" bash "$deep_scanner" "$unzip_blocked_dir" "$secret_file"

run_case T5 "canary in loose evidence fails the base scan" 1 "sensitive value found in evidence" \
  bash "$base_scanner" "$hit_dir" "$secret_file"

zip_hit_dir="$work/zip-hit-evidence"
mkdir -p "$zip_hit_dir"
make_zip "$zip_hit_dir/login.zip" login.json
run_case T6 "canary inside a zip fails the deep scan (stub unzip)" 1 "$zip_hit_dir/login.zip" \
  env PATH="$stub_bin" bash "$deep_scanner" "$zip_hit_dir" "$secret_file"

run_case T7 "redacted evidence passes the base scan" 0 "" \
  bash "$base_scanner" "$redacted_dir" "$secret_file"

run_case T8 "redacted evidence passes the deep scan" 0 "deep scan clean" \
  bash "$deep_scanner" "$redacted_dir" "$secret_file"

run_case T9 "missing secret file is a setup error, not a clean pass" 2 "secret file is missing or unreadable" \
  bash "$base_scanner" "$redacted_dir" "$work/absent-secret.txt"

run_case T10 "missing evidence directory is a setup error" 2 "evidence directory is missing or not a directory" \
  bash "$base_scanner" "$work/absent-evidence-dir" "$secret_file"

# --- summary ---
echo
echo "cases: $((pass + fail + skip)) total | pass: $pass | fail: $fail | skip: $skip"
if (( fail > 0 )); then
  echo 'sensitive evidence scanner test FAILED' >&2
  exit 1
fi
echo 'sensitive evidence scanner test passed'
