import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import PaymentSimulatorView from './PaymentSimulatorView.vue'

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
