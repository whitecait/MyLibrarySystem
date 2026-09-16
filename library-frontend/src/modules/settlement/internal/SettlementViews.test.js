import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import PaymentSimulatorView from './PaymentSimulatorView.vue'
import ReconciliationView from './ReconciliationView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1003', role: 'ADMIN' } }))
afterEach(() => logout())

describe('PaymentSimulatorView', () => {
  it('schedules a simulated result with duplicate count', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(PaymentSimulatorView)
    await flushPromises()

    await wrapper.find('[data-testid="simulator-order-id"]').setValue('6002')
    await wrapper.find('[data-testid="simulator-result"]').setValue('FAILURE')
    await wrapper.find('[data-testid="simulator-duplicate"]').setValue('2')
    await wrapper.find('[data-testid="simulator-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="simulator-scheduled"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('8001')
  })
})

describe('ReconciliationView', () => {
  it('reconciles a business date and shows counts and exceptions', async () => {
    installMockTransport({
      fixtures: {
        reconcileBusinessDate: {
          code: 'MLS-PAY-S007',
          message: 'reconciled',
          data: {
            reconciliationId: '9001',
            counts: { orders: 3, paid: 2, failed: 1 },
            exceptions: ['订单 6002 回调迟到'],
          },
        },
      },
    })
    const wrapper = await mountView(ReconciliationView)
    await flushPromises()

    await wrapper.find('[data-testid="reconcile-date"]').setValue('2026-09-15')
    await wrapper.find('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="reconcile-success"]').text()).toContain('9001')
    expect(wrapper.find('[data-testid="reconcile-counts"]').text()).toContain('3')
    expect(wrapper.find('[data-testid="reconcile-exceptions"]').text()).toContain('6002')
  })

  it('marks a clean reconciliation without exceptions', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(ReconciliationView)
    await flushPromises()

    await wrapper.find('[data-testid="reconcile-date"]').setValue('2026-09-15')
    await wrapper.find('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="reconcile-clean"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reconcile-exceptions"]').exists()).toBe(false)
  })

  it('presents duplicate reconciliation conflicts', async () => {
    installMockTransport({
      fixtures: {
        reconcileBusinessDate: { status: 409, code: 'MLS-PAY-008', message: '对账日期重复', data: {} },
      },
    })
    const wrapper = await mountView(ReconciliationView)
    await flushPromises()

    await wrapper.find('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-PAY-008')
  })
})
