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

compose() {
  # Same pinned overlay set + optional ENV_FILE as run-protocol.sh: exec
  # needs the base file's required vars (DB_PASSWORD...) to pass validation.
  docker compose -p "$project" \
    -f compose.yaml -f compose.observability.yaml -f compose.perf-quota.yaml \
    ${ENV_FILE:+--env-file "$ENV_FILE"} "$@"
}
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

# Environment-only probe staff (NOT part of the versioned dataset manifest):
# the steady mixed load (e2e/perf/scenarios/steady.js) needs an authenticated
# LIBRARIAN for audit queries and the damaged-return leg of the payment
# chain, and a separate ADMIN for the simulated-payment scheduling call
# (DEF-M6-008: CONTROLLED_TEST_OPERATOR maps to ADMIN, so ordinary traffic
# keeps the LIBRARIAN session). Public registration only creates READERs, so
# the accounts are injected here with PBKDF2 hashes in the backend
# PasswordHasher format
# (pbkdf2$<iter>$<saltHex>$<hashHex>; same 210k iterations as the e2e
# fixtures). Passwords: Perf-Librarian-2026! / Perf-Admin-2026! (documented in
# e2e/perf/lib/common.js, never real credentials).
lib_hash="$(node -e 'const{pbkdf2Sync}=require("node:crypto");const salt=Buffer.from("9f8e7d6c5b4a3928170f1e2d3c4b5a69","hex");process.stdout.write("pbkdf2$210000$"+salt.toString("hex")+"$"+pbkdf2Sync("Perf-Librarian-2026!",salt,210000,32,"sha256").toString("hex"))')"
psql -q -c "INSERT INTO account (username, password_hash, display_name, role, status, version, created_at, updated_at)
  SELECT 'perf-probe-librarian', '${lib_hash}', '性能探针馆员', 'LIBRARIAN', 'ACTIVE', 0, now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM account WHERE username = 'perf-probe-librarian');"
admin_hash="$(node -e 'const{pbkdf2Sync}=require("node:crypto");const salt=Buffer.from("2b7c9d4e6f8a1b3c5d7e9f0a1b2c3d4e","hex");process.stdout.write("pbkdf2$210000$"+salt.toString("hex")+"$"+pbkdf2Sync("Perf-Admin-2026!",salt,210000,32,"sha256").toString("hex"))')"
psql -q -c "INSERT INTO account (username, password_hash, display_name, role, status, version, created_at, updated_at)
  SELECT 'perf-probe-admin', '${admin_hash}', '性能探针管理员', 'ADMIN', 'ACTIVE', 0, now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM account WHERE username = 'perf-probe-admin');"
psql -q -c "SELECT setval(pg_get_serial_sequence('account','id'), (SELECT max(id) FROM account));" > /dev/null

echo "loaded: $(psql -tA -c 'SELECT count(*) FROM borrow_transaction;') transactions, $(psql -tA -c 'SELECT count(*) FROM book_title;') titles"
echo "verify checksums against manifest.json before registering the dataset version"
