import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import TitleBorrowView from './TitleBorrowView.vue'

afterEach(() => setTransport(null))

describe('TitleBorrowView', () => {
  it('loads the title calendar directly without clearing the selected reservation fields', async () => {
    const calls = []
    const mock = installMockTransport({ fixtures: sharedFixtures })
    setTransport({ mode: 'mock', request(operationId, options) { calls.push({ operationId, options }); return mock.request(operationId, options) } })
    const wrapper = await mountView(TitleBorrowView, { props: { titleId: '2001' } })
    await flushPromises()
    expect(wrapper.get('[data-testid="borrow-title-name"]').text()).toBe('Database Systems')
    expect(wrapper.get('[data-testid="reserve-form"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="calendar-grid"]').exists()).toBe(true)
    expect(calls.filter(call => call.operationId === 'getReservationCalendar')).toHaveLength(1)
    expect(calls.find(call => call.operationId === 'getReservationCalendar').options.params).toEqual({ titleId: '2001' })
    await wrapper.get('[data-testid="reserve-date"]').setValue('2026-10-15')
    await wrapper.get('[data-testid="reserve-slot"]').setValue('16:00-17:00')
    await wrapper.get('[aria-label="下个月"]').trigger('click')
    await flushPromises()
    expect(calls.filter(call => call.operationId === 'getReservationCalendar')).toHaveLength(2)
    expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-15')
    expect(wrapper.get('[data-testid="reserve-slot"]').element.value).toBe('16:00-17:00')
    expect(calls.some(call => call.operationId === 'reserveTitle')).toBe(false)
  })

  it('hides both tools for a title that cannot be found', async () => {
    installMockTransport({ fixtures: { getTitle: { status: 404, code: 'MLS-CAT-002', message: 'not found', data: {} } } })
    const wrapper = await mountView(TitleBorrowView, { props: { titleId: '9999' } })
    await flushPromises()
    expect(wrapper.get('[data-testid="borrow-title-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reserve-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="calendar-grid"]').exists()).toBe(false)
  })
})
