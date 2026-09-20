// Retrieval-specific sub-scenario (3 minutes, >=100 valid samples).
// RETRIEVAL_SET=hot|tail selects the popular or long-tail query set produced
// by the dataset generator; the cold/hot cache pairing is orchestrated by
// run-protocol.sh (cold = database container restarted immediately before).
import { check } from 'k6'
import { api, req, thresholds } from '../lib/common.js'

const SET = __ENV.RETRIEVAL_SET || 'hot'
// The pinned k6 image (2.2.0) does not export open from the 'k6' module
// (the import binds null); the file-reading global is used instead. Absolute
// in-container path: the k6 service mounts ./e2e/perf at /scripts
// (compose.perf-quota.yaml).
const queries = JSON.parse(open(`/scripts/out/queries-${SET}.json`))

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
  const res = req('GET', api(`/titles?q=${encodeURIComponent(q.q)}&limit=10`), 'public_read')
  check(res, { 'retrieval 2xx': (r) => r.status === 200 })
  if (cursor % 5 === 0) {
    const items = res.json()?.data?.items ?? []
    if (items.length > 0) req('GET', api(`/titles/${items[0].titleId}`), 'public_read')
  }
}
