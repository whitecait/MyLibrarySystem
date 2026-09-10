#!/usr/bin/env bash
# Regression: PostgreSQL's init server accepts Unix sockets before TCP is ready.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
name="mls-readiness-$(date -u +%Y%m%d%H%M%S)-$$"
cleanup() {
  result=$?
  trap - EXIT
  docker rm -f "$name" >/dev/null || result=1
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
config="$(DB_PASSWORD=readiness-probe-only docker compose --env-file /dev/null -f compose.yaml config --format json)"
image="$(printf '%s' "$config" | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["database"]["image"])')"
probe="$(printf '%s' "$config" | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["database"]["healthcheck"]["test"][1])')"
docker run -d --name "$name" --network none --tmpfs /var/lib/postgresql/data \
  -e POSTGRES_USER=library -e POSTGRES_DB=library -e POSTGRES_HOST_AUTH_METHOD=trust \
  "$image" postgres -c listen_addresses= >/dev/null
socket_ready=false
for attempt in {1..60}; do
  if docker exec "$name" pg_isready -U library -d library >/dev/null 2>&1; then
    socket_ready=true
    break
  fi
  sleep 0.5
done
test "$socket_ready" = true
if docker exec "$name" sh -c "$probe"; then
  echo 'FAIL: configured health check accepted a socket-only PostgreSQL server' >&2
  exit 1
fi
echo 'PASS: configured health check rejects socket-only PostgreSQL initialization'
