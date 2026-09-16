import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ReadingView from './ReadingView.vue'

afterEach(() => setTransport(null))

describe('ReadingView', () => {
  it('renders the reading pages and offers pagination while a cursor exists', async () => {
    installMockTransport({
      fixtures: {
        getReadingContent: {
          code: 'MLS-CAT-S003',
          message: 'ok',
          data: { contentType: 'text/plain', pages: [{ pageNo: 1, text: '第一章' }], nextCursor: 'c2' },
        },
      },
    })
    const wrapper = await mountView(ReadingView, { props: { titleId: '2001' } })
    await flushPromises()

    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.find('[data-testid="reading-next"]').exists()).toBe(true)

    await wrapper.find('[data-testid="reading-next"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="reading-pages"]').exists()).toBe(true)
  })

  it('marks the end of the content when no cursor remains', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(ReadingView, { props: { titleId: '2001' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="reading-next"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reading-end"]').exists()).toBe(true)
  })

  it('presents missing reading content explicitly', async () => {
    installMockTransport({
      fixtures: {
        getReadingContent: { status: 404, code: 'MLS-CAT-005', message: 'no public reading content', data: {} },
      },
    })
    const wrapper = await mountView(ReadingView, { props: { titleId: '2003' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="reading-not-found"]').exists()).toBe(true)
  })
})
