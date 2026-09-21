#!/usr/bin/env bash
# Load functional-batch fixtures into a FRESH dedicated instance
# (acceptance plan 2026-09-19 §4.3). Exactly ONE scenario per invocation:
# at-0024.sql and at-0029-corpus.sql each refuse non-empty instances
# (empty-DB guard in both files), so they must target separate compose
# projects with independent volumes — loading both into the same database
# makes the second apply fail on its non-fresh guard.
#
#   bash e2e/data/load-fixtures.sh --scenario at0024|at0029 \
#        --project <compose-project> [--out-dir <dir>] [--business-date D]
#
# at-0024.sql requires the business date D (AT-0002 §2.24 当日);
# at-0029-corpus.sql needs no date and rejects one. Before touching the
# database the loader verifies the rendered files against the
# fixtures-manifest.json SHA-256 checksums (mismatch exits non-zero) and
# refuses non-empty business tables.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

usage() {
  echo "usage: load-fixtures.sh --scenario at0024|at0029 --project <compose-project>" \
    "[--out-dir <dir>] [--business-date <D> (at0024 only, e.g. 2026-09-20)]" >&2
}

scenario="" project="" out_dir="e2e/data/out" business_date=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scenario) scenario="${2:?--scenario requires a value}"; shift 2 ;;
    --project) project="${2:?--project requires a value}"; shift 2 ;;
    --out-dir) out_dir="${2:?--out-dir requires a value}"; shift 2 ;;
    --business-date) business_date="${2:?--business-date requires a value}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

case "$scenario" in
  at0024) sql="$out_dir/at-0024.sql" ;;
  at0029) sql="$out_dir/at-0029-corpus.sql" ;;
  "") echo "--scenario is required" >&2; usage; exit 2 ;;
  *) echo "--scenario must be at0024 or at0029 (got: $scenario)" >&2; exit 2 ;;
esac
[[ -n "$project" ]] || { echo "--project is required" >&2; usage; exit 2; }
case "$scenario:$business_date" in
  at0024:) echo "--scenario at0024 requires --business-date D (AT-0002 §2.24 当日)" >&2; exit 2 ;;
  at0029:?*) echo "--scenario at0029 takes no --business-date" >&2; exit 2 ;;
esac
if [[ "$scenario" == at0024 && ! "$business_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "--business-date must be YYYY-MM-DD (got: $business_date)" >&2
  exit 2
fi

node e2e/data/functional/render-fixtures.mjs --out "$out_dir" > /dev/null

manifest="$out_dir/fixtures-manifest.json"
[[ -f "$manifest" ]] || { echo "missing $manifest (renderer emitted no manifest)" >&2; exit 1; }
node -e '
  const { createHash } = require("node:crypto");
  const { readFileSync } = require("node:fs");
  const path = require("node:path");
  const dir = process.argv[1];
  const manifest = JSON.parse(readFileSync(path.join(dir, "fixtures-manifest.json"), "utf8"));
  let mismatches = 0;
  for (const [name, expected] of Object.entries(manifest.files)) {
    const actual = createHash("sha256").update(readFileSync(path.join(dir, name))).digest("hex");
    if (actual === expected) {
      console.log(`checksum ok: ${name} ${actual}`);
    } else {
      console.error(`checksum mismatch: ${name} expected ${expected} got ${actual}`);
      mismatches += 1;
    }
  }
  process.exit(mismatches > 0 ? 1 : 0);
' "$out_dir"

compose() { docker compose -p "$project" "$@"; }
psql() { compose exec -T database psql -U library -d library -v ON_ERROR_STOP=1 "$@"; }

for table in account book_title book_copy borrow_transaction fine; do
  count="$(psql -tA -c "SELECT count(*) FROM ${table};")"
  if [[ "$count" != "0" ]]; then
    echo "table ${table} is not empty (${count} rows); fixtures require a FRESH dedicated" >&2
    echo "instance — use a separate compose project with its own volume, not an already-loaded one" >&2
    exit 1
  fi
done

case "$scenario" in
  at0024)
    echo "loading at-0024 dataset (business date ${business_date})"
    psql -v D="$business_date" -q -f - < "$sql"
    ;;
  at0029)
    echo "loading at-0029 annotation corpus"
    psql -q -f - < "$sql"
    ;;
esac

echo "loaded ${scenario} fixtures from ${sql} (manifest checksums verified, instance was empty)"
echo "record $manifest checksums in the evidence package"
