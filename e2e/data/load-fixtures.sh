#!/usr/bin/env bash
# Load functional-batch fixtures into FRESH dedicated instances
# (acceptance plan 2026-09-19 §4.3). Renders deterministically, then applies.
#
#   bash e2e/data/load-fixtures.sh <compose-project> <out-dir> <business-date-D>
#
# at-0024.sql requires the business date D (AT-0002 §2.24 当日);
# at-0029-corpus.sql needs no date. Both refuse non-empty instances.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

project="${1:?usage: load-fixtures.sh <compose-project> <out-dir> <business-date-D>}"
dir="${2:?usage: load-fixtures.sh <compose-project> <out-dir> <business-date-D>}"
business_date="${3:?usage: load-fixtures.sh <compose-project> <out-dir> <business-date-D> (e.g. 2026-09-20)}"

node e2e/data/functional/render-fixtures.mjs --out "$dir" > /dev/null

compose() { docker compose -p "$project" "$@"; }
psql() { compose exec -T database psql -U library -d library -v ON_ERROR_STOP=1 "$@"; }

echo "loading at-0024 dataset (business date ${business_date})"
psql -v D="$business_date" -q -f - < "$dir/at-0024.sql"
echo "loading at-0029 annotation corpus"
psql -q -f - < "$dir/at-0029-corpus.sql"

echo "verify checksums against $dir/fixtures-manifest.json and record them in the evidence package"
