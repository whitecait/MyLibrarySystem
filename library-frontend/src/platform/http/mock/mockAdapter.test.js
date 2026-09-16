import { describe, expect, it } from 'vitest'

import { FailureKind, RequestFailure } from '../errors'
import { createMockTransport } from './mockAdapter'

describe('mock transport', () => {
  it('replays an approved scenario response verbatim', async () => {
    const transport = createMockTransport({ scenarioId: 'CATALOG-PUBLIC-SEARCH' })
    const envelope = await transport.request('searchTitles', { query: { q: 'database', limit: 20 } })
    expect(envelope.code).toBe('MLS-CAT-S001')
    expect(envelope.data.items).toEqual([
      { titleId: '2001', title: 'Database Systems', availability: 'AVAILABLE' },
    ])
    expect(envelope.data.nextCursor).toBeNull()
  })

  it('replays scenario error statuses as typed failures', async () => {
    const transport = createMockTransport({ scenarioId: 'ORDER-CONCURRENT-CONFLICT' })
    const failure = await transport.request('createOrder', { params: { fineId: '5001' } }).catch((error) => error)
    expect(failure).toBeInstanceOf(RequestFailure)
    expect(failure.kind).toBe(FailureKind.CONFLICT)
    expect(failure.code).toBe('MLS-PAY-001')
  })

  it('replays the AI quota exhaustion sample with its 429 status', async () => {
    const transport = createMockTransport({ scenarioId: 'AI-QUOTA-EXHAUSTED' })
    const failure = await transport.request('sendAiMessage', { params: { sessionId: '7001' } }).catch((error) => error)
    expect(failure.kind).toBe(FailureKind.QUOTA_EXHAUSTED)
    expect(failure.code).toBe('MLS-AI-002')
    expect(failure.data).toEqual({ remaining: 0 })
  })

  it('falls back to explicitly registered fixtures outside the selected scenario', async () => {
    const transport = createMockTransport({
      scenarioId: 'CATALOG-PUBLIC-SEARCH',
      fixtures: {
        getMyBusiness: {
          code: 'MLS-QUERY-S001',
          message: 'ok',
          data: { items: [], nextCursor: null },
        },
      },
    })
    await expect(transport.request('searchTitles')).resolves.toBeTruthy()
    const envelope = await transport.request('getMyBusiness', { query: { limit: 20 } })
    expect(envelope.data.items).toEqual([])
  })

  it('fails fast when neither scenario nor fixture covers an operation', async () => {
    const transport = createMockTransport({})
    await expect(transport.request('listAccounts')).rejects.toThrow(/MOCK_MISSING_FIXTURE.*listAccounts/)
  })

  it('stays canned: repeated calls return identical results and payloads never change outcomes', async () => {
    const transport = createMockTransport({ scenarioId: 'RETURN-WITH-FINE' })
    const first = await transport.request('returnCopy', { body: { copyId: '3001', condition: 'INTACT' } })
    const second = await transport.request('returnCopy', { body: { copyId: '9999', condition: 'LOST' } })
    expect(second).toEqual(first)
    expect(first.data).toEqual({ txId: '4001', outcome: 'RETURNED', fineId: '5001' })
  })

  it('rejects unknown operation ids', async () => {
    const transport = createMockTransport({})
    await expect(transport.request('not-an-operation')).rejects.toThrow(/unknown operationId/)
  })
})
