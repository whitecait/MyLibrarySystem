// Warmup (60 seconds — the 2026-09-22 reduced protocol window; excluded from
// all statistics per MLS-NFR-PER-0002): registers the probe reader accounts
// through the public path and drives light search traffic to warm caches.
// Account provisioning happens once in setup (not spread over the
// window), so shortening the warmup does not change what steady.js can log
// in with.
import { check } from 'k6'
import http from 'k6/http'
import { api, req, thresholds, readerName, probePassword, RUN_ID } from '../lib/common.js'

export { handleSummary } from '../lib/common.js'

export const options = {
  scenarios: {
    warmup: { executor: 'constant-arrival-rate', rate: 60, timeUnit: '1m', duration: '60s', preAllocatedVUs: 20, maxVUs: 60 },
  },
  thresholds: thresholds(['public_read', 'normal_read']),
}

const READERS = Number(__ENV.PROBE_READERS || 10)
// Each synthetic reader represents a separate registration client session.
// Provision once in setup, rather than ten attempts in every VU's first iteration.
export function setup() {
  for (let i = 1; i <= READERS; i++) {
    const res = req('POST', api('/auth/registrations'), 'normal_read',
      { username: readerName(i), password: probePassword, displayName: readerName(i) },
      [{ status: 409, reason: 'MLS-ID-002' }], {}, new http.CookieJar())
    const accepted = res.status < 300 || res.status === 409
    check(res, { 'registration accepted or already registered': () => accepted })
    if (!accepted) throw new Error(`probe registration failed: HTTP ${res.status}`)
  }
  return {}
}

export default function () {
  req('GET', api(`/titles?q=${encodeURIComponent(RUN_ID)}&limit=5`), 'public_read')
}
