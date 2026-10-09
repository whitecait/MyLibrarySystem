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
    expect(wrapper.find('[data-testid="title-borrow-entry"]').text()).toBe('借阅')
    expect(wrapper.find('[data-testid="reserve-form"]').exists()).toBe(false)
  })

  it('labels missing optional metadata while retaining zero price and reading/borrowing navigation targets', async () => {
    installMockTransport({
      fixtures: {
        getTitle: {
          code: 'MLS-CAT-S002',
          message: 'ok',
          data: { ...sharedFixtures.getTitle.data, isbn: null, publisher: null, publishYear: null, priceCents: 0 },
        },
      },
    })
    const wrapper = await mountView(TitleDetailView, { props: { titleId: '2001' } })
    await flushPromises()
    const rows = wrapper.find('[data-testid="title-metadata"]').findAll('div')
    const values = Object.fromEntries(rows.map(row => [row.find('dt').text(), row.find('dd').text()]))
    expect(values).toMatchObject({ ISBN: '未提供', 出版社: '未提供', 出版年: '未提供', 定价: '0.00 元' })
    expect(wrapper.find('[data-testid="title-reading-entry"]').attributes('href')).toBe('/titles/2001/read')
    expect(wrapper.find('[data-testid="title-borrow-entry"]').attributes('href')).toBe('/titles/2001/borrow')
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
