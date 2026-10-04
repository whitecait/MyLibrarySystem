import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { getOperation } from '@/platform/http/operations'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ClosingAdminView from './ClosingAdminView.vue'
import ReservePanel from './ReservePanel.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1003', role: 'ADMIN' } }))
afterEach(() => logout())

describe('ClosingAdminView', () => {
  it('reopens a business date and presents the new epoch', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(ClosingAdminView)
    await flushPromises()

    await wrapper.find('[data-testid="reopen-date"]').setValue('2026-09-15')
    await wrapper.find('[data-testid="reopen-form"]').trigger('submit')
    await flushPromises()

    const success = wrapper.find('[data-testid="reopen-success"]')
    expect(success.exists()).toBe(true)
    expect(success.text()).toContain('REOPENED')
    expect(success.text()).toContain('2')
    expect(success.text()).toContain('不会自动重放')
  })

  it('blocks reopening while the closing batch has not caught up', async () => {
    installMockTransport({
      fixtures: {
        reopenBusinessDate: { status: 409, code: 'MLS-CIRC-008', message: '批次未追平', data: {} },
      },
    })
    const wrapper = await mountView(ClosingAdminView)
    await flushPromises()

    await wrapper.find('[data-testid="reopen-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="reopen-blocked"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reopen-success"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('补结')
  })

  it('surfaces other reopen failures without the blocked hint', async () => {
    installMockTransport({
      fixtures: {
        reopenBusinessDate: { status: 403, code: 'MLS-ID-001', message: 'forbidden', data: {} },
      },
    })
    const wrapper = await mountView(ClosingAdminView)
    await flushPromises()

    await wrapper.find('[data-testid="reopen-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="reopen-blocked"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-ID-001')
  })
})

describe('isolation period presentation', () => {
  it('shows an explicit closing-coordination failure instead of hanging', async () => {
    const transport = installMockTransport({
      fixtures: {
        reserveTitle: { status: 422, code: 'MLS-CIRC-014', message: '闭馆协调失败，请稍后重试', data: {} },
      },
    })
    const request = vi.spyOn(transport, 'request')
    const wrapper = await mountView(ReservePanel, { props: { titleId: '2001' } })
    await flushPromises()

    await wrapper.find('[data-testid="reserve-date"]').setValue('2026-09-16')
    await wrapper.find('[data-testid="reserve-slot"]').setValue('14:00-14:30')
    await wrapper.find('[data-testid="reserve-form"]').trigger('submit')
    await flushPromises()

    expect(request).toHaveBeenCalledOnce()
    expect(request.mock.calls[0][0]).toBe('reserveTitle')
    expect(getOperation(request.mock.calls[0][0]).method).toBe('POST')
    expect(request.mock.calls[0][1].body.pickupSlot).toBe('14:00-14:30')

    const alert = wrapper.find('[data-testid="error-alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('MLS-CIRC-014')
  })
})
