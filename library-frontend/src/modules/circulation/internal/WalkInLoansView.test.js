import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { setTransport } from '@/platform/http'
import { startLoopbackHttp } from '@/platform/http/testing/loopback'
import { ElementPlus, createTestRouter, installMockTransport, mountView } from '../../../tests/helpers'
import WalkInLoansView from './WalkInLoansView.vue'

afterEach(() => setTransport(null))

describe('WalkInLoansView', () => {
  it('shows per-item mixed results from the approved scenario', async () => {
    installMockTransport({ scenarioId: 'WALKIN-MIXED-BATCH' })
    const wrapper = await mountView(WalkInLoansView)
    await flushPromises()

    await wrapper.find('[data-testid="walkin-title-0"]').setValue('2001')
    await wrapper.find('[data-testid="walkin-copy-0"]').setValue('3001')
    await wrapper.find('[data-testid="walkin-add"]').trigger('click')
    await wrapper.find('[data-testid="walkin-title-1"]').setValue('2002')
    await wrapper.find('[data-testid="walkin-copy-1"]').setValue('3002')
    await wrapper.find('[data-testid="walkin-form"]').trigger('submit')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="walkin-results"] tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('LENT')
    expect(rows[0].text()).toContain('4001')
    expect(rows[1].text()).toContain('REJECTED')
    expect(rows[1].text()).toContain('COPY_NOT_AVAILABLE')
  })

  it('always submits the librarian desk channel and carries the reader id', async () => {
    let captured = null
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        captured = { operationId, options }
        return { code: 'MLS-T-S001', message: 'ok', data: { items: [] } }
      },
    })
    const wrapper = await mountView(WalkInLoansView)
    await flushPromises()

    expect(wrapper.find('[data-testid="walkin-channel"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('馆员在服务台逐册办理')
    await wrapper.find('[data-testid="walkin-reader-id"]').setValue('1001')
    await wrapper.find('[data-testid="walkin-title-0"]').setValue('2001')
    await wrapper.find('[data-testid="walkin-copy-0"]').setValue('3001')
    await wrapper.find('[data-testid="walkin-form"]').trigger('submit')
    await flushPromises()

    expect(captured.options.body.channel).toBe('DESK')
    expect(captured.options.body.readerId).toBe('1001')
    expect(captured.options.body.items).toEqual([{ titleId: '2001', copyId: '3001' }])
  })

  it('applies a librarian AI walk-in draft carried in the route query', async () => {
    const router = createTestRouter([{ path: '/walk-in', name: 'walk-in', component: WalkInLoansView }])
    await router.push('/walk-in?readerId=2&titleId=700001&copyId=800002')
    await router.isReady()
    const wrapper = await mountView(WalkInLoansView, { router })
    await flushPromises()

    expect(wrapper.find('[data-testid="walkin-reader-id"]').element.value).toBe('2')
    expect(wrapper.find('[data-testid="walkin-title-0"]').element.value).toBe('700001')
    expect(wrapper.find('[data-testid="walkin-copy-0"]').element.value).toBe('800002')
    expect(wrapper.find('[data-testid="walkin-results"]').exists()).toBe(false)
  })

  it('sends zero HTTP loan requests while applying a draft and exactly one when its submit button is clicked', async () => {
    const receiver = await startLoopbackHttp({
      getToken: () => 'p3-local-test-token',
      responseEnvelope: {
        code: 'MLS-CIRC-S001', message: 'ok',
        data: { items: [{ titleId: '700001', copyId: '800002', txId: '4001', outcome: 'LENT', reasons: [] }] },
      },
    })
    const { requests } = receiver
    setTransport(receiver.transport)
    let wrapper
    try {
      const router = createTestRouter([{ path: '/walk-in', name: 'walk-in', component: WalkInLoansView }])
      await router.push('/walk-in?readerId=2&titleId=700001&copyId=800002&aiDraftId=7217')
      await router.isReady()
      wrapper = mount(WalkInLoansView, { attachTo: document.body, global: { plugins: [ElementPlus, router] } })
      await flushPromises()
      expect(wrapper.find('[data-testid="walkin-reader-id"]').element.value).toBe('2')
      expect(requests).toHaveLength(0)

      await wrapper.find('[data-testid="walkin-submit"]').trigger('click')
      await vi.waitFor(() => expect(wrapper.find('[data-testid="walkin-results"]').exists()).toBe(true))
      expect(requests).toHaveLength(1)
      expect(requests[0]).toMatchObject({
        method: 'POST', path: '/api/v1/walk-in-loans',
        body: { readerId: '2', channel: 'DESK', items: [{ titleId: '700001', copyId: '800002' }] },
      })
      expect(requests[0].headers['ai-draft-id']).toBe('7217')
      expect(requests[0].headers['idempotency-key']).toBeTruthy()
      expect(wrapper.findAll('[data-testid="walkin-results"] tbody tr')).toHaveLength(1)
      expect(wrapper.find('[data-testid="walkin-results"]').text()).toContain('LENT')
    } finally {
      wrapper?.unmount()
      await receiver.close()
    }
  })

  it('omits incomplete rows from the submission', async () => {
    let captured = null
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        captured = { operationId, options }
        return { code: 'MLS-T-S001', message: 'ok', data: { items: [] } }
      },
    })
    const wrapper = await mountView(WalkInLoansView)
    await flushPromises()

    await wrapper.find('[data-testid="walkin-title-0"]').setValue('2001')
    await wrapper.find('[data-testid="walkin-form"]').trigger('submit')
    await flushPromises()

    expect(captured.options.body.items).toEqual([])
  })
})
