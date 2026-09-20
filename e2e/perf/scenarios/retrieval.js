// Retrieval-specific sub-scenario (3 minutes, >=100 valid samples).
// RETRIEVAL_SET=hot|tail selects the popular or long-tail query set produced
// by the dataset generator; the cold/hot cache pairing is orchestrated by
// run-protocol.sh (cold = database container restarted immediately before).
import { open } from 'k6'
import { check } from 'k6'
import { api, req, thresholds } from '../lib/common.js'

const SET = __ENV.RETRIEVAL_SET || 'hot'
const queries = JSON.parse(open(`../out/queries-${SET}.json`))

export const options = {
  scenarios: {
    retrieval: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1m', duration: '3m', preAllocatedVUs: 30, maxVUs: 120 },
  },
  thresholds: thresholds(['public_read']),
}

let cursor = 0
export default function () {
  const q = queries[cursor % queries.length]
  cursor++
  const res = req('GET', api(`/titles?search=${encodeURIComponent(q.q)}&limit=10`), 'public_read')
  check(res, { 'retrieval 2xx': (r) => r.status === 200 })
  if (cursor % 5 === 0) {
    const items = res.json()?.data?.items ?? []
    if (items.length > 0) req('GET', api(`/titles/${items[0].id}`), 'public_read')
  }
}
