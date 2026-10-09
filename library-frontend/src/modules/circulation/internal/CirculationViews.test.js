import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ReservationCalendarView from './ReservationCalendarView.vue'

beforeEach(() => vi.setSystemTime(new Date('2026-09-15T02:00:00Z')))
afterEach(() => { setTransport(null); vi.useRealTimers() })

describe('ReservationCalendarView', () => {
  it('loads the month immediately and shows one labelled colour per returned day', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="calendar-form"]').exists()).toBe(false)
    expect(wrapper.get('[data-date="2026-09-16"]').text()).toContain('可借阅')
    expect(wrapper.get('[data-date="2026-09-16"]').classes()).toContain('available')
    expect(wrapper.get('[data-date="2026-09-17"]').text()).toContain('已预定')
    expect(wrapper.get('[data-date="2026-09-18"]').text()).toContain('已借出')
    expect(wrapper.get('[data-date="2026-09-19"]').classes()).not.toContain('lent')
    expect(wrapper.get('[data-date="2026-09-18"]').attributes('aria-label')).toContain('已借出')
  })

  it('opens the current Shanghai month even when the reservation default is next month', async () => {
    vi.setSystemTime(new Date('2026-09-30T15:00:00Z'))
    const queries = []
    setTransport({ mode: 'test', async request(_operationId, options) {
      queries.push(options.query)
      return { code: 'MLS-CIRC-S004', message: 'ok', data: { items: [] } }
    } })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await flushPromises()
    expect(wrapper.get('[data-testid="calendar-month"]').text()).toBe('2026年09月')
    expect(queries).toEqual([{ from: '2026-09-01', to: '2026-09-30' }])
    wrapper.unmount()
  })

  it('does not reinterpret legacy guarantee values as copy states', async () => {
    installMockTransport({ fixtures: { getReservationCalendar: { code: 'MLS-CIRC-S006', message: 'ok', data: { items: [{ businessDate: '2026-09-18', availability: 'UNAVAILABLE' }] } } } })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await flushPromises()
    expect(wrapper.get('[data-date="2026-09-18"]').classes()).not.toContain('lent')
    expect(wrapper.get('[data-date="2026-09-18"]').text()).not.toContain('已借出')
  })

  it('ignores an old month response after the new month has loaded', async () => {
    const pending = []
    setTransport({ mode: 'test', request(_operationId, options) { return new Promise(resolve => pending.push({ options, resolve })) } })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await wrapper.get('[aria-label="下个月"]').trigger('click')
    expect(pending[0].options.query).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(pending[1].options.query).toEqual({ from: '2026-10-01', to: '2026-10-31' })
    pending[1].resolve({ code: 'MLS-CIRC-S006', message: 'ok', data: { items: [{ businessDate: '2026-10-10', availability: 'NOT_GUARANTEED', displayState: 'LENT' }] } })
    await flushPromises()
    pending[0].resolve({ code: 'MLS-CIRC-S006', message: 'ok', data: { items: [{ businessDate: '2026-09-18', availability: 'GUARANTEED', displayState: 'AVAILABLE' }] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="calendar-month"]').text()).toBe('2026年10月')
    expect(wrapper.get('[data-date="2026-10-10"]').text()).toContain('已借出')
  })

  it('shows query failures and leaves colours unset', async () => {
    installMockTransport({ fixtures: { getReservationCalendar: { status: 400, code: 'MLS-CIRC-001', message: '日期无效', data: {} } } })
    const wrapper = await mountView(ReservationCalendarView, { props: { titleId: '2001' } })
    await flushPromises()
    expect(wrapper.get('[data-testid="error-alert"]').text()).toContain('MLS-CIRC-001')
    expect(wrapper.get('[data-date="2026-09-18"]').classes()).not.toContain('lent')
  })
})
