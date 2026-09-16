import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ArrivalDashboardView from './ArrivalDashboardView.vue'

afterEach(() => setTransport(null))

describe('ArrivalDashboardView', () => {
  it('renders the arrival counts', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(ArrivalDashboardView)
    await flushPromises()

    await wrapper.find('[data-testid="dashboard-date"]').setValue('2026-09-16')
    await wrapper.find('[data-testid="dashboard-filter"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="dashboard-expected"]').text()).toBe('12')
    expect(wrapper.find('[data-testid="dashboard-arrived"]').text()).toBe('7')
    expect(wrapper.find('[data-testid="dashboard-pending"]').text()).toBe('4')
    expect(wrapper.find('[data-testid="dashboard-exceptions"]').text()).toBe('1')
  })

  it('presents dashboard failures as typed errors', async () => {
    installMockTransport({
      fixtures: {
        getArrivalDashboard: { status: 500, code: 'MLS-QUERY-001', message: 'system failure', data: {} },
      },
    })
    const wrapper = await mountView(ArrivalDashboardView)
    await flushPromises()

    await wrapper.find('[data-testid="dashboard-filter"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-QUERY-001')
  })
})
