// Warmup (60 seconds — the 2026-09-22 reduced protocol window; excluded from
// all statistics per MLS-NFR-PER-0002): registers the probe reader accounts
// through the public path and drives light search traffic to warm caches.
// Account provisioning happens on the first iteration (not spread over the
// window), so shortening the warmup does not change what steady.js can log
// in with.
import { check } from 'k6'
import { api, req, thresholds, readerName, probePassword, RUN_ID } from '../lib/common.js'

export { handleSummary } from '../lib/common.js'

export const options = {
  scenarios: {
    warmup: { executor: 'constant-arrival-rate', rate: 60, timeUnit: '1m', duration: '60s', preAllocatedVUs: 20, maxVUs: 60 },
  },
  thresholds: thresholds(['public_read', 'normal_read']),
}

const READERS = Number(__ENV.PROBE_READERS || 10)
let provisioned = false

export default function () {
  req('GET', api(`/titles?q=${encodeURIComponent(RUN_ID)}&limit=5`), 'public_read')
  if (provisioned) return
  // Probe accounts come from the public registration path (dataset accounts
  // are not for login — see generate-dataset.mjs). Re-registering an existing
  // probe account is declared expected as the contract's duplicate path:
  // 409 / MLS-ID-002 (用户名重名); field errors are 400 / MLS-ID-003 and
  // would land in unexpected_4xx.
  for (let i = 1; i <= READERS; i++) {
    const res = req('POST', api('/auth/registrations'), 'normal_read', { username: readerName(i), password: probePassword, displayName: readerName(i) },
      [{ status: 409, reason: 'MLS-ID-002' }])
    check(res, { 'registration accepted or already registered': (r) => r.status < 300 || r.status === 409 })
  }
  provisioned = true
}
