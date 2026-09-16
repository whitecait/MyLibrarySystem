import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import TitleDetailView from './TitleDetailView.vue'

afterEach(() => setTransport(null))

describe('TitleDetailView', () => {
  it('renders the title detail with availability and the reading entry', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(TitleDetailView, { props: { titleId: '2001' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="title-detail-name"]').text()).toBe('Database Systems')
    expect(wrapper.find('[data-testid="title-availability"]').text()).toBe('可借')
    expect(wrapper.find('[data-testid="title-reading-entry"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('128.00 元')
    expect(wrapper.text()).toContain('不构成借阅承诺')
  })

  it('hides the reading entry when the title has no reading content', async () => {
    installMockTransport({
      fixtures: {
        getTitle: {
          code: 'MLS-CAT-S002',
          message: 'ok',
          data: {
            titleId: '2003',
            title: 'Paper Book',
            authors: ['X'],
            category: '综合',
            availability: 'UNAVAILABLE',
            isbn: 'isbn-x',
            publisher: null,
            publishYear: null,
            priceCents: 0,
            version: 1,
            hasReadingContent: false,
          },
        },
      },
    })
    const wrapper = await mountView(TitleDetailView, { props: { titleId: '2003' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="title-reading-entry"]').exists()).toBe(false)
  })

  it('presents invisible titles as not found', async () => {
    installMockTransport({
      fixtures: {
        getTitle: { status: 404, code: 'MLS-CAT-002', message: 'not found', data: {} },
      },
    })
    const wrapper = await mountView(TitleDetailView, { props: { titleId: '9999' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="title-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(false)
  })
})
