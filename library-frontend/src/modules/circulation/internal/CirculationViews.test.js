import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ReservationCalendarView from './ReservationCalendarView.vue'

afterEach(() => setTransport(null))

describe('ReservationCalendarView', () => {
  it('renders the availability per business day', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await flushPromises()

    await wrapper.find('[data-testid="calendar-from"]').setValue('2026-09-16')
    await wrapper.find('[data-testid="calendar-to"]').setValue('2026-09-18')
    await wrapper.find('[data-testid="calendar-form"]').trigger('submit')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="calendar-table"] tbody tr')
    expect(rows).toHaveLength(3)
    expect(wrapper.find('[data-testid="calendar-day-2026-09-16"]').text()).toContain('有保障')
    expect(wrapper.find('[data-testid="calendar-day-2026-09-18"]').text()).toContain('不可预约')
  })

  it('presents invalid ranges as typed failures', async () => {
    installMockTransport({
      fixtures: {
        getReservationCalendar: { status: 422, code: 'MLS-CIRC-001', message: '日期无效', data: {} },
      },
    })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await flushPromises()

    await wrapper.find('[data-testid="calendar-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-CIRC-001')
  })
})
