import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import FineSettlementView from './FineSettlementView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => logout())

async function mountFines(fixtures) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(FineSettlementView)
  await flushPromises()
  return wrapper
}

describe('FineSettlementView', () => {
  it('lists fines and creates an order from an unpaid fine', async () => {
    const wrapper = await mountFines(sharedFixtures)

    const rows = wrapper.findAll('[data-testid="fines-table"] tbody tr')
    expect(rows).toHaveLength(1)
    expect(wrapper.text()).toContain('1.50 元')

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="order-panel-title"]').text()).toContain('6002')
    expect(wrapper.find('[data-testid="order-wait-note"]').text()).toContain('等待超时不代表支付失败')
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
  })

  it('submits a refund request and shows only the pending review reference', async () => {
    await login({ accessToken: 't', actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const wrapper = await mountFines(sharedFixtures)

    await wrapper.find('[data-testid="create-order-5001"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="refund-amount"]').setValue('1.5')
    await wrapper.find('[data-testid="refund-reason"]').setValue('多收费用')
    await wrapper.find('[data-testid="refund-submit"]').trigger('click')
    await flushPromises()

    const pending = wrapper.find('[data-testid="refund-review-pending"]')
    expect(pending.exists()).toBe(true)
    expect(pending.text()).toContain('6003')
    expect(pending.text()).toContain('PENDING')
    expect(pending.text()).toContain('批准前不生效')
  })
})
