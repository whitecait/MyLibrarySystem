// Peak scenario: concurrency ramps to 50 and holds for a total of 10 minutes
// (MLS-NFR-PER-0001 peak concurrency 50, >=20 concurrent operations).
import { check, sleep } from 'k6'
import { api, req, thresholds } from '../lib/common.js'

export const options = {
  scenarios: {
    peak: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '2m', target: 50 }, // ramp to 50
        { duration: '8m', target: 50 }, // hold
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: thresholds(['public_read', 'normal_read']),
}

let cursor = 0
export default function () {
  cursor++
  const res = req('GET', api('/titles?q=&limit=10'), 'public_read')
  check(res, { 'peak search 2xx': (r) => r.status === 200 })
  const items = res.json()?.data?.items ?? []
  if (items.length > 0 && cursor % 4 === 0) {
    req('GET', api(`/titles/${items[cursor % items.length].titleId}`), 'public_read')
  }
  if (cursor % 20 === 0) {
    // occasional login keeps the normal_read class represented under peak
    req('POST', api('/auth/sessions'), 'normal_read', { username: `peak-${cursor}`, password: 'wrong-on-purpose' }, true)
  }
  sleep(1)
}
