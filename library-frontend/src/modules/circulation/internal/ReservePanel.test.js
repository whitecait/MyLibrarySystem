import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { setTransport } from '@/platform/http'
import { installMockTransport, mountView } from '../../../tests/helpers'
import { formatPickupSlot } from '../index'
import ReservePanel from './ReservePanel.vue'

afterEach(() => { setTransport(null); vi.useRealTimers() })

async function mountPanel() {
  const wrapper = await mountView(ReservePanel, { props: { titleId: '2001' } })
  await flushPromises()
  return wrapper
}

async function submitReservation(wrapper) {
  await wrapper.find('[data-testid="reserve-date"]').setValue('2026-09-15')
  await wrapper.find('[data-testid="reserve-slot"]').setValue('13:00-14:30')
  await wrapper.find('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
}

describe('ReservePanel', () => {
  it('prefills tomorrow and the suggested block without making a reservation', async () => {
    vi.setSystemTime(new Date('2026-10-09T04:30:00Z'))
    const calls = []
    setTransport({ mode: 'mock', async request(operationId, options) {
      calls.push({ operationId, options })
      return { code: 'MLS-CIRC-S005', message: 'ok', data: { txId: '4002', outcome: 'CONFIRMED' } }
    } })
    const wrapper = await mountPanel()
    expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-10')
    expect(wrapper.get('[data-testid="reserve-slot"]').element.value).toBe('13:00-14:30')
    expect(calls).toHaveLength(0)
    await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
    await flushPromises()
    expect(calls[0].options.body).toMatchObject({ reservationDate: '2026-10-10', pickupSlot: '13:00-14:30', channel: 'ONLINE', confirmWaitlist: false })
  })

  it('offers five opening-hour pickup blocks and only online reservation', async () => {
    const wrapper = await mountPanel()
    const options = wrapper.find('[data-testid="reserve-slot"]').findAll('option')
    expect(options.slice(1).map(option => option.element.value)).toEqual([
      '09:00-10:30', '10:30-12:00', '13:00-14:30', '14:30-16:00', '16:00-17:00',
    ])
    expect(wrapper.find('[data-testid="reserve-channel"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('线上预约')
  })

  it('keeps legacy slot meanings and exact half-hour values distinct', () => {
    expect(formatPickupSlot('14:00-14:30')).toBe('14:00-14:30')
    expect(formatPickupSlot('AM')).toBe('上午（AM）')
    expect(formatPickupSlot('PM')).toBe('下午（PM）')
    expect(formatPickupSlot(null)).toBe('未知')
  })

  it('does not submit after the selected interval is cleared', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-CIRC-S005', message: 'ok', data: { txId: '4002', outcome: 'CONFIRMED' } }
      },
    })
    const wrapper = await mountPanel()
    await wrapper.find('[data-testid="reserve-slot"]').setValue('')

    await wrapper.find('[data-testid="reserve-form"]').trigger('submit')
    await flushPromises()

    expect(calls).toHaveLength(0)
    expect(wrapper.find('[data-testid="reserve-confirmed"]').exists()).toBe(false)
  })

  it('submits the selected interval and preserves reservation request fields', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-CIRC-S005', message: 'ok', data: { txId: '4002', outcome: 'CONFIRMED' } }
      },
    })
    const wrapper = await mountPanel()
    await submitReservation(wrapper)

    expect(calls).toHaveLength(1)
    expect(calls[0].operationId).toBe('reserveTitle')
    expect(calls[0].options.body).toMatchObject({
      titleId: '2001',
      reservationDate: '2026-09-15',
      pickupSlot: '13:00-14:30',
      channel: 'ONLINE',
      confirmWaitlist: false,
    })
  })

  it('shows a confirmed reservation with its transaction id', async () => {
    installMockTransport({
      fixtures: {
        reserveTitle: { code: 'MLS-CIRC-S005', message: 'confirmed', data: { txId: '4002', outcome: 'CONFIRMED' } },
      },
    })
    const wrapper = await mountPanel()
    await submitReservation(wrapper)

    const confirmed = wrapper.find('[data-testid="reserve-confirmed"]')
    expect(confirmed.exists()).toBe(true)
    expect(confirmed.text()).toContain('4002')
  })

  it('requires explicit confirmation before a waitlist reservation becomes a fact', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return {
          code: 'MLS-CIRC-S001',
          message: 'ok',
          data: calls.length === 1
            ? { txId: null, outcome: 'WAITLIST_PENDING_CONFIRMATION' }
            : { txId: '4004', outcome: 'CONFIRMED' },
        }
      },
    })
    const wrapper = await mountPanel()
    await submitReservation(wrapper)

    expect(calls).toHaveLength(1)
    expect(calls[0].options.body).toMatchObject({
      reservationDate: '2026-09-15',
      pickupSlot: '13:00-14:30',
      confirmWaitlist: false,
    })
    const pending = wrapper.find('[data-testid="reserve-waitlist-pending"]')
    expect(pending.exists()).toBe(true)
    expect(pending.text()).toContain('不产生业务事实')
    expect(wrapper.find('[data-testid="reserve-confirm"]').exists()).toBe(true)

    await wrapper.find('[data-testid="reserve-confirm"]').trigger('click')
    await flushPromises()

    expect(calls).toHaveLength(2)
    expect(calls[1].options.body).toMatchObject({
      reservationDate: '2026-09-15',
      pickupSlot: '13:00-14:30',
      channel: 'ONLINE',
      confirmWaitlist: true,
    })
    expect(wrapper.find('[data-testid="reserve-confirmed"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('4004')
  })

  it('presents a rejection with its reasons', async () => {
    installMockTransport({
      fixtures: {
        reserveTitle: {
          code: 'MLS-CIRC-S005',
          message: 'rejected',
          data: { txId: null, outcome: 'REJECTED', reasons: ['READER_SUSPENDED'] },
        },
      },
    })
    const wrapper = await mountPanel()
    await submitReservation(wrapper)

    const rejected = wrapper.find('[data-testid="reserve-rejected"]')
    expect(rejected.exists()).toBe(true)
    expect(rejected.text()).toContain('READER_SUSPENDED')
  })

  it('surfaces reservation rule rejections from the mock as typed failures', async () => {
    installMockTransport({
      fixtures: {
        reserveTitle: { status: 422, code: 'MLS-CIRC-011', message: '不具备预约资格', data: {} },
      },
    })
    const wrapper = await mountPanel()
    await submitReservation(wrapper)

    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-CIRC-011')
  })
})
