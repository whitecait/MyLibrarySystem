#!/usr/bin/env bash
# Load the generated performance dataset into a FRESH dedicated database
# instance (acceptance plan 2026-09-19 §4.3).
#
#   bash e2e/data/load-dataset.sh <compose-project> <dataset-dir>
#
# The loader refuses non-empty business tables (dataset instances start
# empty), applies dataset.sql in one transaction, resyncs the identity
# sequences (COPY rows map to file-order ids), and VACUUM ANALYZEs.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

project="${1:?usage: load-dataset.sh <compose-project> <dataset-dir>}"
dir="${2:?usage: load-dataset.sh <compose-project> <dataset-dir>}"
sql="$dir/dataset.sql"
test -f "$sql" || { echo "missing $sql (run: node e2e/data/generate-dataset.mjs --out $dir)" >&2; exit 2; }

compose() { docker compose -p "$project" "$@"; }
psql() { compose exec -T database psql -U library -d library -v ON_ERROR_STOP=1 "$@"; }

for table in account book_title book_copy borrow_transaction fine; do
  count="$(psql -tA -c "SELECT count(*) FROM ${table};")"
  if [[ "$count" != "0" ]]; then
    echo "table ${table} is not empty (${count} rows); refusing to load into a non-fresh instance" >&2
    exit 1
  fi
done

echo "applying $(grep -c '^' "$sql") lines from dataset.sql"
psql -q < "$sql"

for table in account book_title book_copy borrow_transaction fine; do
  psql -q -c "SELECT setval(pg_get_serial_sequence('${table}','id'), (SELECT max(id) FROM ${table}));" > /dev/null
done
for table in account book_title book_copy borrow_transaction fine; do
  psql -q -c "VACUUM ANALYZE ${table};" > /dev/null
done

echo "loaded: $(psql -tA -c 'SELECT count(*) FROM borrow_transaction;') transactions, $(psql -tA -c 'SELECT count(*) FROM book_title;') titles"
echo "verify checksums against manifest.json before registering the dataset version"
