import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import { installMockTransport, mountView } from '../../../tests/helpers'
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

  it('carries the reader id for desk-channel batches', async () => {
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

    await wrapper.find('[data-testid="walkin-channel"]').setValue('DESK')
    await wrapper.find('[data-testid="walkin-reader-id"]').setValue('1001')
    await wrapper.find('[data-testid="walkin-title-0"]').setValue('2001')
    await wrapper.find('[data-testid="walkin-copy-0"]').setValue('3001')
    await wrapper.find('[data-testid="walkin-form"]').trigger('submit')
    await flushPromises()

    expect(captured.options.body.channel).toBe('DESK')
    expect(captured.options.body.readerId).toBe('1001')
    expect(captured.options.body.items).toEqual([{ titleId: '2001', copyId: '3001' }])
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
