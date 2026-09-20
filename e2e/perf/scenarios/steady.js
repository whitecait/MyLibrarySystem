// Steady mixed load: 10 minutes, >=100 requests/min with >=20% writes
// (MLS-NFR-PER-0001 capacity baseline). Reads are public search/detail;
// writes are reader reservation create+cancel pairs through the circulation
// class. Covers all five MLS-NFR-PER-0001 classes: an audit-query stream
// (authenticated LIBRARIAN) and a low-rate end-to-end payment chain
// (self borrow -> damaged librarian return -> fine -> order -> simulated
// payment) on top of the read/circulation mix. Same-query conflicts (e.g.
// all-copies-reserved, lost races) are controlled business rejections and
// tagged expected.
import { check } from 'k6'
import { api, req, login, auth, thresholds, readerName, probePassword, librarianName, librarianPassword } from '../lib/common.js'

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
  const res = req('GET', api('/titles?search=&limit=10'), 'public_read')
  check(res, { 'search 2xx': (r) => r.status === 200 })
  const items = res.json()?.data?.items ?? []
  if (items.length > 0 && queryCursor % 3 === 0) {
    const titleId = items[queryCursor % items.length]?.id
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
  // find a borrowable title from public availability, then create+cancel a
  // reservation (two circulation writes; conflicts are expected rejections)
  const res = req('GET', api('/titles?search=&limit=10'), 'public_read')
  const items = res.json()?.data?.items ?? []
  const borrowable = items.find((t) => t.availability === 'AVAILABLE')
  if (!borrowable) return
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
  const create = req('POST', api('/reservations'), 'circulation', { titleId: borrowable.id, reservationDate: tomorrow, pickupSlot: '10:00-10:30' }, true, auth(token))
  const txId = create.json()?.data?.txId ?? create.json()?.data?.id
  if (txId) {
    req('POST', api(`/reservations/${txId}/cancel`), 'circulation', {}, true, auth(token))
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
  // the reader opens an order on the fine and schedules a SUCCESS simulated
  // payment. Lost races (copy taken between list and borrow) are expected.
  const token = data.tokens[payCursor % data.tokens.length]
  payCursor++
  const res = req('GET', api('/titles?search=&limit=10'), 'public_read')
  const items = res.json()?.data?.items ?? []
  const title = items.find((t) => t.availability === 'AVAILABLE')
  if (!title) return
  const copies = req('GET', api(`/copies?titleId=${title.id}&status=SHELF_READY&limit=5`), 'normal_read', null, false, auth(token))
    .json()?.data?.items ?? []
  const copy = copies[0]
  if (!copy) return
  const loan = req('POST', api('/walk-in-loans'), 'circulation', { items: [{ titleId: String(title.id), copyId: String(copy.copyId) }], channel: 'SELF_SERVICE' }, true, auth(token))
  const lent = (loan.json()?.data?.items ?? []).some((i) => i.outcome === 'LENT')
  if (!lent) return
  req('POST', api('/returns'), 'circulation', { copyId: String(copy.copyId), condition: 'DAMAGED' }, true, auth(data.librarianToken))
  const fines = req('GET', api('/fines?status=UNPAID&limit=5'), 'circulation', null, false, auth(token))
    .json()?.data?.items ?? []
  const fine = fines[0]
  if (!fine) return
  const order = req('POST', api(`/fines/${fine.fineId}/orders`), 'payment', {}, true, auth(token))
  const orderId = order.json()?.data?.orderId
  if (!orderId) return
  req('POST', api('/payment-simulator/results'), 'payment', { orderId: String(orderId), result: 'SUCCESS', delayMs: 0, duplicateCount: 0 }, true, auth(token))
}
