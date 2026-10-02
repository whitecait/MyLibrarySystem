// Shared k6 helpers and thresholds (MLS-NFR-PER-0001 response classes,
// MLS-NFR-PER-0002 SLI rules: unexpected failures must be 0 within load
// windows; expected business rejections are declared per call site and
// counted in their own class).
//
// Error contract grounding: the response envelope is {code,message,data}
// with `code` carrying the stable business code (MD-0003 §1, Envelope.java),
// and StableErrorCode maps codes to HTTP statuses (400 字段错误, 401 未认证,
// 403 无权限, 404 不可见, 409 冲突, 422 业务拒绝, 429 限额, 500 系统失败).
import http from 'k6/http'
import { Counter } from 'k6/metrics'

export const BASE_URL = __ENV.BASE_URL || 'http://frontend:80'
export const RUN_ID = __ENV.RUN_ID || 'local'

// Four-class error scheme: every response lands in exactly one class.
// `unexpected_failures` is kept as the derived aggregate
// (unexpected_4xx + server_5xx + timeouts) — it stays the MLS-NFR-PER-0002
// SLI metric and the one existing tooling reads.
export const expected_4xx = new Counter('expected_4xx')
export const unexpected_4xx = new Counter('unexpected_4xx')
export const server_5xx = new Counter('server_5xx')
export const timeouts = new Counter('timeouts')
export const unexpected = new Counter('unexpected_failures')

const CLASS_COUNTERS = {
  expected_4xx,
  unexpected_4xx,
  server_5xx,
  timeouts,
}

// Response-time classes (RA-0013 §7 MLS-NFR-PER-0001).
export const CLASSES = {
  public_read: { p95: 2000, p99: 4000 }, // 公开检索/列表/详情
  normal_read: { p95: 2000, p99: 4000 }, // 登录/注册/普通查询/单对象读取
  circulation: { p95: 3000, p99: 5000 }, // 借阅/归还/续借/预约/罚款
  payment: { p95: 3000, p99: 6000 }, // 模拟支付结果/退款/对账
  audit: { p95: 4000, p99: 8000 }, // 审计查询与筛选
}

export function thresholds(classes) {
  const percentileScope = __ENV.PERF_PERCENTILE_SCOPE || 'full'
  if (!['full', 'p95'].includes(percentileScope)) throw new Error('PERF_PERCENTILE_SCOPE must be full or p95')
  const t = { unexpected_failures: ['count==0'] }
  for (const c of classes) {
    t[`http_req_duration{class:${c}}`] = [`p(95)<${CLASSES[c].p95}`]
    // P0 approved removal of P99 assertions. Default full preserves the legacy
    // protocol; explicit p95 changes only the approved percentile gate.
    if (percentileScope === 'full') t[`http_req_duration{class:${c}}`].push(`p(99)<${CLASSES[c].p99}`)
    // Observability-only sub-metric thresholds (always true, never cross):
    // k6 only materialises tag sub-metrics that a threshold references, so
    // these put the per-class request count and failure rate into the
    // summary export for e2e/perf/collect-verdicts.mjs to count valid
    // samples per scenario and class.
    t[`http_reqs{class:${c}}`] = ['count>=0']
    t[`http_req_failed{class:${c}}`] = ['rate>=0']
  }
  return t
}

export const api = (path) => `${BASE_URL}/api/v1${path}`

// Normalizes an `expected` declaration into a flat list of
// {status, reason} pairs. Accepted shapes: one entry, an array of entries,
// and array-valued status/reason inside an entry. An empty declaration is
// the default: any 4xx then counts as unexpected_4xx. A legacy boolean
// argument normalizes to the empty set (never treated as "all 4xx ok").
function asList(v) {
  if (v === undefined || v === null) return []
  return Array.isArray(v) ? v : [v]
}

export function normalizeExpected(expected) {
  return asList(expected).flatMap((entry) =>
    asList(entry?.status).flatMap((status) =>
      asList(entry?.reason).map((reason) => ({ status: Number(status), reason: String(reason) }))))
}

// Reads the stable business code from the response envelope; null when the
// body is not parseable JSON or carries no string `code`.
function envelopeCode(res) {
  try {
    const body = res.json()
    return typeof body?.code === 'string' ? body.code : null
  } catch {
    return null
  }
}

// Classifies one response per the four-class error scheme and returns the
// class ('expected_4xx' | 'unexpected_4xx' | 'server_5xx' | 'timeouts' |
// 'ok'). A 4xx counts as expected only when the call site declared it and
// BOTH the HTTP status and the envelope business code match a declaration —
// a 409 from a different failure path than declared is unexpected_4xx.
export function classify(res, className, expected = null) {
  const status = res.status
  let cls
  if (status === 0) cls = 'timeouts'
  else if (status >= 500) cls = 'server_5xx'
  else if (status >= 400) {
    const code = envelopeCode(res)
    const hit = normalizeExpected(expected).some((e) => e.status === status && e.reason === code)
    cls = hit ? 'expected_4xx' : 'unexpected_4xx'
  } else cls = 'ok'
  if (cls !== 'ok') CLASS_COUNTERS[cls].add(1)
  if (cls === 'unexpected_4xx' || cls === 'server_5xx' || cls === 'timeouts') unexpected.add(1)
  return cls
}

export function req(method, url, className, body = null, expected = null, headers = {}, jar = null) {
  // Every mutating endpoint declares Idempotency-Key as a required header
  // (including login); a missing key is a 400 before any business logic runs.
  // JSON bodies need the content type spelled out — k6 does not set it for
  // pre-serialized strings, and Spring rejects the parse in ~1ms.
  const h = { ...headers }
  if (method !== 'GET' && !h['Idempotency-Key']) h['Idempotency-Key'] = idemKey()
  if (body !== null && !h['Content-Type']) h['Content-Type'] = 'application/json'
  const expectedSet = normalizeExpected(expected)
  const params = { tags: { class: className, expected: expectedSet.length > 0 ? 'true' : 'false' }, headers: h, ...(jar ? { jar } : {}) }
  const res = body === null ? http.request(method, url, null, params) : http.request(method, url, JSON.stringify(body), params)
  classify(res, className, expectedSet)
  return res
}

export function idemKey() {
  return `${RUN_ID}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function login(username, password) {
  const res = req('POST', api('/auth/sessions'), 'normal_read', { username, password })
  if (res.status !== 200 && res.status !== 201) return null
  const body = res.json()
  return body?.data?.accessToken ?? body?.data?.token ?? null
}

export const auth = (token) => ({ Authorization: `Bearer ${token}` })

export const PROBE_PREFIX = `perf-${RUN_ID}`
export const readerName = (n) => `${PROBE_PREFIX}-reader-${String(n).padStart(3, '0')}`
export const probePassword = 'Perf-Probe-2026!'

// Environment staff injected by e2e/data/load-dataset.sh (not part of the
// versioned dataset): the steady load needs an authenticated LIBRARIAN for
// audit queries and the damaged-return leg of the payment chain, and a
// separate ADMIN for the simulated-payment scheduling call (DEF-M6-008,
// widened 2026-09-22: CONTROLLED_TEST_OPERATOR maps to ADMIN, so ordinary
// circulation traffic keeps using the librarian probe).
export const librarianName = 'perf-probe-librarian'
export const librarianPassword = 'Perf-Librarian-2026!'
export const adminName = 'perf-probe-admin'
export const adminPassword = 'Perf-Admin-2026!'

// End-of-test summary: prints the four-class counts, the derived aggregate
// and the threshold verdicts. The raw `--summary-export` JSON file is still
// written by k6 independently of handleSummary (verified on the pinned
// image); in the handleSummary data a counter lives under
// data.metrics[name].values.count, and zero-sample metrics are absent from
// data.metrics entirely.
export function handleSummary(data) {
  const count = (name) => {
    const m = data.metrics?.[name]
    return m?.values?.count ?? m?.count ?? 0
  }
  const lines = [
    `=== four-class error counts (run ${RUN_ID}) ===`,
    `http_reqs total: ${count('http_reqs')}`,
    `expected_4xx: ${count('expected_4xx')}`,
    `unexpected_4xx: ${count('unexpected_4xx')}`,
    `server_5xx: ${count('server_5xx')}`,
    `timeouts: ${count('timeouts')}`,
    `unexpected_failures (derived = unexpected_4xx + server_5xx + timeouts): ${count('unexpected_failures')}`,
  ]
  for (const [name, m] of Object.entries(data.metrics ?? {})) {
    const th = m?.thresholds ?? m?.values?.thresholds
    for (const [rule, verdict] of Object.entries(th ?? {})) {
      const ok = typeof verdict === 'boolean' ? verdict : verdict?.ok !== false
      lines.push(`threshold ${name} ${rule}: ${ok ? 'ok' : 'CROSSED'}`)
    }
  }
  return { stdout: lines.join('\n') + '\n' }
}
