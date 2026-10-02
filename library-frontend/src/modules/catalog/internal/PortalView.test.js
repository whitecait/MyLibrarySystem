import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import PortalView from './PortalView.vue'

afterEach(() => setTransport(null))

async function mountPortal() {
  const wrapper = await mountView(PortalView)
  await flushPromises()
  return wrapper
}

describe('PortalView', () => {
  it('replays the approved public search scenario', async () => {
    installMockTransport({ scenarioId: 'CATALOG-PUBLIC-SEARCH' })
    const wrapper = await mountPortal()

    await wrapper.find('[data-testid="portal-search-form"]').trigger('submit')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="portal-results"] tbody tr')
    expect(rows).toHaveLength(1)
    expect(wrapper.text()).toContain('Database Systems')
    expect(wrapper.text()).toContain('可借')
    expect(wrapper.find('[data-testid="portal-title-link-2001"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="portal-load-more"]').exists()).toBe(false)
  })

  it('shows the empty state when nothing matches', async () => {
    installMockTransport({
      fixtures: {
        searchTitles: { code: 'MLS-CAT-S001', message: 'ok', data: { items: [], nextCursor: null } },
      },
    })
    const wrapper = await mountPortal()

    await wrapper.find('[data-testid="portal-search-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="portal-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="portal-results"]').exists()).toBe(false)
  })

  it('presents system failures from the mock', async () => {
    installMockTransport({
      fixtures: {
        searchTitles: { status: 500, code: 'MLS-CAT-001', message: 'system failure', data: {} },
      },
    })
    const wrapper = await mountPortal()

    await wrapper.find('[data-testid="portal-search-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="portal-results"]').exists()).toBe(false)
  })

  it('appends more rows when a next cursor exists', async () => {
    let call = 0
    installMockTransport({
      fixtures: {
        searchTitles: {
          code: 'MLS-CAT-S001',
          message: 'ok',
          get data() {
            call += 1
            return call === 1
              ? { items: [{ titleId: '2001', title: 'A', availability: 'AVAILABLE' }], nextCursor: 'c2' }
              : { items: [{ titleId: '2002', title: 'B', availability: 'UNKNOWN' }], nextCursor: null }
          },
        },
      },
    })
    const wrapper = await mountPortal()

    await wrapper.find('[data-testid="portal-search-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="portal-load-more"]').exists()).toBe(true)

    await wrapper.find('[data-testid="portal-load-more"]').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="portal-results"] tbody tr')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('B')
  })

  it('filters by availability and preserves the filter when loading another page', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        const items = calls.length === 1
          ? [{ titleId: '2001', title: 'Available title', availability: 'AVAILABLE' }]
          : [{ titleId: '2002', title: 'Another available title', availability: 'AVAILABLE' }]
        return {
          code: 'MLS-CAT-S001',
          message: 'ok',
          data: { items, nextCursor: calls.length === 1 ? 'c2' : null },
        }
      },
    })
    const wrapper = await mountPortal()

    expect(wrapper.find('[data-testid="portal-availability-filter"]').exists()).toBe(true)
    await wrapper.find('[data-testid="portal-availability-filter"]').setValue('AVAILABLE')
    await wrapper.find('[data-testid="portal-search-form"]').trigger('submit')
    await flushPromises()
    expect(calls[0].options.query.availability).toBe('AVAILABLE')

    await wrapper.find('[data-testid="portal-load-more"]').trigger('click')
    await flushPromises()
    expect(calls[1].options.query).toMatchObject({ availability: 'AVAILABLE', cursor: 'c2' })
    expect(wrapper.findAll('[data-testid="portal-results"] tbody tr')).toHaveLength(2)
  })

  it('keeps the shared fixture registry contract-shaped', () => {
    expect(sharedFixtures.listAccounts.data.items).toHaveLength(2)
  })
})
