// Peak scenario: concurrency ramps to 50 over 2 minutes and then holds at 50
// for 10 minutes (MLS-NFR-PER-0001 peak concurrency 50, >=20 concurrent
// operations; 12 minutes wall clock in total).
import { check, sleep } from 'k6'
import { api, req, thresholds } from '../lib/common.js'

export { handleSummary } from '../lib/common.js'

export const options = {
  scenarios: {
    peak: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '2m', target: 50 }, // ramp to 50
        { duration: '10m', target: 50 }, // hold at peak for the protocol window
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
    // occasional failed login keeps the normal_read class represented under
    // peak; the credential rejection is declared expected as the contract's
    // concrete path: 401 / MLS-ID-004 (登录凭据失败)
    req('POST', api('/auth/sessions'), 'normal_read', { username: `peak-${cursor}`, password: 'wrong-on-purpose' },
      [{ status: 401, reason: 'MLS-ID-004' }])
  }
  sleep(1)
}
