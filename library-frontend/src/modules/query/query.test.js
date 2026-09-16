import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport } from '../../tests/helpers'
import * as query from './index'

afterEach(() => setTransport(null))

describe('query module facade', () => {
  it('fetches the arrival dashboard for a business date', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const envelope = await query.getArrivalDashboard({ businessDate: '2026-09-16' })
    expect(envelope.data).toEqual({ businessDate: '2026-09-16', expected: 12, arrived: 7, pending: 4, exceptions: 1 })
  })

  it('fetches my business with a kind filter', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: { items: [], nextCursor: null } }
      },
    })
    await query.getMyBusiness({ kind: 'LOAN', cursor: 'c2' })
    expect(calls[0].operationId).toBe('getMyBusiness')
    expect(calls[0].options.query).toEqual({ kind: 'LOAN', status: undefined, cursor: 'c2', limit: undefined })
  })

  it('exposes the module views through the public entry', () => {
    expect(query.MyBusinessView).toBeTruthy()
    expect(query.ArrivalDashboardView).toBeTruthy()
  })
})
