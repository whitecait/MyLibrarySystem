import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import MyBusinessView from './MyBusinessView.vue'

beforeEach(() => logout())
afterEach(() => logout())

async function mountBusiness(fixtures) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(MyBusinessView)
  await flushPromises()
  return wrapper
}

describe('MyBusinessView', () => {
  it('lists business items with amount and kind presentation', async () => {
    const wrapper = await mountBusiness(sharedFixtures)
    const rows = wrapper.findAll('[data-testid="my-business-table"] tbody tr')
    expect(rows).toHaveLength(3)
    expect(wrapper.text()).toContain('Database Systems')
    expect(wrapper.text()).toContain('1.50 元')
    expect(wrapper.find('[data-testid="renew-4001"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cancel-4003"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="renew-5001"]').exists()).toBe(false)
  })

  it('renews a loan and shows the new due date', async () => {
    const wrapper = await mountBusiness(sharedFixtures)

    await wrapper.find('[data-testid="renew-4001"]').trigger('click')
    await flushPromises()

    const result = wrapper.find('[data-testid="renew-result"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('2026-09-29')
  })

  it('presents renewal rule rejections as typed failures', async () => {
    const wrapper = await mountBusiness({
      ...sharedFixtures,
      renewLoan: { status: 422, code: 'MLS-CIRC-012', message: '已被预约，无法续借', data: {} },
    })

    await wrapper.find('[data-testid="renew-4001"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="renew-result"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-CIRC-012')
  })

  it('cancels a reservation and reloads the list', async () => {
    let calls = 0
    const wrapper = await mountBusiness({
      getMyBusiness: {
        code: 'MLS-QUERY-S002',
        message: 'ok',
        get data() {
          calls += 1
          return calls === 1
            ? {
                items: [{ kind: 'RESERVATION', id: '4003', status: 'WAITLIST' }],
                nextCursor: null,
              }
            : { items: [], nextCursor: null }
        },
      },
      cancelReservation: { code: 'MLS-CIRC-S006', message: 'canceled', data: { txId: '4003', outcome: 'CANCELED' } },
    })

    await wrapper.find('[data-testid="cancel-4003"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(true)
  })
})
