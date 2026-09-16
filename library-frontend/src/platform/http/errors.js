// 请求失败的统一类型（MD-0003 HTTP 状态映射 + 传输层异常）。
// 页面只按 kind 与稳定错误码分支呈现，不得自造错误码。
export const FailureKind = {
  UNAUTHENTICATED: 'UNAUTHENTICATED', // 401 未认证
  FORBIDDEN: 'FORBIDDEN', // 403 无权限
  NOT_FOUND: 'NOT_FOUND', // 404 不可见对象
  INVALID: 'INVALID', // 400 字段错误
  CONFLICT: 'CONFLICT', // 409 业务/版本/幂等冲突
  REJECTED: 'REJECTED', // 422 规则拒绝
  QUOTA_EXHAUSTED: 'QUOTA_EXHAUSTED', // 429 额度耗尽（共同样例 AI-QUOTA-EXHAUSTED）
  SYSTEM: 'SYSTEM', // 500 及其余未预期状态
  NETWORK: 'NETWORK', // 网络不可达
  TIMEOUT: 'TIMEOUT', // 前端总等待超时（30s 上限）
  CANCELLED: 'CANCELLED', // 调用方主动取消
  MALFORMED: 'MALFORMED', // 响应不符合统一信封
}

const STATUS_KIND_MAP = {
  400: FailureKind.INVALID,
  401: FailureKind.UNAUTHENTICATED,
  403: FailureKind.FORBIDDEN,
  404: FailureKind.NOT_FOUND,
  409: FailureKind.CONFLICT,
  422: FailureKind.REJECTED,
  429: FailureKind.QUOTA_EXHAUSTED,
}

export class RequestFailure extends Error {
  constructor({ kind, status = null, code = null, message, data = null, cause = null }) {
    super(message, cause ? { cause } : undefined)
    this.name = 'RequestFailure'
    this.kind = kind
    this.status = status
    this.code = code
    this.data = data
  }
}

export function failureKindForStatus(status) {
  return STATUS_KIND_MAP[status] ?? FailureKind.SYSTEM
}

export function failureFromHttpStatus(status, body) {
  const envelope = body != null && typeof body === 'object' && !Array.isArray(body) ? body : null
  return new RequestFailure({
    kind: failureKindForStatus(status),
    status,
    code: typeof envelope?.code === 'string' ? envelope.code : null,
    message: typeof envelope?.message === 'string' && envelope.message.length > 0 ? envelope.message : `HTTP ${status}`,
    data: envelope?.data ?? null,
  })
}
