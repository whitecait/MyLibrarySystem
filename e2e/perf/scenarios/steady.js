// Steady mixed load: 10 minutes, >=100 requests/min with >=20% writes
// (MLS-NFR-PER-0001 capacity baseline). Reads are public search/detail;
// writes are reader reservation create+cancel pairs through the circulation
// class. Covers all five MLS-NFR-PER-0001 classes: an audit-query stream
// (authenticated LIBRARIAN) and a low-rate end-to-end payment chain
// (self borrow -> damaged librarian return -> fine -> order -> simulated
// payment) on top of the read/circulation mix. Same-query conflicts (e.g.
// all-copies-reserved, lost races) are controlled business rejections,
// declared per call site as expected 4xx with the contract's concrete HTTP
// status and stable business code (see the `expected` arguments below).
//
// Traffic follows the hot-set distribution (RA-0013 §11 data distribution
// [D]): 80% of search/detail/write/payment targets are drawn from the top
// 10% of titles via queries-hot.json, 20% from the long tail via
// queries-tail.json (both produced by the dataset generator). Generator
// seed-20260910-v3 keeps at least one lendable (SHELF_READY) copy on every
// hot title, so hot-weighted picks can actually proceed instead of being
// forced to the tail.
import { check } from 'k6'
import { api, req, login, auth, thresholds, readerName, probePassword, librarianName, librarianPassword } from '../lib/common.js'

export { handleSummary } from '../lib/common.js'

export const options = {
  scenarios: {
    reads: { executor: 'constant-arrival-rate', rate: 96, timeUnit: '1m', duration: '10m', preAllocatedVUs: 30, maxVUs: 120, exec: 'reads' },
    writes: { executor: 'constant-arrival-rate', rate: 24, timeUnit: '1m', duration: '10m', preAllocatedVUs: 20, maxVUs: 60, exec: 'writes', startTime: '5s' },
    audit: { executor: 'constant-arrival-rate', rate: 6, timeUnit: '1m', duration: '10m', preAllocatedVUs: 2, maxVUs: 8, exec: 'audit', startTime: '10s' },
    payment: { executor: 'constant-arrival-rate', rate: 2, timeUnit: '1m', duration: '10m', preAllocatedVUs: 4, maxVUs: 12, exec: 'paymentChain', startTime: '15s' },
  },
  thresholds: thresholds(['public_read', 'normal_read', 'circulation', 'payment', 'audit']),
}

const READERS = Number(__ENV.PROBE_READERS || 10)
const probePasswordLocal = probePassword
// Query pools produced by the dataset generator. Loaded via the file-reading
// global with the absolute in-container path (the pinned k6 image does not
// export open from 'k6'; the k6 service mounts ./e2e/perf at /scripts,
// compose.perf-quota.yaml).
const hotQueries = JSON.parse(open('/scripts/out/queries-hot.json'))
const tailQueries = JSON.parse(open('/scripts/out/queries-tail.json'))
// Traffic-weighted pool pick: 80% hot set / 20% long tail.
function pickPool() {
  return Math.random() < 0.8 ? hotQueries : tailQueries
}
let tokens = []

export function setup() {
  const tokensLocal = []
  for (let i = 1; i <= READERS; i++) {
    const token = login(readerName(i), probePasswordLocal)
    if (token) tokensLocal.push(token)
  }
  if (tokensLocal.length === 0) throw new Error('no probe account could log in — warmup did not run against this instance?')
  const librarianToken = login(librarianName, librarianPassword)
  if (!librarianToken) throw new Error('perf-probe-librarian could not log in — run the updated e2e/data/load-dataset.sh (staff injection) against this instance')
  return { tokens: tokensLocal, librarianToken }
}

let queryCursor = 0
export function reads(data) {
  const token = data.tokens[queryCursor % data.tokens.length]
  // Search target follows the 80/20 hot/tail weighting; the detail request
  // shares the weighting by drawing on the hits of the current round.
  const pool = pickPool()
  const res = req('GET', api(`/titles?q=${encodeURIComponent(pool[queryCursor % pool.length].q)}&limit=10`), 'public_read')
  check(res, { 'search 2xx': (r) => r.status === 200 })
  const items = res.json()?.data?.items ?? []
  if (items.length > 0 && queryCursor % 3 === 0) {
    const titleId = items[queryCursor % items.length]?.titleId
    if (titleId) req('GET', api(`/titles/${titleId}`), 'public_read')
  }
  // keep some authenticated reads in the mix (normal_read class)
  req('GET', api('/me/business?kind=LOAN&limit=5'), 'normal_read', null, false, auth(token))
  queryCursor++
}


let writeCursor = 0
export function writes(data) {
  const token = data.tokens[writeCursor % data.tokens.length]
  writeCursor++
  // Target selection follows the 80/20 hot/tail weighting: the pool is
  // picked weighted and the retries stay inside the same pool, so a hot
  // iteration borrows a hot title and a tail iteration a tail title. With
  // generator seed-20260910-v3 hot titles keep a lendable copy; when the
  // drawn queries still yield no AVAILABLE title the iteration is a
  // controlled no-op. The search itself is a public_read request; the
  // create+cancel reservation pair below is two circulation writes
  // (same-reader open-transaction conflicts are declared expected:
  // 409 / MLS-CIRC-003).
  const pool = pickPool()
  let borrowable = null
  for (let attempt = 0; attempt < 3 && !borrowable; attempt++) {
    const q = pool[(writeCursor * 7 + attempt) % pool.length]
    const res = req('GET', api(`/titles?q=${encodeURIComponent(q.q)}&limit=10`), 'public_read')
    borrowable = res.json()?.data?.items?.find((t) => t.availability === 'AVAILABLE') ?? null
  }
  if (!borrowable) return
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
  const create = req('POST', api('/reservations'), 'circulation',
    { titleId: String(borrowable.titleId), reservationDate: tomorrow, pickupSlot: 'PM', channel: 'ONLINE' },
    [{ status: 409, reason: 'MLS-CIRC-003' }], auth(token))
  const txId = create.json()?.data?.txId
  if (txId) {
    req('POST', api(`/reservations/${txId}/cancel`), 'circulation', { reason: 'perf-load-release' },
      [{ status: 409, reason: 'MLS-CIRC-003' }], auth(token))
  }
}

export function audit(data) {
  // MLS-NFR-PER-0001 audit class: audit queries through the authenticated
  // librarian (events accumulate from the load itself). Both calls are the
  // documented list shape; no unverified filter values.
  req('GET', api('/audit-events?limit=20'), 'audit', null, false, auth(data.librarianToken))
  req('GET', api('/audit-events?limit=50'), 'audit', null, false, auth(data.librarianToken))
}

let payCursor = 0
export function paymentChain(data) {
  // End-to-end payment class at low rate (2/min): a reader borrows a
  // shelf-ready copy self-service, the librarian returns it DAMAGED (fine),
  // the reader opens an order on the fine and a SUCCESS simulated payment is
  // scheduled. Target selection follows the 80/20 hot/tail weighting with
  // same-pool retries (as writes). Lost races (copy taken between list and
  // borrow) are expected. Copy listing is a LIBRARIAN capability, so the
  // staff token performs the lookup (and the simulator call); business
  // writes stay with the reader.
  const token = data.tokens[payCursor % data.tokens.length]
  payCursor++
  const pool = pickPool()
  const candidates = []
  for (let attempt = 0; attempt < 3 && candidates.length === 0; attempt++) {
    const q = pool[(payCursor * 11 + attempt) % pool.length]
    const res = req('GET', api(`/titles?q=${encodeURIComponent(q.q)}&limit=10`), 'public_read')
    for (const t of res.json()?.data?.items ?? []) {
      if (t.availability === 'AVAILABLE') candidates.push(t)
    }
  }
  let copy = null
  let titleId = null
  for (const t of candidates.slice(0, 3)) {
    const copies = req('GET', api(`/copies?titleId=${t.titleId}&status=AVAILABLE&limit=3`), 'normal_read', null, false, auth(data.librarianToken))
      .json()?.data?.items ?? []
    if (copies.length > 0) { copy = copies[0]; titleId = t.titleId; break }
  }
  if (!copy) return
  // Lost copy races are declared expected as the contract's conflict paths:
  // copy-state conflict 409 / MLS-CIRC-003, reader-eligibility 422 /
  // MLS-CIRC-011. Any other 4xx lands in unexpected_4xx.
  const loan = req('POST', api('/walk-in-loans'), 'circulation',
    { items: [{ titleId: String(titleId), copyId: String(copy.copyId) }], channel: 'SELF_SERVICE' },
    [{ status: 409, reason: 'MLS-CIRC-003' }, { status: 422, reason: 'MLS-CIRC-011' }], auth(token))
  const lent = (loan.json()?.data?.items ?? []).some((i) => i.outcome === 'LENT')
  if (!lent) return
  // double-return / not-on-loan races: 409 / MLS-CIRC-006
  req('POST', api('/returns'), 'circulation', { copyId: String(copy.copyId), condition: 'DAMAGED' },
    [{ status: 409, reason: 'MLS-CIRC-006' }], auth(data.librarianToken))
  const fines = req('GET', api('/fines?status=UNPAID&limit=5'), 'circulation', null, false, auth(token))
    .json()?.data?.items ?? []
  const fine = fines[0]
  if (!fine) return
  // concurrent order on the same fine: 409 / MLS-PAY-001
  const order = req('POST', api(`/fines/${fine.fineId}/orders`), 'payment', {},
    [{ status: 409, reason: 'MLS-PAY-001' }], auth(token))
  const orderId = order.json()?.data?.orderId
  if (!orderId) return
  // conflicting callback events: 409 / MLS-PAY-003
  req('POST', api('/payment-simulator/results'), 'payment',
    { orderId: String(orderId), result: 'SUCCESS', delayMs: 0, duplicateCount: 0 },
    [{ status: 409, reason: 'MLS-PAY-003' }], auth(data.librarianToken))
}
