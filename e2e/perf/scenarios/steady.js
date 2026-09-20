// Steady mixed load: 10 minutes, >=100 requests/min with >=20% writes
// (MLS-NFR-PER-0001 capacity baseline). Reads are public search/detail;
// writes are reader reservation create+cancel pairs through the circulation
// class. Same-query conflicts (e.g. all-copies-reserved, lost races) are
// controlled business rejections and tagged expected.
import { check } from 'k6'
import { api, req, login, auth, thresholds, readerName, probePassword } from '../lib/common.js'

export const options = {
  scenarios: {
    reads: { executor: 'constant-arrival-rate', rate: 96, timeUnit: '1m', duration: '10m', preAllocatedVUs: 30, maxVUs: 120, exec: 'reads' },
    writes: { executor: 'constant-arrival-rate', rate: 24, timeUnit: '1m', duration: '10m', preAllocatedVUs: 20, maxVUs: 60, exec: 'writes', startTime: '5s' },
  },
  thresholds: thresholds(['public_read', 'normal_read', 'circulation']),
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
  return { tokens: tokensLocal }
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
