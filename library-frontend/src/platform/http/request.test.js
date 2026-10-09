import { afterEach, describe, expect, it, vi } from 'vitest'

import { FailureKind, RequestFailure } from './errors'
import { DEFAULT_TIMEOUT_MS, executeRequest, onUnauthorized } from './request'

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function stubFetch(handler) {
  const calls = []
  const impl = vi.fn(async (url, init) => {
    calls.push({ url, init })
    return handler ? handler(url, init) : jsonResponse(200, { code: 'MLS-CAT-S001', message: 'ok', data: {} })
  })
  vi.stubGlobal('fetch', impl)
  return calls
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('http request core', () => {
  it('sends GET without auth or idempotency headers and parses the envelope', async () => {
    const calls = stubFetch()
    const envelope = await executeRequest({ operationId: 'searchTitles', query: { q: 'database', limit: 20 } })
    expect(envelope.code).toBe('MLS-CAT-S001')
    expect(calls[0].url).toBe('/api/v1/titles?q=database&limit=20')
    expect(calls[0].init.method).toBe('GET')
    expect(calls[0].init.credentials).toBe('same-origin')
    expect(calls[0].init.headers.get('Authorization')).toBeNull()
    expect(calls[0].init.headers.get('Cookie')).toBeNull()
    expect(calls[0].init.headers.get('Idempotency-Key')).toBeNull()
  })

  it('attaches the bearer token and injects a fresh idempotency key per write call', async () => {
    const calls = stubFetch()
    const options = { operationId: 'registerReader', body: { username: 'reader01', password: 'x', displayName: 'r' } }
    await executeRequest({ ...options, getToken: () => 'token-1' })
    await executeRequest({ ...options, getToken: () => 'token-1' })
    expect(calls[0].init.headers.get('Authorization')).toBe('Bearer token-1')
    const firstKey = calls[0].init.headers.get('Idempotency-Key')
    const secondKey = calls[1].init.headers.get('Idempotency-Key')
    expect(firstKey).toBeTruthy()
    expect(secondKey).toBeTruthy()
    expect(firstKey).not.toBe(secondKey)
  })

  it('honors a caller-provided idempotency key', async () => {
    const calls = stubFetch()
    await executeRequest({ operationId: 'reserveTitle', body: {}, idempotencyKey: 'fixed-key-1' })
    expect(calls[0].init.headers.get('Idempotency-Key')).toBe('fixed-key-1')
  })

  it('maps error envelope responses to typed RequestFailure', async () => {
    stubFetch(() =>
      jsonResponse(409, { code: 'MLS-PAY-001', message: 'fine already has an unreleased order', data: { fineId: '5001' } }),
    )
    const failure = await executeRequest({ operationId: 'createOrder', params: { fineId: '5001' } }).catch((error) => error)
    expect(failure).toBeInstanceOf(RequestFailure)
    expect(failure.kind).toBe(FailureKind.CONFLICT)
    expect(failure.status).toBe(409)
    expect(failure.code).toBe('MLS-PAY-001')
    expect(failure.data).toEqual({ fineId: '5001' })
  })

  it('ignores an old unauthorized response cancelled while its body is arriving', async () => {
    const controller = new AbortController()
    let completeBody
    let bodyStarted
    const started = new Promise(resolve => { bodyStarted = resolve })
    const listener = vi.fn()
    const unsubscribe = onUnauthorized(listener)
    try {
      const request = executeRequest({ operationId: 'listNotifications', signal: controller.signal,
        fetchImpl: async () => ({ ok: false, status: 401, text: () => {
          bodyStarted()
          return new Promise(resolve => { completeBody = resolve })
        } }),
      }).catch(error => error)
      await started
      controller.abort()
      completeBody(JSON.stringify({ code: 'MLS-ID-001', message: 'old identity', data: {} }))
      expect((await request).kind).toBe(FailureKind.CANCELLED)
      expect(listener).not.toHaveBeenCalled()
    } finally { unsubscribe() }
  })

  it('notifies unauthorized listeners exactly once on 401', async () => {
    stubFetch(() => jsonResponse(401, { code: 'MLS-ID-001', message: 'sign in required', data: {} }))
    const listener = vi.fn()
    const unsubscribe = onUnauthorized(listener)
    const failure = await executeRequest({ operationId: 'listNotifications' }).catch((error) => error)
    expect(failure.kind).toBe(FailureKind.UNAUTHENTICATED)
    expect(listener).toHaveBeenCalledTimes(1)
    unsubscribe()
  })

  it('lets a form handle 401 locally while preserving the typed failure', async () => {
    stubFetch(() => jsonResponse(401, { code: 'MLS-ID-001', message: 'sign in required', data: {} }))
    const globalListener = vi.fn()
    const localHandler = vi.fn()
    const unsubscribe = onUnauthorized(globalListener)
    try {
      const failure = await executeRequest({ operationId: 'reserveTitle', body: {}, onUnauthorizedResponse: localHandler }).catch(error => error)
      expect(failure.kind).toBe(FailureKind.UNAUTHENTICATED)
      expect(localHandler).toHaveBeenCalledExactlyOnceWith(failure)
      expect(globalListener).not.toHaveBeenCalled()
    } finally { unsubscribe() }
  })

  it('refuses to treat a non-success code on 2xx as success', async () => {
    stubFetch(() => jsonResponse(200, { code: 'MLS-PAY-001', message: 'disguised error', data: {} }))
    const failure = await executeRequest({ operationId: 'searchTitles' }).catch((error) => error)
    expect(failure.kind).toBe(FailureKind.MALFORMED)
    expect(failure.code).toBe('MLS-PAY-001')
  })

  it('classifies non-JSON 5xx bodies as system failures', async () => {
    stubFetch(() => new Response('boom', { status: 500 }))
    const failure = await executeRequest({ operationId: 'searchTitles' }).catch((error) => error)
    expect(failure.kind).toBe(FailureKind.SYSTEM)
    expect(failure.code).toBeNull()
  })

  it('classifies transport rejections as network failures', async () => {
    const failure = await executeRequest({
      operationId: 'searchTitles',
      fetchImpl: async () => {
        throw new TypeError('connection refused')
      },
    }).catch((error) => error)
    expect(failure.kind).toBe(FailureKind.NETWORK)
  })

  it('reports a timeout when the 30s wait budget elapses', async () => {
    vi.useFakeTimers()
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, init) =>
          new Promise((_resolve, reject) => {
            init.signal.addEventListener('abort', () => reject(init.signal.reason))
          }),
      ),
    )
    const pending = executeRequest({ operationId: 'searchTitles' }).catch((error) => error)
    await vi.advanceTimersByTimeAsync(DEFAULT_TIMEOUT_MS + 1)
    const failure = await pending
    expect(failure.kind).toBe(FailureKind.TIMEOUT)
  })

  it('reports cancellation when the caller aborts', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, init) =>
          new Promise((_resolve, reject) => {
            if (init.signal.aborted) reject(init.signal.reason)
            else init.signal.addEventListener('abort', () => reject(init.signal.reason))
          }),
      ),
    )
    const controller = new AbortController()
    const pending = executeRequest({ operationId: 'searchTitles', signal: controller.signal }).catch((error) => error)
    controller.abort(new DOMException('user cancelled', 'AbortError'))
    const failure = await pending
    expect(failure.kind).toBe(FailureKind.CANCELLED)
  })
})
