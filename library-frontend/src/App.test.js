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
  it('keeps home results, filters and assistant while navigating detail, borrowing and login', async () => {
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

    const assistant = wrapper.get('#home-ai-dialog').element
    await wrapper.get('[data-testid="ai-input"]').setValue('层级切换保留草稿')
    await wrapper.get('[data-testid="title-borrow-entry"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
    expect(wrapper.get('[data-testid="calendar-grid"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="reserve-form"]').exists()).toBe(true)
    const breadcrumb = wrapper.get('[data-testid="business-breadcrumbs"]')
    expect(breadcrumb.get('a').attributes('href')).toBe('/titles/2001')
    expect(breadcrumb.get('[aria-current="page"]').text()).toBe('借阅')
    expect(wrapper.get('#home-ai-dialog').element).toBe(assistant)
    expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('层级切换保留草稿')
    await breadcrumb.get('a').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/titles/2001')
    expect(wrapper.find('[data-testid="reserve-form"]').exists()).toBe(false)
    await router.push('/titles/2001/calendar')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
    expect(wrapper.get('[data-testid="calendar-grid"]').exists()).toBe(true)
    expect(calls.filter(id => id === 'startAiSession')).toHaveLength(1)
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

it('shows the correct parent for direct borrowing and reading routes', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
  setTransport(createMockTransport({ fixtures: sharedFixtures }))
  const router = createAppRouter({ history: createMemoryHistory() })
  await router.push('/titles/2001/borrow')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router, ElementPlus] } })
  mountedWrappers.add(wrapper)
  await flushPromises()
  expect(wrapper.get('[data-testid="business-breadcrumbs"] a').attributes('href')).toBe('/titles/2001')
  await router.push('/titles/2001/read')
  await flushPromises()
  expect(wrapper.get('[data-testid="business-breadcrumbs"] [aria-current="page"]').text()).toBe('阅读')
  await wrapper.get('[data-testid="business-breadcrumbs"] a').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/titles/2001')
  await wrapper.get('[data-testid="business-close"]').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/')
})

async function mountBorrowingAuthentication({ expired = false } = {}) {
  const { createMockTransport } = await import('./platform/http/mock/mockAdapter')
  const { executeRequest } = await import('./platform/http/request')
  const { getToken } = await import('./platform/auth/session')
  const mock = createMockTransport({ fixtures: sharedFixtures })
  const calls = []
  let firstReservation = true
  if (expired) login({ accessToken: 'expired-token', actor: { accountId: '1001', role: 'READER' } })
  setTransport({ mode: 'test', request(operationId, options = {}) {
    if (!['createSession', 'reserveTitle', 'registerReader'].includes(operationId)) { calls.push({ operationId }); return mock.request(operationId, options) }
    return executeRequest({ operationId, getToken, ...options, fetchImpl: async (_url, init) => {
      const body = JSON.parse(init.body)
      calls.push({ operationId, body, token: init.headers.get('Authorization'), key: init.headers.get('Idempotency-Key') })
      let status = 200
      let envelope
      if (operationId === 'registerReader') {
        if (body.username === 'taken-reader') { status = 409; envelope = { code: 'MLS-ID-002', message: '用户名已被占用', data: {} } }
        else envelope = { code: 'MLS-ID-S001', message: 'ok', data: { accountId: '1001', role: 'READER', status: 'ACTIVE' } }
      } else if (operationId === 'createSession') {
        if (body.password === 'wrong') { status = 401; envelope = { code: 'MLS-ID-004', message: '用户名或密码不正确', data: {} } }
        else envelope = { code: 'MLS-ID-S002', message: 'ok', data: { accessToken: 'fresh-reader-token', actor: { accountId: '1001', role: 'READER' } } }
      } else if (expired && firstReservation) {
        firstReservation = false; status = 401; envelope = { code: 'MLS-ID-001', message: 'session expired', data: {} }
      } else envelope = { code: 'MLS-CIRC-S005', message: 'ok', data: { txId: '4002', outcome: 'CONFIRMED' } }
      return new Response(JSON.stringify(envelope), { status, headers: { 'Content-Type': 'application/json' } })
    } })
  } })
  const wrapper = await mountApp()
  await wrapper.vm.$router.push('/titles/2001/borrow')
  const { flushPromises } = await import('@vue/test-utils')
  await flushPromises()
  await wrapper.get('[data-testid="reserve-date"]').setValue('2026-10-20')
  await wrapper.get('[data-testid="reserve-slot"]').setValue('16:00-17:00')
  await wrapper.get('[data-testid="ai-input"]').setValue('保留这条未发送草稿')
  return { wrapper, calls }
}

it('keeps the borrowing form frozen under login and requires another explicit submit', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { onUnauthorized } = await import('./platform/http/request')
  const globalFailures = []
  const unsubscribe = onUnauthorized(failure => globalFailures.push(failure))
  try {
    const { wrapper, calls } = await mountBorrowingAuthentication()
    const borrowed = wrapper.get('.title-borrow-view').element
    const assistant = wrapper.getComponent({ name: 'AiAssistantView' }).vm.$.uid
    const titleReads = calls.filter(call => call.operationId === 'getTitle').length
    await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
    await flushPromises()
    expect(calls.filter(call => call.operationId === 'reserveTitle')).toHaveLength(0)
    expect(wrapper.get('.app-shell').attributes('inert')).toBeDefined()
    const form = wrapper.getComponent({ name: 'LoginForm' })
    await form.get('[data-testid="login-username"]').setValue('reader01')
    await form.get('[data-testid="login-password"]').setValue('wrong')
    await form.get('[data-testid="login-form"]').trigger('submit')
    await flushPromises()
    expect(form.get('[data-testid="error-alert"]').text()).toContain('MLS-ID-004')
    expect(globalFailures).toHaveLength(0)
    expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
    await form.get('[data-testid="login-password"]').setValue('password123')
    await form.get('[data-testid="login-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.app-shell').attributes('inert')).toBeUndefined()
    expect(wrapper.get('.title-borrow-view').element).toBe(borrowed)
    expect(wrapper.getComponent({ name: 'AiAssistantView' }).vm.$.uid).toBe(assistant)
    expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('保留这条未发送草稿')
    expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-20')
    expect(wrapper.get('[data-testid="reserve-slot"]').element.value).toBe('16:00-17:00')
    expect(calls.filter(call => call.operationId === 'getTitle')).toHaveLength(titleReads)
    expect(calls.filter(call => call.operationId === 'reserveTitle')).toHaveLength(0)
    const calendars = calls.filter(call => call.operationId === 'getReservationCalendar').length
    await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
    await flushPromises()
    const write = calls.find(call => call.operationId === 'reserveTitle')
    expect(write.token).toBe('Bearer fresh-reader-token')
    expect(write.body).toMatchObject({ titleId: '2001', reservationDate: '2026-10-20', pickupSlot: '16:00-17:00', channel: 'ONLINE' })
    expect(wrapper.get('[data-testid="reserve-confirmed"]').exists()).toBe(true)
    expect(calls.filter(call => call.operationId === 'getReservationCalendar')).toHaveLength(calendars + 1)
  } finally { unsubscribe() }
})

it('preserves the fields when an expired reservation session is reauthenticated without replaying the write', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { wrapper, calls } = await mountBorrowingAuthentication({ expired: true })
  const assistant = wrapper.getComponent({ name: 'AiAssistantView' }).vm.$.uid
  await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
  expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
  const form = wrapper.getComponent({ name: 'LoginForm' })
  await form.get('[data-testid="login-username"]').setValue('reader01')
  await form.get('[data-testid="login-password"]').setValue('password123')
  await form.get('[data-testid="login-form"]').trigger('submit')
  await flushPromises()
  expect(calls.filter(call => call.operationId === 'reserveTitle')).toHaveLength(1)
  expect(wrapper.getComponent({ name: 'AiAssistantView' }).vm.$.uid).toBe(assistant)
  expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('保留这条未发送草稿')
  expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-20')
  await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
  const writes = calls.filter(call => call.operationId === 'reserveTitle')
  expect(writes).toHaveLength(2)
  expect(writes[1].body).toEqual(writes[0].body)
  expect(writes[1].token).toBe('Bearer fresh-reader-token')
  expect(writes[1].key).not.toBe(writes[0].key)
})

it('cancels only the login layer while keeping the borrowing values and route', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { wrapper, calls } = await mountBorrowingAuthentication()
  await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
  const { ElDialog } = await import('element-plus')
  const dialog = wrapper.findAllComponents(ElDialog).find(component => component.props('title') === '登录后提交预约')
  dialog.vm.$emit('update:modelValue', false)
  await flushPromises()
  expect(wrapper.get('.app-shell').attributes('inert')).toBeUndefined()
  expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
  expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-20')
  expect(wrapper.get('[data-testid="reserve-slot"]').element.value).toBe('16:00-17:00')
  expect(calls.filter(call => call.operationId === 'reserveTitle')).toHaveLength(0)
})


it('registers inside the authentication layer and returns to login without replacing the borrowing draft', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { isAuthenticated } = await import('./platform/auth/session')
  const { wrapper, calls } = await mountBorrowingAuthentication()
  const borrowed = wrapper.get('.title-borrow-view').element
  const assistant = wrapper.getComponent({ name: 'AiAssistantView' }).vm.$.uid
  await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
  await clickAuthenticationEntry('register-entry')
  await flushPromises()
  expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
  expect(wrapper.get('.app-shell').attributes('inert')).toBeDefined()
  const registration = wrapper.getComponent({ name: 'RegisterForm' })
  await registration.get('[data-testid="register-username"]').setValue('new-reader')
  await registration.get('[data-testid="register-password"]').setValue('password123')
  await registration.get('[data-testid="register-display-name"]').setValue('新读者')
  await registration.get('[data-testid="register-form"]').trigger('submit')
  await flushPromises()
  expect(registration.get('[data-testid="register-success"]').text()).toContain('注册成功')
  expect(calls.filter(call => call.operationId === 'registerReader')).toHaveLength(1)
  expect(calls.filter(call => ['createSession', 'reserveTitle'].includes(call.operationId))).toHaveLength(0)
  expect(isAuthenticated()).toBe(false)
  await clickAuthenticationEntry('login-entry')
  await flushPromises()
  const loginForm = wrapper.getComponent({ name: 'LoginForm' })
  expect(loginForm.get('[data-testid="login-username"]').element.value).toBe('new-reader')
  expect(loginForm.get('[data-testid="login-password"]').element.value).toBe('')
  await loginForm.get('[data-testid="login-password"]').setValue('password123')
  await loginForm.get('[data-testid="login-form"]').trigger('submit')
  await flushPromises()
  expect(wrapper.get('.app-shell').attributes('inert')).toBeUndefined()
  expect(wrapper.get('.title-borrow-view').element).toBe(borrowed)
  expect(wrapper.getComponent({ name: 'AiAssistantView' }).vm.$.uid).toBe(assistant)
  expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('保留这条未发送草稿')
  expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-20')
  expect(wrapper.get('[data-testid="reserve-slot"]').element.value).toBe('16:00-17:00')
  expect(calls.filter(call => call.operationId === 'reserveTitle')).toHaveLength(0)
  await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
  expect(calls.filter(call => call.operationId === 'reserveTitle')).toHaveLength(1)
})

it('keeps a failed registration in the local layer and cancels back to the unchanged borrowing page', async () => {
  const { flushPromises } = await import('@vue/test-utils')
  const { ElDialog } = await import('element-plus')
  const { wrapper, calls } = await mountBorrowingAuthentication()
  await wrapper.get('[data-testid="reserve-form"]').trigger('submit')
  await flushPromises()
  await clickAuthenticationEntry('register-entry')
  await flushPromises()
  const registration = wrapper.getComponent({ name: 'RegisterForm' })
  await registration.get('[data-testid="register-username"]').setValue('taken-reader')
  await registration.get('[data-testid="register-password"]').setValue('password123')
  await registration.get('[data-testid="register-display-name"]').setValue('重复读者')
  await registration.get('[data-testid="register-form"]').trigger('submit')
  await flushPromises()
  expect(registration.get('[data-testid="error-alert"]').text()).toContain('MLS-ID-002')
  expect(registration.get('[data-testid="register-username"]').element.value).toBe('taken-reader')
  expect(calls.filter(call => ['createSession', 'reserveTitle'].includes(call.operationId))).toHaveLength(0)
  const dialog = wrapper.findAllComponents(ElDialog).find(component => component.props('title') === '注册读者账户')
  dialog.vm.$emit('update:modelValue', false)
  await flushPromises()
  expect(wrapper.get('.app-shell').attributes('inert')).toBeUndefined()
  expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe('/titles/2001/borrow')
  expect(wrapper.get('[data-testid="reserve-date"]').element.value).toBe('2026-10-20')
  expect(wrapper.get('[data-testid="reserve-slot"]').element.value).toBe('16:00-17:00')
})


async function clickAuthenticationEntry(testId) {
  const { DOMWrapper } = await import('@vue/test-utils')
  const button = document.body.querySelector(`.reservation-login-dialog [data-testid="${testId}"]`)
  expect(button).not.toBeNull()
  await new DOMWrapper(button).trigger('click')
}
