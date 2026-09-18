#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
node --version
npm --version
java -version
docker info >/dev/null
(
  cd library-frontend
  npm ci
  npm run lint:check
  npm run test:ci
  npm run build
)
(
  cd library-server
  ./mvnw -B -ntp clean verify
)
bash scripts/verify-compose.sh
bash scripts/verify-integration.sh
