import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport } from '../../tests/helpers'
import * as settlement from './index'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => {
  logout()
  setTransport(null)
})

describe('settlement module facade', () => {
  it('replays the concurrent order conflict scenario as a typed 409', async () => {
    installMockTransport({ scenarioId: 'ORDER-CONCURRENT-CONFLICT' })
    const failure = await settlement.createOrder({ fineId: '5001' }).catch((error) => error)
    expect(failure.kind).toBe('CONFLICT')
    expect(failure.code).toBe('MLS-PAY-001')
    expect(failure.data).toEqual({ fineId: '5001' })
  })

  it('creates an order from a fine via the mock fixtures', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const envelope = await settlement.createOrder({ fineId: '5001' })
    expect(envelope.data.status).toBe('OCCUPIED')
  })

  it('maps the settlement operations to the contract', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: {} }
      },
    })
    await settlement.listFines({ status: 'UNPAID' })
    await settlement.getOrder({ orderId: '6002' })
    await settlement.scheduleSimulatedPayment({ orderId: '6002', result: 'FAILURE', duplicateCount: 2 })
    await settlement.closeOrder({ orderId: '6002' })
    await settlement.requestRefund({ orderId: '6002', amountCents: 150, reason: '多收' })
    await settlement.reconcileBusinessDate({ businessDate: '2026-09-15' })

    expect(calls.map((call) => call.operationId)).toEqual([
      'listFines',
      'getOrder',
      'scheduleSimulatedPayment',
      'closeOrder',
      'requestRefund',
      'reconcileBusinessDate',
    ])
    expect(calls[2].options.body).toEqual({ orderId: '6002', result: 'FAILURE', delayMs: undefined, duplicateCount: 2 })
    expect(calls[4].options.body).toEqual({ amountCents: 150, reason: '多收' })
    expect(calls[5].options.body).toEqual({ businessDate: '2026-09-15' })
  })

  it('exposes the module views through the public entry', () => {
    expect(settlement.FineSettlementView).toBeTruthy()
    expect(settlement.PaymentSimulatorView).toBeTruthy()
    expect(settlement.ReconciliationView).toBeTruthy()
  })
})
