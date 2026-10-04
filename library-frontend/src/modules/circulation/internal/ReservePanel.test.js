import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import { installMockTransport, mountView } from '../../../tests/helpers'
import { formatPickupSlot, PICKUP_SLOTS } from '../index'
import ReservePanel from './ReservePanel.vue'

afterEach(() => setTransport(null))

async function mountPanel() {
  const wrapper = await mountView(ReservePanel, { props: { titleId: '2001' } })
  await flushPromises()
  return wrapper
}

async function submitReservation(wrapper) {
  await wrapper.find('[data-testid="reserve-date"]').setValue('2026-09-15')
  await wrapper.find('[data-testid="reserve-slot"]').setValue('14:00-14:30')
  await wrapper.find('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
}

describe('ReservePanel', () => {
  it('renders every half-hour interval through the final 23:30-24:00 interval', async () => {
    expect(PICKUP_SLOTS).toHaveLength(48)
    expect(PICKUP_SLOTS[0]).toBe('00:00-00:30')
    expect(PICKUP_SLOTS).toContain('14:00-14:30')
    expect(PICKUP_SLOTS[47]).toBe('23:30-24:00')
    expect(new Set(PICKUP_SLOTS).size).toBe(48)

    const wrapper = await mountPanel()
    const options = wrapper.find('[data-testid="reserve-slot"]').findAll('option')
    expect(options.slice(1).map((option) => option.element.value)).toEqual(PICKUP_SLOTS)
  })

  it('keeps legacy slot meanings and exact half-hour values distinct', () => {
    expect(formatPickupSlot('14:00-14:30')).toBe('14:00-14:30')
    expect(formatPickupSlot('AM')).toBe('上午（AM）')
    expect(formatPickupSlot('PM')).toBe('下午（PM）')
    expect(formatPickupSlot(null)).toBe('未知')
  })

  it('does not submit until a concrete interval is selected', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-CIRC-S005', message: 'ok', data: { txId: '4002', outcome: 'CONFIRMED' } }
      },
    })
    const wrapper = await mountPanel()
    await wrapper.find('[data-testid="reserve-date"]').setValue('2026-09-15')

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
      pickupSlot: '14:00-14:30',
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
      pickupSlot: '14:00-14:30',
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
      pickupSlot: '14:00-14:30',
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
