import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import CopyAdminView from './CopyAdminView.vue'

afterEach(() => setTransport(null))

async function mountCopies(fixtures) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(CopyAdminView)
  await flushPromises()
  return wrapper
}

describe('CopyAdminView', () => {
  it('lists copies with their states', async () => {
    const wrapper = await mountCopies(sharedFixtures)

    await wrapper.find('[data-testid="copy-filter-title"]').setValue('2001')
    await wrapper.find('[data-testid="copy-filter-form"]').trigger('submit')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="copy-table"] tbody tr')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('C-001')
    expect(wrapper.text()).toContain('BORROWED')
  })

  it('registers a new copy for the filtered title', async () => {
    const wrapper = await mountCopies(sharedFixtures)

    await wrapper.find('[data-testid="copy-filter-title"]').setValue('2001')
    await wrapper.find('[data-testid="create-copy-no"]').setValue('C-003')
    await wrapper.find('[data-testid="create-copy-form"]').trigger('submit')
    await flushPromises()

    const success = wrapper.find('[data-testid="create-copy-success"]')
    expect(success.exists()).toBe(true)
    expect(success.text()).toContain('C-001')
    expect(success.text()).toContain('PENDING_SHELVING')
  })

  it('applies a state action with optimistic version', async () => {
    const wrapper = await mountCopies(sharedFixtures)

    await wrapper.find('[data-testid="copy-filter-form"]').trigger('submit')
    await flushPromises()
    await wrapper.find('[data-testid="copy-select-3001"]').trigger('click')
    await wrapper.find('[data-testid="copy-action"]').setValue('MAINTAIN')
    await wrapper.find('[data-testid="copy-action-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="copy-action-success"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('MAINTENANCE')
    expect(result.text()).toContain('3')
  })

  it('presents state conflicts on stale versions', async () => {
    const wrapper = await mountCopies({
      ...sharedFixtures,
      changeCopyState: { status: 409, code: 'MLS-CIRC-010', message: '副本状态冲突', data: {} },
    })

    await wrapper.find('[data-testid="copy-filter-form"]').trigger('submit')
    await flushPromises()
    await wrapper.find('[data-testid="copy-select-3001"]').trigger('click')
    await wrapper.find('[data-testid="copy-action-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="copy-action-success"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-CIRC-010')
  })
})
