import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ReservePanel from './ReservePanel.vue'

afterEach(() => setTransport(null))

async function mountPanel() {
  const wrapper = await mountView(ReservePanel, { props: { titleId: '2001' } })
  await flushPromises()
  return wrapper
}

async function submitReservation(wrapper) {
  await wrapper.find('[data-testid="reserve-date"]').setValue('2026-09-15')
  await wrapper.find('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
}

describe('ReservePanel', () => {
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
    let call = 0
    installMockTransport({
      fixtures: {
        reserveTitle: {
          code: 'MLS-CIRC-S001',
          message: 'ok',
          get data() {
            call += 1
            return call === 1
              ? { txId: null, outcome: 'WAITLIST_PENDING_CONFIRMATION' }
              : { txId: '4004', outcome: 'CONFIRMED' }
          },
        },
      },
    })
    const wrapper = await mountPanel()
    await submitReservation(wrapper)

    const pending = wrapper.find('[data-testid="reserve-waitlist-pending"]')
    expect(pending.exists()).toBe(true)
    expect(pending.text()).toContain('不产生业务事实')
    expect(wrapper.find('[data-testid="reserve-confirm"]').exists()).toBe(true)

    await wrapper.find('[data-testid="reserve-confirm"]').trigger('click')
    await flushPromises()

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
