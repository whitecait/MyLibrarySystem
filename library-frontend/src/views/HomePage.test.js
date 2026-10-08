import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import { mountView } from '@/tests/helpers'
import { ref } from 'vue'
import { backgroundModalKey } from '@/router/presentation'
import HomePage from './HomePage.vue'

const mountedWrappers = new Set()

function success(items, nextCursor = null) {
  return { code: 'MLS-CAT-S001', message: 'ok', data: { items, nextCursor } }
}

function title(titleId, name, overrides = {}) {
  return {
    titleId,
    title: name,
    authors: ['图书作者'],
    category: '文学',
    availability: 'AVAILABLE',
    ...overrides,
  }
}

function setSearchTransport(responder, discoveryResponder = () => success([title('9001', '随机馆藏')])) {
  const calls = []
  calls.discovery = []
  setTransport({
    mode: 'test',
    async request(operationId, options = {}) {
      if (options.query?.limit === 100) {
        calls.discovery.push({ operationId, options })
        return discoveryResponder(operationId, options)
      }
      calls.push({ operationId, options })
      return responder(operationId, options, calls.length - 1)
    },
  })
  return calls
}

async function mountHome() {
  const wrapper = await mountView(HomePage)
  mountedWrappers.add(wrapper)
  await flushPromises()
  return wrapper
}

beforeEach(() => logout())

afterEach(() => {
  for (const wrapper of mountedWrappers) wrapper.unmount()
  mountedWrappers.clear()
  logout()
  setTransport(null)
  vi.unstubAllGlobals()
})

describe('HomePage', () => {
  it('loads four real catalog items and links to the detail page with its reservation form', async () => {
    const calls = setSearchTransport(() => success([
      title('2001', 'Database Systems', { authors: ['M. Stonebraker', 'A. Pavlo'] }),
    ]))
    const wrapper = await mountHome()

    expect(calls).toHaveLength(1)
    expect(calls[0].operationId).toBe('searchTitles')
    expect(calls[0].options.query).toEqual({
      q: undefined,
      category: undefined,
      availability: undefined,
      cursor: undefined,
      limit: 4,
    })
    expect(wrapper.text()).toContain('Database Systems')
    expect(wrapper.get('[data-testid="home-detail-2001"]').attributes('href')).toBe('/titles/2001')
    expect(wrapper.get('[data-testid="home-reserve-2001"]').attributes('href')).toBe('/titles/2001')
    expect(wrapper.text()).not.toContain('共 1 条结果')
  })

  it('applies keyword, category and availability buttons to every cursor page', async () => {
    const calls = setSearchTransport((_operationId, options) => {
      if (!options.query.category) return success([title('1000', '初始书目')])
      if (options.query.cursor) return success([title('2002', '下一页书目')])
      return success([title('2001', '匹配书目')], 'cursor-2')
    })
    const wrapper = await mountHome()

    await wrapper.get('[data-testid="home-search-input"]').setValue('真实书名')
    await wrapper.get('[data-testid="home-category-option"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="home-availability-option"]').trigger('click')
    await flushPromises()

    expect(calls.at(-1).options.query).toEqual({
      q: '真实书名', category: '文学', availability: 'AVAILABLE', cursor: undefined, limit: 4,
    })
    expect(wrapper.get('[data-testid="home-category-option"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="home-availability-option"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('[data-testid="home-card-2001"]').exists()).toBe(true)

    await wrapper.get('[data-testid="home-load-more"]').trigger('click')
    await flushPromises()
    expect(calls.at(-1).options.query).toEqual({
      q: '真实书名', category: '文学', availability: 'AVAILABLE', cursor: 'cursor-2', limit: 4,
    })
    expect(wrapper.text()).toContain('下一页书目')
    expect(wrapper.text()).toContain('匹配书目')
    expect(wrapper.find('[data-testid="home-feed-end"]').exists()).toBe(true)
    expect(wrapper.find('select, datalist').exists()).toBe(false)
  })

  it('displays all catalog choices and three distinct real title links beyond the visible feed', async () => {
    const calls = setSearchTransport(() => success([title('2001', '首屏书目')]), (_operation, options) => {
      if (!options.query.cursor) return success([
        title('3001', '随机图书一', { category: '历史' }),
        title('3002', '随机图书二', { category: '文学' }),
      ], 'discovery-page2')
      return success([title('3003', '随机图书三', { category: '科学', availability: 'UNAVAILABLE' })])
    })
    const wrapper = await mountHome()
    expect(calls.discovery.map(call => call.options.query.cursor)).toEqual([undefined, 'discovery-page2'])
    expect(wrapper.findAll('[data-testid="home-category-option"]').map(button => button.text()).sort())
      .toEqual(['历史', '文学', '科学'])
    expect(wrapper.findAll('[data-testid="home-availability-option"]').map(button => button.text()))
      .toEqual(['可借', '不可借'])
    expect(wrapper.findAll('[data-testid="home-suggestion"]').map(link => link.attributes('href')))
      .toEqual(['/titles/3001', '/titles/3002', '/titles/3003'])
    await wrapper.get('[data-testid="home-category-option"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="home-category-option"]')).toHaveLength(3)
    expect(calls.discovery).toHaveLength(2)
  })

  it('retries discovery separately while the feed remains usable', async () => {
    let fail = true
    const calls = setSearchTransport(() => success([title('2001', '仍可浏览')]), () => {
      if (fail) throw new Error('options unavailable')
      return success([title('3001', '恢复后的随机书')])
    })
    const wrapper = await mountHome()
    expect(wrapper.find('[data-testid="home-discovery-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-card-2001"]').exists()).toBe(true)
    fail = false
    await wrapper.get('[data-testid="home-discovery-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="home-discovery-error"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="home-suggestion"]').text()).toBe('恢复后的随机书')
    expect(calls).toHaveLength(1)
  })

  it('does not let an older filter response replace the latest results', async () => {
    const pending = []
    setSearchTransport((_operationId, options) => new Promise((resolve) => pending.push({ options, resolve })))
    const wrapper = await mountHome()

    await wrapper.get('[data-testid="home-search-input"]').setValue('新筛选')
    await wrapper.get('[data-testid="home-search-form"]').trigger('submit')
    expect(pending).toHaveLength(2)

    pending[1].resolve(success([title('2002', '新筛选结果')]))
    await flushPromises()
    pending[0].resolve(success([title('2001', '旧筛选结果')]))
    await flushPromises()

    expect(wrapper.text()).toContain('新筛选结果')
    expect(wrapper.text()).not.toContain('旧筛选结果')
  })

  it('updates reader shortcuts when the session changes on the same page', async () => {
    setSearchTransport(() => success([]))
    login({ accessToken: 'reader-token', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = await mountHome()

    expect(wrapper.find('[data-testid="reader-service-shortcuts"]').exists()).toBe(true)
    logout()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="reader-service-shortcuts"]').exists()).toBe(false)
  })

  it('keeps existing cards when the next cursor returns an empty final batch', async () => {
    setSearchTransport((_operationId, _options, index) => index === 0
      ? success([title('2001', '已有书目')], 'cursor-2') : success([]))
    const wrapper = await mountHome()
    await wrapper.get('[data-testid="home-load-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="home-empty"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('已有书目')
    expect(wrapper.find('[data-testid="home-feed-end"]').exists()).toBe(true)
  })

  it('retains existing cards and retries the same cursor after an append failure', async () => {
    let fail = true
    const calls = setSearchTransport((_operationId, options) => {
      if (!options.query.cursor) return success([title('2001', '已有书目')], 'cursor-2')
      if (fail) throw new Error('connection lost')
      return success([title('2002', '追加书目')])
    })
    const wrapper = await mountHome()
    await wrapper.get('[data-testid="home-load-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已有书目')
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
    fail = false
    await wrapper.get('[data-testid="home-load-more"]').trigger('click')
    await flushPromises()
    expect(calls.slice(1).map(call => call.options.query.cursor)).toEqual(['cursor-2', 'cursor-2'])
    expect(wrapper.text()).toContain('已有书目')
    expect(wrapper.text()).toContain('追加书目')
  })

  it('discards a pending append when filters change', async () => {
    let resolveAppend
    setSearchTransport((_operationId, options) => {
      if (options.query.cursor) return new Promise(resolve => { resolveAppend = resolve })
      return success([title(options.query.q ? '2003' : '2001', options.query.q ? '新筛选' : '旧筛选')], 'cursor-2')
    })
    const wrapper = await mountHome()
    await wrapper.get('[data-testid="home-load-more"]').trigger('click')
    await wrapper.get('[data-testid="home-search-input"]').setValue('新筛选')
    await wrapper.get('[data-testid="home-search-form"]').trigger('submit')
    await flushPromises()
    resolveAppend(success([title('2002', '过期追加')]))
    await flushPromises()
    expect(wrapper.text()).toContain('新筛选')
    expect(wrapper.text()).not.toContain('旧筛选')
    expect(wrapper.text()).not.toContain('过期追加')
  })

  it('loads automatically at the feed boundary without duplicate requests and pauses under a modal', async () => {
    let intersection
    let resolveAppend
    const disconnect = vi.fn()
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback) { intersection = callback }
      observe() {}
      disconnect() { disconnect() }
    })
    const calls = setSearchTransport((_operationId, options) => {
      if (!options.query.cursor) return success([title('2001', '首批')], 'cursor-2')
      return new Promise(resolve => { resolveAppend = resolve })
    })
    const blocked = ref(true)
    const wrapper = await mountView(HomePage, { provide: { [backgroundModalKey]: blocked } })
    mountedWrappers.add(wrapper)
    await flushPromises()
    intersection([{ isIntersecting: true }])
    expect(calls).toHaveLength(1)
    blocked.value = false
    await flushPromises()
    intersection([{ isIntersecting: true }])
    intersection([{ isIntersecting: true }])
    expect(calls).toHaveLength(2)
    resolveAppend(success([title('2002', '后续')]))
    await flushPromises()
    expect(wrapper.text()).toContain('首批')
    expect(wrapper.text()).toContain('后续')
    wrapper.unmount()
    mountedWrappers.delete(wrapper)
    expect(disconnect).toHaveBeenCalled()
  })

  it('shows the empty state and can retry a failed search', async () => {
    let shouldFail = false
    const calls = setSearchTransport(() => {
      if (shouldFail) throw new Error('network unavailable')
      return success([])
    })
    const wrapper = await mountHome()

    expect(wrapper.find('[data-testid="home-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-results"]').exists()).toBe(false)

    shouldFail = true
    await wrapper.get('[data-testid="home-search-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="home-retry"]').exists()).toBe(true)

    shouldFail = false
    await wrapper.get('[data-testid="home-retry"]').trigger('click')
    await flushPromises()
    expect(calls).toHaveLength(3)
    expect(wrapper.find('[data-testid="home-error-state"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="home-empty"]').exists()).toBe(true)
  })
})
