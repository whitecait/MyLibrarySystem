import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import FineSettlementView from './FineSettlementView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => {
  logout()
  setTransport(null)
})

async function mountFines(fixtures) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(FineSettlementView)
  await flushPromises()
  return wrapper
}

describe('FineSettlementView', () => {
  it('lists fines and creates an order from an unpaid fine', async () => {
    const wrapper = await mountFines(sharedFixtures)

    expect(wrapper.find('[data-testid="payment-simulation-warning"]').text())
      .toContain('不接入真实资金')
    expect(wrapper.find('[data-testid="payment-simulation-warning"]').text())
      .toContain('不会扣款或转账')

    const rows = wrapper.findAll('[data-testid="fines-table"] tbody tr')
    expect(rows).toHaveLength(1)
    expect(wrapper.text()).toContain('1.50 元')

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('6002')
    expect(wrapper.find('[data-testid="order-wait-note"]').text()).toContain('等待超时不代表支付失败')
  })

  it('allows closing an occupied unpaid order and hides close after it is closed', async () => {
    const wrapper = await mountFines(sharedFixtures)

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('OCCUPIED')
    expect(wrapper.find('[data-testid="order-close"]').exists()).toBe(true)

    await wrapper.find('[data-testid="order-close"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('CLOSED')
    expect(wrapper.find('[data-testid="order-close"]').exists()).toBe(false)
  })

  it('presents concurrent order creation conflicts without changing the fine', async () => {
    const wrapper = await mountFines({
      ...sharedFixtures,
      createOrder: { status: 409, code: 'MLS-PAY-001', message: '已有未释放支付单', data: { fineId: '5001' } },
    })

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="order-panel-title"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-PAY-001')
    expect(wrapper.find('[data-testid="fines-table"]').exists()).toBe(true)
  })

  it('refreshes the order state manually while waiting for the callback', async () => {
    const wrapper = await mountFines({
      ...sharedFixtures,
      getOrder: {
        code: 'MLS-PAY-S003',
        message: 'ok',
        data: { orderId: '6002', fineId: '5001', amountCents: 150, status: 'PAID', expiresAt: null },
      },
    })

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('OCCUPIED')

    await wrapper.find('[data-testid="order-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('PAID')
    expect(wrapper.find('[data-testid="order-close"]').exists()).toBe(false)
  })

  it('restores a server-owned occupied order after a fresh load and permits query and close', async () => {
    const calls = []
    const occupiedOrder = {
      orderId: '6002',
      fineId: '5001',
      amountCents: 150,
      status: 'OCCUPIED',
      expiresAt: '2026-09-30T12:15:00Z',
    }
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        if (operationId === 'listFines') {
          return {
            code: 'MLS-PAY-S001',
            message: 'ok',
            data: {
              items: [{ fineId: '5001', readerId: '1001', amountCents: 150, status: 'UNPAID' }],
              nextCursor: null,
            },
          }
        }
        if (operationId === 'listOccupiedOrders') {
          return { code: 'MLS-PAY-S002', message: 'ok', data: { items: [occupiedOrder], nextCursor: null } }
        }
        if (operationId === 'getOrder') {
          return { code: 'MLS-PAY-S003', message: 'ok', data: occupiedOrder }
        }
        if (operationId === 'closeOrder') {
          return { code: 'MLS-PAY-S004', message: 'ok', data: { ...occupiedOrder, status: 'CLOSED' } }
        }
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })
    const wrapper = await mountView(FineSettlementView)
    await flushPromises()

    expect(calls.some((call) => call.operationId === 'listOccupiedOrders')).toBe(true)
    expect(wrapper.find('[data-testid="order-panel-title"]').exists()).toBe(false)
    await wrapper.get('[data-testid="select-order-6002"]').trigger('click')
    await flushPromises()
    expect(calls.find((call) => call.operationId === 'listOccupiedOrders').options.query)
      .toEqual({ cursor: undefined, limit: 20 })
    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('6002')
    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('OCCUPIED')
    expect(wrapper.find('[data-testid="order-refresh"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="order-close"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="create-order-5001"]').exists()).toBe(false)

    await wrapper.find('[data-testid="order-refresh"]').trigger('click')
    await flushPromises()
    expect(calls.find((call) => call.operationId === 'getOrder').options.params).toEqual({ orderId: '6002' })

    await wrapper.find('[data-testid="order-close"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('CLOSED')
    expect(wrapper.find('[data-testid="order-close"]').exists()).toBe(false)
  })

  it('does not expose a refund action to librarians', async () => {
    await login({ accessToken: 't', actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const wrapper = await mountFines(sharedFixtures)

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="refund-amount"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="refund-reason"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="refund-submit"]').exists()).toBe(false)
  })

  it('keeps librarian fine listing available without calling the reader-only occupied-order query', async () => {
    await login({ accessToken: 't', actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId) {
        calls.push(operationId)
        if (operationId === 'listFines') return sharedFixtures.listFines
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })

    const wrapper = await mountView(FineSettlementView)
    await flushPromises()

    expect(wrapper.find('[data-testid="fines-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(false)
    expect(calls).toContain('listFines')
    expect(calls).not.toContain('listOccupiedOrders')
  })

  it('shows an occupied-order recovery failure even when the query returns no orders', async () => {
    setTransport({
      mode: 'mock',
      async request(operationId) {
        if (operationId === 'listFines') return sharedFixtures.listFines
        if (operationId === 'listOccupiedOrders') throw new Error('orders query unavailable')
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })

    const wrapper = await mountView(FineSettlementView)
    await flushPromises()

    expect(wrapper.find('[data-testid="occupied-orders-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('orders query unavailable')
  })
})
