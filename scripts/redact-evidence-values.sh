#!/usr/bin/env bash
# Source-level redaction of an evidence tree against a value set (D7 of the W2
# M6 fix batch).
#
#   bash scripts/redact-evidence-values.sh --stdin SECRET_VALUE_FILE < in > out
#   bash scripts/redact-evidence-values.sh SECRET_VALUE_FILE PATH...
#
# The value set is the file produced by scripts/collect-evidence-secret-values.sh
# (one credential value per line). Every value is replaced by the literal
# [REDACTED-SECRET] wherever it occurs, so an output that renders environment
# values (docker inspect "Env", `docker compose config` result) can be written
# into an evidence tree without carrying the credential.
#
# Contract:
#   - replacement is literal byte-level and values are applied longest-first, so
#     a value that is a prefix of another value cannot leave a residue behind;
#     the operation is idempotent (an already redacted file does not change);
#   - no value is ever printed: per-file output carries the path and the number
#     of replacements only;
#   - the value set file is read, never written; callers keep it in tmpfs and
#     delete it after use (see scripts/collect-evidence-secret-values.sh);
#   - over-redaction is preferred to a leaked value: a short value is replaced
#     exactly like a long one.
#
# Exit codes:
#   0  every requested path was processed (a file in which nothing was replaced
#      is not an error)
#   2  usage or setup error — no secret-value file, unreadable secret-value
#      file, python3 missing, no path given in path mode; nothing was written
#   3  a path could not be read or written; the run aborted at that path (paths
#      listed on stderr), earlier paths may already be redacted
set -Eeuo pipefail

usage() {
  echo "usage: bash scripts/redact-evidence-values.sh --stdin SECRET_VALUE_FILE" >&2
  echo "       bash scripts/redact-evidence-values.sh SECRET_VALUE_FILE PATH..." >&2
}

mode="paths"
if [[ "${1:-}" == "--stdin" ]]; then
  mode="stdin"
  shift
fi
if (( $# < 1 )); then
  usage
  exit 2
fi

secret_file="$1"
shift
if ! test -r "$secret_file"; then
  echo "secret-value file is missing or unreadable: $secret_file" >&2
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo 'python3 is not available; redaction cannot run' >&2
  exit 2
fi

if [[ "$mode" == "stdin" ]]; then
  if (( $# > 0 )); then
    echo "--stdin takes no path arguments (got: $*)" >&2
    exit 2
  fi
  exec python3 -c '
import sys
secret_file = sys.argv[1]
with open(secret_file, "rb") as fh:
    values = [line for line in fh.read().split(b"\n") if line]
values.sort(key=len, reverse=True)
data = sys.stdin.buffer.read()
for value in values:
    data = data.replace(value, b"[REDACTED-SECRET]")
sys.stdout.buffer.write(data)
' "$secret_file"
fi

if (( $# < 1 )); then
  usage
  exit 2
fi

workroot="$(mktemp -d "${TMPDIR:-/tmp}/redact-evidence-values.XXXXXX")"
trap 'rm -rf -- "$workroot"' EXIT
engine="$workroot/redact.py"
cat > "$engine" <<'PY'
import pathlib, sys

secret_file = sys.argv[1]
values = [line for line in pathlib.Path(secret_file).read_bytes().split(b"\n") if line]
# Longest first: a value that is a prefix of another value must not be replaced
# before the longer one, or the longer one would leave a residue behind.
values.sort(key=len, reverse=True)

if not values:
    print("redact: the secret-value set is empty; nothing can be matched", file=sys.stderr)

stats = {"files": 0, "changed": 0, "replacements": 0}
failed = []


def process(path):
    stats["files"] += 1
    try:
        original = path.read_bytes()
    except OSError as exc:
        failed.append(f"{path}: unreadable ({exc.strerror})")
        return
    data = original
    replacements = 0
    for value in values:
        hits = data.count(value)
        if hits:
            replacements += hits
            data = data.replace(value, b"[REDACTED-SECRET]")
    if replacements == 0:
        return
    try:
        path.write_bytes(data)
    except OSError as exc:
        failed.append(f"{path}: not writable ({exc.strerror})")
        return
    stats["changed"] += 1
    stats["replacements"] += replacements
    print(f"redacted: {path} replacements={replacements}")


for arg in sys.argv[2:]:
    p = pathlib.Path(arg)
    if p.is_dir():
        for f in sorted(x for x in p.rglob("*") if x.is_file()):
            process(f)
    elif p.exists():
        process(p)
    else:
        failed.append(f"{arg}: no such file or directory")

for line in failed:
    print(f"redact: FAILED {line}", file=sys.stderr)
print("redact: files={files} changed={changed} replacements={replacements} values={values}".format(
    files=stats["files"], changed=stats["changed"],
    replacements=stats["replacements"], values=len(values)))
sys.exit(3 if failed else 0)
PY

python3 "$engine" "$secret_file" "$@"
