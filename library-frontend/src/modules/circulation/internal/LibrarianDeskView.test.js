import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import LibrarianDeskView from './LibrarianDeskView.vue'

afterEach(() => setTransport(null))

async function mountDesk({ scenarioId, fixtures } = {}) {
  installMockTransport({ scenarioId, fixtures })
  const wrapper = await mountView(LibrarianDeskView)
  await flushPromises()
  return wrapper
}

describe('LibrarianDeskView', () => {
  it('records an intact return and points to the formed fine', async () => {
    const wrapper = await mountDesk({ scenarioId: 'RETURN-WITH-FINE' })

    await wrapper.get('[data-testid="desk-return-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="return-copy-id"]').setValue('3001')
    await wrapper.find('[data-testid="return-form"]').trigger('submit')
    await flushPromises()

    const returned = wrapper.find('[data-testid="return-returned"]')
    expect(returned.exists()).toBe(true)
    expect(returned.text()).toContain('4001')
    expect(returned.text()).toContain('5001')
    expect(returned.text()).toContain('罚款')
  })

  it('routes abnormal returns to manual handling presentation', async () => {
    const wrapper = await mountDesk({
      fixtures: {
        returnCopy: {
          code: 'MLS-CIRC-S003',
          message: 'returned',
          data: { txId: '4001', outcome: 'RETURN_ABNORMAL' },
        },
      },
    })

    await wrapper.get('[data-testid="desk-return-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="return-copy-id"]').setValue('3001')
    await wrapper.find('[data-testid="return-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="return-abnormal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="return-returned"]').exists()).toBe(false)
  })

  it('confirms a pickup claim', async () => {
    const wrapper = await mountDesk({ fixtures: sharedFixtures })

    await wrapper.get('[data-testid="desk-claim-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="claim-tx-id"]').setValue('4002')
    await wrapper.find('[data-testid="claim-copy-id"]').setValue('3001')
    await wrapper.find('[data-testid="claim-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="claim-success"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('4001')
  })

  it('shows a non-date pickup routed to manual handling without claiming it completed', async () => {
    const wrapper = await mountDesk({
      fixtures: {
        claimPickup: {
          code: 'MLS-CIRC-S004',
          message: 'manual handling',
          data: { txId: '4002', outcome: 'MANUAL_HANDLING', status: 'MANUAL_HANDLING' },
        },
      },
    })

    await wrapper.get('[data-testid="desk-claim-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="claim-tx-id"]').setValue('4002')
    await wrapper.find('[data-testid="claim-copy-id"]').setValue('3001')
    await wrapper.find('[data-testid="claim-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="claim-manual"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="claim-success"]').exists()).toBe(false)
  })

  it('applies a manual action with its reason', async () => {
    const wrapper = await mountDesk({ fixtures: sharedFixtures })

    await wrapper.get('[data-testid="desk-manual-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="manual-tx-id"]').setValue('4003')
    await wrapper.find('[data-testid="manual-action"]').setValue('REJECT')
    await wrapper.find('[data-testid="manual-reason"]').setValue('无法确认入库')
    await wrapper.find('[data-testid="manual-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="manual-success"]').exists()).toBe(true)
  })

  it('surfaces duplicate-return conflicts without fake success', async () => {
    const wrapper = await mountDesk({
      fixtures: {
        returnCopy: { status: 422, code: 'MLS-CIRC-006', message: '重复归还', data: {} },
      },
    })

    await wrapper.get('[data-testid="desk-return-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="return-copy-id"]').setValue('3001')
    await wrapper.find('[data-testid="return-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="return-returned"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="return-abnormal"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-CIRC-006')
  })
})

it('sends one real HTTP return request only after submission inside the modal', async () => {
  const { startLoopbackHttp } = await import('@/platform/http/testing/loopback')
  const receiver = await startLoopbackHttp({
    getToken: () => 'local-modal-test-token',
    responseEnvelope: { code: 'MLS-CIRC-S003', message: 'returned', data: { txId: '4001', outcome: 'RETURNED', fineId: null } },
  })
  setTransport(receiver.transport)
  let wrapper
  try {
    wrapper = await mountView(LibrarianDeskView)
    await flushPromises()
    await wrapper.get('[data-testid="desk-return-open"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="return-copy-id"]').setValue('3001')
    expect(receiver.requests).toHaveLength(0)
    await wrapper.get('[data-testid="return-form"]').trigger('submit')
    const { vi } = await import('vitest')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="return-returned"]').exists()).toBe(true))
    expect(receiver.requests).toHaveLength(1)
    expect(receiver.requests[0]).toMatchObject({ method: 'POST', body: { copyId: '3001', condition: 'INTACT' } })
    expect(receiver.requests[0].headers.authorization).toBe('Bearer local-modal-test-token')
    expect(receiver.requests[0].headers['idempotency-key']).toBeTruthy()
  } finally {
    wrapper?.unmount()
    await receiver.close()
  }
})
