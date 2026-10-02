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
    const callsCaptured = []
    const { setTransport } = await import('@/platform/http')
    const { createMockTransport } = await import('@/platform/http/mock/mockAdapter')
    const mock = createMockTransport({ fixtures: {
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
    } })
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        callsCaptured.push({ operationId, options })
        return mock.request(operationId, options)
      },
    })
    const wrapper = await mountView(MyBusinessView)
    await flushPromises()

    await wrapper.find('[data-testid="cancel-4003"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(true)
    expect(callsCaptured.find((call) => call.operationId === 'cancelReservation').options.body)
      .toEqual({ reason: '读者自助取消预约' })
  })

  it('shows renewal only for loans that are still lent', async () => {
    const wrapper = await mountBusiness({
      getMyBusiness: {
        code: 'MLS-QUERY-S002',
        message: 'ok',
        data: { items: [
          { kind: 'LOAN', id: '4101', status: 'LENT' },
          { kind: 'LOAN', id: '4102', status: 'RETURNED' },
          { kind: 'LOAN', id: '4103', status: 'RETURN_ABNORMAL' },
        ], nextCursor: null },
      },
    })

    expect(wrapper.find('[data-testid="renew-4101"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="renew-4102"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="renew-4103"]').exists()).toBe(false)
  })

  it('keeps cancellation available only while a reservation can still be cancelled', async () => {
    const wrapper = await mountBusiness({
      getMyBusiness: {
        code: 'MLS-QUERY-S002',
        message: 'ok',
        data: { items: [
          { kind: 'RESERVATION', id: '4110', status: 'AWAIT' },
          { kind: 'RESERVATION', id: '4111', status: 'CONFIRMED' },
          { kind: 'RESERVATION', id: '4112', status: 'WAITLIST' },
          { kind: 'RESERVATION', id: '4113', status: 'MANUAL_HANDLING' },
          { kind: 'RESERVATION', id: '4114', status: 'NOT_BORROWED' },
        ], nextCursor: null },
      },
    })

    for (const txId of ['4110', '4111', '4112', '4113']) {
      expect(wrapper.find(`[data-testid="cancel-${txId}"]`).exists()).toBe(true)
    }
    expect(wrapper.find('[data-testid="cancel-4114"]').exists()).toBe(false)
  })

  it('prefills the renew modal from an AI L2 card and submits with the draft id', async () => {
    const calls = []
    const { setTransport } = await import('@/platform/http')
    const { createMockTransport } = await import('@/platform/http/mock/mockAdapter')
    const mock = createMockTransport({ fixtures: sharedFixtures })
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return mock.request(operationId, options)
      },
    })
    const wrapper = await mountView(MyBusinessView)
    await flushPromises()

    await wrapper.find('[data-testid="ai-input"]').setValue('帮我预填续借')
    await wrapper.find('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()

    await wrapper.find('[data-testid="ai-action-open-7201"]').trigger('click')
    const modal = wrapper.find('[data-testid="renew-modal"]')
    expect(modal.exists()).toBe(true)
    expect(wrapper.find('[data-testid="renew-modal-tx-id"]').element.value).toBe('4001')
    expect(wrapper.find('[data-testid="renew-draft-note"]').text()).toContain('7201')

    await wrapper.find('[data-testid="renew-modal-submit"]').trigger('click')
    await flushPromises()

    const renewCall = calls.find((call) => call.operationId === 'renewLoan')
    expect(renewCall.options.params).toEqual({ txId: '4001' })
    expect(renewCall.options.headers).toEqual({ 'Ai-Draft-Id': '7201' })
    expect(wrapper.find('[data-testid="renew-result"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="renew-modal"]').isVisible()).toBe(false)
  })

  it('shows a server renewal rejection inside the AI-prefilled modal', async () => {
    const wrapper = await mountBusiness({
      ...sharedFixtures,
      renewLoan: { status: 422, code: 'MLS-CIRC-012', message: '已被预约，无法续借', data: {} },
    })

    await wrapper.find('[data-testid="ai-input"]').setValue('帮我预填续借')
    await wrapper.find('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()
    await wrapper.find('[data-testid="ai-action-open-7201"]').trigger('click')
    await wrapper.find('[data-testid="renew-modal-submit"]').trigger('click')
    await flushPromises()

    const modal = wrapper.find('[data-testid="renew-modal"]')
    const modalError = modal.find('[data-testid="error-alert"]')
    expect(modalError.exists()).toBe(true)
    expect(modalError.text()).toContain('MLS-CIRC-012')
    expect(modal.text()).toContain('已被预约，无法续借')
    expect(modal.isVisible()).toBe(true)
    expect(wrapper.find('[data-testid="renew-result"]').exists()).toBe(false)

    await wrapper.find('[data-testid="renew-modal-cancel"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="ai-action-open-7201"]').trigger('click')

    expect(wrapper.find('[data-testid="renew-modal"]').find('[data-testid="error-alert"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="renew-modal"]').isVisible()).toBe(true)
  })

  it('sends no business request when the prefilled modal is cancelled', async () => {
    const calls = []
    const { setTransport } = await import('@/platform/http')
    const { createMockTransport } = await import('@/platform/http/mock/mockAdapter')
    const mock = createMockTransport({ fixtures: sharedFixtures })
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return mock.request(operationId, options)
      },
    })
    const wrapper = await mountView(MyBusinessView)
    await flushPromises()

    await wrapper.find('[data-testid="ai-input"]').setValue('帮我预填续借')
    await wrapper.find('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()
    await wrapper.find('[data-testid="ai-action-open-7201"]').trigger('click')
    await wrapper.find('[data-testid="renew-modal-cancel"]').trigger('click')
    await flushPromises()

    expect(calls.filter((call) => call.operationId === 'renewLoan')).toHaveLength(0)
    expect(wrapper.find('[data-testid="renew-modal"]').isVisible()).toBe(false)
  })
})
