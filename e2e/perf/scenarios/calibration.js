// Wiring calibration, 60 seconds — NOT one of the three formal protocol
// runs. Proves the harness end to end before a real round: k6 service
// discovery and mounts (pinned overlay set), scenario env passthrough
// (BASE_URL/RUN_ID), the Idempotency-Key injection on mutating requests
// (login is a guaranteed 400 without it) and one authenticated read.
//   env CALIB_USER=<user> CALIB_PASSWORD=<pass> \
//     docker compose -p <project> -f compose.yaml -f compose.observability.yaml \
//     -f compose.perf-quota.yaml --profile perf run --rm \
//     k6 run --summary-export /scripts/out/calibration-summary.json \
//     /scripts/scenarios/calibration.js
import { check } from 'k6'
import { api, req, login, auth, thresholds } from '../lib/common.js'

export const options = {
  scenarios: {
    calib: { executor: 'constant-vus', vus: 2, duration: '60s' },
  },
  thresholds: { ...thresholds(['public_read']), checks: ['rate>0.99'] },
}

const USER = __ENV.CALIB_USER || 'e2e-librarian'
const PASS = __ENV.CALIB_PASSWORD || 'E2e-Librarian-2026!'

export default function () {
  const res = req('GET', api('/titles?search=&limit=5'), 'public_read')
  check(res, { 'search 2xx': (r) => r.status === 200 })
  const token = login(USER, PASS)
  check(token, { 'login succeeded (idempotency key present)': (t) => t !== null })
  if (token) {
    // role-matched authenticated read: the default account is a librarian,
    // and /me/business is reader-scoped — audit listing is the librarian's
    // own class and also calibrates the audit metric tags.
    const audit = req('GET', api('/audit-events?limit=5'), 'audit', null, false, auth(token))
    check(audit, { 'authenticated audit read 2xx': (r) => r.status === 200 })
  }
}
