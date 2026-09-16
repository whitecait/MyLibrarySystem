// fetch 请求核心：URL 只能由 generated 操作清单构建；统一信封解析、认证头、
// 写操作 Idempotency-Key 注入、30 秒总等待上限与可取消（MD-0003 分页与超时约定）。
import { buildUrl, getOperation } from './operations.js'
import { FailureKind, RequestFailure, failureFromHttpStatus } from './errors.js'
import { isSuccessCode, parseEnvelope } from './envelope.js'

// MD-0003：服务端处理上限 10 秒，前端总等待上限 30 秒并允许取消。
export const DEFAULT_TIMEOUT_MS = 30000

const unauthorizedListeners = new Set()

export function onUnauthorized(listener) {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

function notifyUnauthorized(failure) {
  for (const listener of [...unauthorizedListeners]) listener(failure)
}

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `mls-${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`
}

export async function executeRequest({
  operationId,
  params,
  query,
  body,
  headers,
  idempotencyKey,
  signal,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  getToken = null,
  fetchImpl = globalThis.fetch,
}) {
  const operation = getOperation(operationId)
  if (!operation) throw new Error(`unknown operationId "${operationId}"`)

  const url = buildUrl(operationId, { params, query })
  const requestHeaders = new Headers(headers ?? {})
  const token = getToken?.()
  if (token) requestHeaders.set('Authorization', `Bearer ${token}`)
  if (operation.requiresIdempotencyKey && !requestHeaders.has('Idempotency-Key')) {
    requestHeaders.set('Idempotency-Key', idempotencyKey ?? newIdempotencyKey())
  }

  const init = { method: operation.method, headers: requestHeaders }
  if (body !== undefined) {
    requestHeaders.set('Content-Type', 'application/json')
    init.body = JSON.stringify(body)
  }

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(new DOMException('frontend timeout', 'TimeoutError')), timeoutMs)
  let onExternalAbort = null
  if (signal) {
    if (signal.aborted) controller.abort(signal.reason)
    else {
      onExternalAbort = () => controller.abort(signal.reason)
      signal.addEventListener('abort', onExternalAbort, { once: true })
    }
  }
  init.signal = controller.signal

  let response
  try {
    response = await fetchImpl(url, init)
  } catch (error) {
    const abortKind =
      controller.signal.reason?.name === 'TimeoutError' ? FailureKind.TIMEOUT : FailureKind.CANCELLED
    const kind = isAbortLike(error) ? abortKind : FailureKind.NETWORK
    throw new RequestFailure({
      kind,
      message: kind === FailureKind.TIMEOUT ? 'frontend wait timeout' : 'request cancelled',
      cause: error,
    })
  } finally {
    clearTimeout(timer)
    if (onExternalAbort && signal) signal.removeEventListener('abort', onExternalAbort)
  }

  const rawText = await response.text()
  let parsedBody
  if (rawText.length > 0) {
    try {
      parsedBody = JSON.parse(rawText)
    } catch {
      parsedBody = undefined
    }
  }

  if (!response.ok) {
    const failure = failureFromHttpStatus(response.status, parsedBody ?? null)
    if (response.status === 401) notifyUnauthorized(failure)
    throw failure
  }

  if (parsedBody === undefined || parsedBody === null) {
    throw new RequestFailure({
      kind: FailureKind.MALFORMED,
      status: response.status,
      message: 'success response is not valid JSON',
    })
  }
  try {
    const envelope = parseEnvelope(parsedBody)
    if (!isSuccessCode(envelope.code)) {
      throw new RequestFailure({
        kind: FailureKind.MALFORMED,
        status: response.status,
        code: envelope.code,
        message: `success response carries non-success code "${envelope.code}"`,
        data: envelope.data,
      })
    }
    return envelope
  } catch (error) {
    if (error instanceof RequestFailure) throw error
    throw new RequestFailure({
      kind: FailureKind.MALFORMED,
      status: response.status,
      message: error instanceof Error ? error.message : 'malformed success envelope',
      cause: error,
    })
  }
}

function isAbortLike(error) {
  return error != null && (error.name === 'AbortError' || error.name === 'TimeoutError')
}
