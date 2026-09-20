// Shared k6 helpers and thresholds (MLS-NFR-PER-0001 response classes,
// MLS-NFR-PER-0002 SLI rules: unexpected failures must be 0 within load
// windows; expected business rejections are tagged and excluded).
import http from 'k6/http'
import { Counter } from 'k6/metrics'

export const BASE_URL = __ENV.BASE_URL || 'http://frontend:80'
export const RUN_ID = __ENV.RUN_ID || 'local'

export const unexpected = new Counter('unexpected_failures')

// Response-time classes (RA-0013 §7 MLS-NFR-PER-0001).
export const CLASSES = {
  public_read: { p95: 2000, p99: 4000 }, // 公开检索/列表/详情
  normal_read: { p95: 2000, p99: 4000 }, // 登录/注册/普通查询/单对象读取
  circulation: { p95: 3000, p99: 5000 }, // 借阅/归还/续借/预约/罚款
  payment: { p95: 3000, p99: 6000 }, // 模拟支付结果/退款/对账
  audit: { p95: 4000, p99: 8000 }, // 审计查询与筛选
}

export function thresholds(classes) {
  const t = { unexpected_failures: ['count==0'] }
  for (const c of classes) {
    t[`http_req_duration{class:${c}}`] = [`p(95)<${CLASSES[c].p95}`, `p(99)<${CLASSES[c].p99}`]
  }
  return t
}

export const api = (path) => `${BASE_URL}/api/v1${path}`

// Classifies one response per the four-class error scheme. `expected` marks
// requests whose 4xx is a controlled business/permission rejection by design.
export function classify(res, className, expected = false) {
  const status = res.status
  const isUnexpected = status === 0 || status >= 500 || (status >= 400 && !expected)
  if (isUnexpected) unexpected.add(1)
  return isUnexpected
}

export function req(method, url, className, body = null, expected = false, headers = {}) {
  const params = { tags: { class: className, expected: String(expected) }, headers }
  const res = body === null ? http.request(method, url, null, params) : http.request(method, url, JSON.stringify(body), params)
  classify(res, className, expected)
  return res
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
