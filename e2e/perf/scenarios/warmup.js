// Warmup (5 minutes, excluded from all statistics per MLS-NFR-PER-0002):
// registers the probe reader accounts through the public path and drives
// light search traffic to warm caches.
import { check } from 'k6'
import { api, req, thresholds, readerName, probePassword, RUN_ID } from '../lib/common.js'

export const options = {
  scenarios: {
    warmup: { executor: 'constant-arrival-rate', rate: 60, timeUnit: '1m', duration: '5m', preAllocatedVUs: 20, maxVUs: 60 },
  },
  thresholds: thresholds(['public_read', 'normal_read']),
}

const READERS = Number(__ENV.PROBE_READERS || 10)
let provisioned = false

export default function () {
  req('GET', api(`/titles?q=${encodeURIComponent(RUN_ID)}&limit=5`), 'public_read')
  if (provisioned) return
  // Probe accounts come from the public registration path (dataset accounts
  // are not for login — see generate-dataset.mjs).
  for (let i = 1; i <= READERS; i++) {
    const res = req('POST', api('/auth/registrations'), 'normal_read', { username: readerName(i), password: probePassword, displayName: readerName(i) }, true)
    check(res, { 'registration accepted or already registered': (r) => r.status < 300 || r.status === 409 || r.status === 422 })
  }
  provisioned = true
}
