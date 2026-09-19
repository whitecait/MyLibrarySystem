import { afterEach, describe, expect, it } from 'vitest'

import { FailureKind, setTransport } from '@/platform/http'
import { installMockTransport } from '../../tests/helpers'
import * as ai from './index'

afterEach(() => setTransport(null))

describe('ai module facade', () => {
  it('replays the L2 prefill scenario with actions and quota', async () => {
    installMockTransport({ scenarioId: 'AI-L2-PREFILL' })
    const envelope = await ai.sendAiMessage({ sessionId: '7001', message: '帮我预填续借' })
    expect(envelope.code).toBe('MLS-AI-S001')
    const action = envelope.data.actions[0]
    expect(action).toEqual({
      actionType: 'RENEW_LOAN',
      level: 'L2',
      prefill: { txId: '4001' },
      draftId: '7201',
      label: '打开续借表单',
    })
    expect(envelope.data.quota).toEqual({ remaining: 9 })
  })

  it('replays quota exhaustion as a typed 429 failure', async () => {
    installMockTransport({ scenarioId: 'AI-QUOTA-EXHAUSTED' })
    const failure = await ai.sendAiMessage({ sessionId: '7001', message: '查询我的借阅' }).catch((error) => error)
    expect(failure.kind).toBe(FailureKind.QUOTA_EXHAUSTED)
    expect(failure.code).toBe('MLS-AI-002')
    expect(failure.data).toEqual({ remaining: 0 })
  })

  it('maps session and event operations to the contract', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: {} }
      },
    })
    await ai.startAiSession()
    await ai.listAiEvents({ sessionId: '7001', limit: 20 })

    expect(calls.map((call) => call.operationId)).toEqual(['startAiSession', 'listAiEvents'])
    expect(calls[0].options).toEqual({})
  })

  it('builds the real event-stream URL with the owned session id', async () => {
    const urls = []
    await ai.listAiEvents(
      { sessionId: '7001', cursor: 'cursor-1', limit: 20 },
      {
        fetchImpl: async (url) => {
          urls.push(url)
          return new Response(
            JSON.stringify({ code: 'MLS-AI-S001', message: 'ok', data: { items: [], nextCursor: null } }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          )
        },
      },
    )

    expect(urls).toEqual(['/api/v1/ai/sessions/7001/events?cursor=cursor-1&limit=20'])
  })

  it('passes caller headers through for draft attribution', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: {} }
      },
    })
    await ai.sendAiMessage({ sessionId: '7001', message: 'hi' }, { headers: { 'Ai-Draft-Id': '7201' } })
    expect(calls[0].options.headers).toEqual({ 'Ai-Draft-Id': '7201' })
  })

  it('exposes the module view through the public entry', () => {
    expect(ai.AiAssistantView).toBeTruthy()
  })
})
