// Recovery observation: fixed-rate synthetic probes for 300 seconds after the
// peak load stops (2026-09-22 reduced protocol window, was 10 minutes; at the
// unchanged 30/minute rate the window still yields >=100 valid samples).
// Recovery judgement: probe p95 back
// within the scenario threshold (this file's thresholds) AND CPU below 85%
// of quota (checked from the resource samples by check-saturation.mjs).
import { check } from 'k6'
import { api, req, thresholds } from '../lib/common.js'

export { handleSummary } from '../lib/common.js'

export const options = {
  scenarios: {
    probe: { executor: 'constant-arrival-rate', rate: 30, timeUnit: '1m', duration: '300s', preAllocatedVUs: 10, maxVUs: 40 },
  },
  thresholds: thresholds(['public_read']),
}

let cursor = 0
export default function () {
  cursor++
  req('GET', api('/titles?q=&limit=5'), 'public_read')
  if (cursor % 5 === 0) {
    req('GET', api('/titles?q=&limit=10'), 'public_read')
  }
}
