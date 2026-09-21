// Recovery observation: fixed-rate synthetic probes for 10 minutes after the
// peak load stops (>=100 valid samples). Recovery judgement: probe p95 back
// within the scenario threshold (this file's thresholds) AND CPU below 85%
// of quota (checked from the resource samples by check-saturation.mjs).
import { check } from 'k6'
import { api, req, thresholds } from '../lib/common.js'

export { handleSummary } from '../lib/common.js'

export const options = {
  scenarios: {
    probe: { executor: 'constant-arrival-rate', rate: 30, timeUnit: '1m', duration: '10m', preAllocatedVUs: 10, maxVUs: 40 },
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
