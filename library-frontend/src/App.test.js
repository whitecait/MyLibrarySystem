import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { createAppRouter } from './router'
import { login, logout } from './platform/auth/session'
import { setTransport } from './platform/http'
import sharedFixtures from './platform/http/mock/fixtures'
import App from './App.vue'

const mountedWrappers = new Set()

async function mountApp() {
  const router = createAppRouter({ history: createMemoryHistory() })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router, ElementPlus] } })
  mountedWrappers.add(wrapper)
  return wrapper
}

beforeEach(() => {
  logout()
  setTransport({
    mode: 'test',
    async request() {
      return { code: 'MLS-CAT-S001', message: 'ok', data: { items: [], nextCursor: null } }
    },
  })
})

afterEach(() => {
  for (const wrapper of mountedWrappers) wrapper.unmount()
  mountedWrappers.clear()
  logout()
  setTransport(null)
})

describe('application shell', () => {
  it('renders the shared shell and the reader home for an anonymous visitor', async () => {
    const wrapper = await mountApp()
    expect(wrapper.text()).toContain('找到下一本想读的书')
    expect(wrapper.find('[data-testid="app-nav"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="app-nav"]').findAll('a').map(link => link.text())).toEqual(['首页', '个人信息'])
    expect(wrapper.get('[data-testid="personal-info-link"]').attributes('href')).toBe('/login')
    expect(wrapper.find('[data-testid="login-link"]').exists()).toBe(true)
  })

  it('shows the actor role and logout control after login', async () => {
    login({ accessToken: 'opaque-token', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = await mountApp()
    expect(wrapper.find('[data-testid="logout-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="session-area"]').text()).toContain('READER')
  })

  it('keeps the librarian walk-in entry out of the reader navigation', async () => {
    login({ accessToken: 'opaque-token', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = await mountApp()
    expect(wrapper.find('.app-nav a[href="/walk-in"]').exists()).toBe(false)
  })

  it('shows the walk-in entry to librarians', async () => {
    login({ accessToken: 'opaque-token', actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const wrapper = await mountApp()
    expect(wrapper.find('.app-nav a[href="/walk-in"]').exists()).toBe(true)
  })
})

// Verify the actual router keeps the background component alive across modal routes.
describe('background and modal navigation', () => {
  it('keeps home results and filters while detail, calendar and login change inside the modal', async () => {
    const { flushPromises } = await import('@vue/test-utils')
    const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
    const calls = []
    const mock = createMockTransport({ fixtures: sharedFixtures })
    setTransport({
      mode: 'test',
      request(operationId, options) {
        calls.push(operationId)
        return mock.request(operationId, options)
      },
    })
    const wrapper = await mountApp()
    const router = wrapper.vm.$router
    await flushPromises()
    await wrapper.get('[data-testid="home-search-input"]').setValue('Database')
    await wrapper.get('[data-testid="home-search-form"]').trigger('submit')
    await flushPromises()
    const background = wrapper.get('.home').element
    const searches = calls.filter(id => id === 'searchTitles').length

    await router.push('/titles/2001')
    await flushPromises()
    expect(wrapper.get('[data-testid="title-detail-name"]').text()).toBe('Database Systems')
    expect(wrapper.get('.home').element).toBe(background)
    expect(wrapper.get('[data-testid="home-search-input"]').element.value).toBe('Database')
    expect(calls.filter(id => id === 'startAiSession')).toHaveLength(1)
    expect(wrapper.findAll('#home-ai-dialog')).toHaveLength(1)
    expect(wrapper.findAll('[data-testid="business-panel"]')).toHaveLength(1)

    await router.push('/titles/2001/calendar')
    await flushPromises()
    expect(wrapper.get('[data-testid="calendar-form"]').exists()).toBe(true)
    await router.push('/login')
    await flushPromises()
    expect(wrapper.get('.home').element).toBe(background)
    await wrapper.get('[data-testid="business-close"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/')
    expect(wrapper.get('.home').element).toBe(background)
    expect(calls.filter(id => id === 'searchTitles')).toHaveLength(searches)
  })

  it('provides a home background for a direct book link and preserves route authorization', async () => {
    const { flushPromises } = await import('@vue/test-utils')
    const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
    setTransport(createMockTransport({ fixtures: sharedFixtures }))
    const router = createAppRouter({ history: createMemoryHistory() })
    await router.push('/titles/2001')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router, ElementPlus] } })
    mountedWrappers.add(wrapper)
    await flushPromises()
    expect(wrapper.get('.home').exists()).toBe(true)
    expect(wrapper.get('[data-testid="title-detail-name"]').text()).toBe('Database Systems')
    await router.push('/admin/catalog')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(wrapper.find('[data-testid="admin-search-form"]').exists()).toBe(false)
    login({ accessToken: 'reader', actor: { accountId: '1001', role: 'READER' } })
    await router.push('/admin/catalog')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('forbidden')
    await router.push('/me/business')
    await flushPromises()
    await router.push('/titles/2001')
    await flushPromises()
    expect(wrapper.find('.my-business-view').exists()).toBe(true)
    logout()
    await flushPromises()
    expect(wrapper.find('.my-business-view').exists()).toBe(false)
    expect(wrapper.find('.home').exists()).toBe(true)
  })
})


it('reuses the floating assistant conversation and draft when a book automatically opens its right panel', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
  const calls = []
  const mock = createMockTransport({ fixtures: sharedFixtures })
  setTransport({ mode: 'test', request(operationId, options) { calls.push(operationId); return mock.request(operationId, options) } })
  const wrapper = await mountApp()
  const router = wrapper.vm.$router
  await flushPromises()
  const home = wrapper.get('.home').element
  const entry = wrapper.get('[data-testid="home-assistant-trigger"]')
  const entryElement = entry.element
  expect(wrapper.findAll('[data-testid="home-assistant-trigger"]')).toHaveLength(1)
  expect(entry.attributes('aria-haspopup')).toBe('dialog')
  await wrapper.get('[data-testid="home-search-input"]').setValue('Database')
  await entry.trigger('click')
  await flushPromises()
  const panel = wrapper.get('#home-ai-dialog').element
  await wrapper.get('[data-testid="ai-input"]').setValue('保留这条尚未发送的问题')
  await wrapper.get('[aria-label="关闭 AI 助手"]').trigger('click')
  await router.push('/titles/2001')
  await flushPromises()
  expect(wrapper.get('#home-ai-dialog').element).toBe(panel)
  expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('保留这条尚未发送的问题')
  expect(entry.attributes('aria-expanded')).toBe('true')
  expect(entry.element.style.display).toBe('none')
  expect(wrapper.findAll('[data-testid="home-assistant-trigger"]')).toHaveLength(1)
  expect(wrapper.find('.modal-assistant-reopen').exists()).toBe(false)
  expect(wrapper.get('[data-testid="home-search-input"]').element.value).toBe('Database')
  expect(wrapper.get('.home').element).toBe(home)
  expect(router.currentRoute.value.fullPath).toBe('/titles/2001')
  expect(calls.filter(id => id === 'startAiSession')).toHaveLength(1)
  expect(wrapper.find('dialog#home-ai-dialog').exists()).toBe(false)
  expect(wrapper.get('#home-ai-dialog').element.parentElement).not.toBe(wrapper.get('[data-testid="business-panel"]').element)

  await wrapper.get('[aria-label="关闭 AI 助手"]').trigger('click')
  expect(wrapper.find('[data-testid="business-panel"]').exists()).toBe(true)
  await flushPromises()
  expect(wrapper.get('[data-testid="home-assistant-trigger"]').element).toBe(entryElement)
  expect(entry.element.style.display).not.toBe('none')
  expect(entry.element.closest('.modal-workspace')).not.toBeNull()
  expect(wrapper.findAll('[data-testid="home-assistant-trigger"]')).toHaveLength(1)
  await entry.trigger('click')
  await wrapper.get('[data-testid="business-close"]').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/')
  expect(wrapper.get('[data-testid="home-assistant-trigger"]').element).toBe(entryElement)
  expect(entry.element.closest('.modal-workspace')).toBeNull()
  expect(entry.element.style.display).not.toBe('none')
  await entry.trigger('click')
  await flushPromises()
  expect(wrapper.get('#home-ai-dialog').element).toBe(panel)
  expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('保留这条尚未发送的问题')
  expect(calls.filter(id => id === 'startAiSession')).toHaveLength(1)
  await router.push('/titles/2001')
  await flushPromises()
  const { ElDialog } = await import('element-plus')
  const backdrop = wrapper.get('.el-overlay-dialog')
  await backdrop.trigger('mousedown')
  await backdrop.trigger('mouseup')
  await backdrop.trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/')
  expect(wrapper.getComponent(ElDialog).props('modelValue')).toBe(false)
  expect(wrapper.get('.el-overlay').element.style.display).toBe('none')
  expect(entry.attributes('aria-expanded')).toBe('false')
  expect(entry.element.style.display).not.toBe('none')
  await entry.trigger('click')
  await flushPromises()
  expect(wrapper.get('#home-ai-dialog').element).toBe(panel)
  expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('保留这条尚未发送的问题')
})

it.each([
  ['paired gap', '.modal-workspace__panels', false],
  ['collapsed assistant space', '.modal-workspace__assistant', true],
  ['empty space beside the standalone assistant', '.modal-workspace__panels', null],
])('dismisses the complete workspace on an outside click: %s', async (_label, selector, collapse) => {
  const { flushPromises } = await import('@vue/test-utils')
  const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
  setTransport(createMockTransport({ fixtures: sharedFixtures }))
  const wrapper = await mountApp()
  const router = wrapper.vm.$router
  await flushPromises()
  const background = wrapper.get('.home').element
  if (collapse === null) await wrapper.get('[data-testid="home-assistant-trigger"]').trigger('click')
  else await router.push('/titles/2001')
  await flushPromises()
  if (collapse) await wrapper.get('[aria-label="关闭 AI 助手"]').trigger('click')

  const outside = wrapper.get(selector)
  await outside.trigger('mousedown')
  await outside.trigger('mouseup')
  await outside.trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/')
  expect(wrapper.get('.el-overlay').element.style.display).toBe('none')
  expect(wrapper.get('[data-testid="home-assistant-trigger"]').element.style.display).not.toBe('none')
  expect(wrapper.get('.home').element).toBe(background)
})

it('keeps forms open for inside clicks and drags from a card into empty space', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
  setTransport(createMockTransport({ fixtures: sharedFixtures }))
  const wrapper = await mountApp()
  const router = wrapper.vm.$router
  await router.push('/titles/2001')
  await flushPromises()
  const input = wrapper.get('[data-testid="ai-input"]')
  await input.setValue('保留草稿')
  await input.trigger('mousedown')
  await input.trigger('mouseup')
  await input.trigger('click')
  const gap = wrapper.get('.modal-workspace__panels')
  await wrapper.get('[data-testid="title-detail-name"]').trigger('mousedown')
  await gap.trigger('mouseup')
  await gap.trigger('click')
  await gap.trigger('mousedown')
  await input.trigger('mouseup')
  await gap.trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/titles/2001')
  expect(wrapper.get('.el-overlay').element.style.display).not.toBe('none')
  expect(input.element.value).toBe('保留草稿')
})
