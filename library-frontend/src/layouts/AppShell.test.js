import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import { login, logout, getToken, isAuthenticated } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import { createMockTransport } from '@/platform/http/mock/mockAdapter'
import {
  clearUnreadBusinessNotification,
  markNotificationRead,
  NotificationCenterView,
} from '@/modules/delivery'

const mountedWrappers = []

// AppShell 只消费 identity 的 endCurrentSession；在此 mock 以捕获
// 「注销请求发出时会话令牌是否仍在」——令牌被清后请求不再携带 Bearer，
// 服务端将按 MLS-ID-001 拒绝且不会真正终结会话。
const { endCurrentSessionMock, tokenAtEndCall } = vi.hoisted(() => ({
  endCurrentSessionMock: vi.fn(),
  tokenAtEndCall: { value: 'unset' },
}))
const { routerPush, routeState } = vi.hoisted(() => ({ routerPush: vi.fn(), routeState: { value: null } }))

vi.mock('@/modules/identity', () => ({ endCurrentSession: endCurrentSessionMock }))
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  routeState.value = reactive({ name: 'home', fullPath: '/' })
  return {
    RouterLink: {
      props: ['to'],
      setup(props) {
        return { navigate: () => routerPush(props.to) }
      },
      template: '<a href="#" :data-route-name="to?.name" :data-route-tx-id="to?.params?.txId" @click.prevent="navigate"><slot /></a>',
    },
    RouterView: { template: '<div />' },
    useRoute: () => routeState.value,
    useRouter: () => ({
      push: routerPush,
      resolve: (path) => ({ fullPath: path, path, name: path === '/' ? 'home' : 'my-business', meta: { requiresAuth: false, roles: [] } }),
    }),
  }
})

import AppShell from './AppShell.vue'

const mountShell = () => {
  const wrapper = mount(AppShell, {
    global: { components: { 'el-button': { template: '<button><slot /></button>' } } },
  })
  mountedWrappers.push(wrapper)
  return wrapper
}

function installNotifications(items, nextCursor = null) {
  const markReadCalls = []
  const transport = createMockTransport({
    fixtures: {
      listNotifications: { code: 'MLS-DEL-S001', message: 'ok', data: { items, nextCursor } },
      markNotificationRead: { code: 'MLS-DEL-S002', message: 'read', data: { read: true } },
    },
  })
  setTransport({
    mode: 'mock',
    async request(operationId, options = {}) {
      if (operationId === 'markNotificationRead') markReadCalls.push(options.params)
      return transport.request(operationId, options)
    },
  })
  return { markReadCalls }
}

const unreadNote = ({ notificationId, relatedTxId, reservationDate, message = '预约通知' }) => ({
  notificationId,
  type: 'PICKUP_READY',
  message,
  read: false,
  relatedTxId,
  reservationDate,
  createdAt: '2026-10-02T12:00:00+08:00',
})

describe('AppShell notification Banner and logout behavior', () => {
  beforeEach(() => {
    logout()
    sessionStorage.clear()
    clearUnreadBusinessNotification()
    installNotifications([])
    routerPush.mockClear()
    Object.assign(routeState.value, { name: 'home', fullPath: '/' })
    endCurrentSessionMock.mockReset()
    tokenAtEndCall.value = 'unset'
  })
  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    logout()
    sessionStorage.clear()
    clearUnreadBusinessNotification()
  })

  it('shows the nearest unread reservation once and routes to its transaction', async () => {
    const { markReadCalls } = installNotifications([
      unreadNote({ notificationId: '7004', relatedTxId: '4012', reservationDate: '2026-10-04', message: '较晚预约通知' }),
      unreadNote({ notificationId: '7002', relatedTxId: '4003', reservationDate: '2026-10-03', message: '较近预约通知' }),
      { notificationId: '7003', type: 'CLOSING_NOTICE', message: '已读通知', read: true, createdAt: '2026-10-02T10:00:00+08:00' },
    ])
    login({ accessToken: 'token-banner-reader', actor: { accountId: '1001', role: 'READER' } })

    const wrapper = mountShell()
    await flushPromises()

    const banners = wrapper.findAll('[data-testid="notification-banner"]')
    expect(banners).toHaveLength(1)
    expect(banners[0].text()).toContain('4003')
    expect(banners[0].text()).toContain('2026-10-03')
    expect(banners[0].text()).not.toContain('4012')
    expect(banners[0].text()).not.toContain('另有')
    const shellChildren = Array.from(wrapper.find('.app-shell').element.children)
    expect(shellChildren.indexOf(banners[0].element)).toBe(shellChildren.indexOf(wrapper.find('.app-main').element) - 1)

    const link = banners[0].find('[data-testid="notification-banner-link"]')
    await link.trigger('click')
    expect(routerPush).toHaveBeenCalledWith({ name: 'my-business-detail', params: { txId: '4003' } })
    expect(markReadCalls).toEqual([])
  })

  it('keeps the same compact header and role work entries on each background', async () => {
    login({ accessToken: 'token-home-shell-reader', actor: { accountId: '1001', role: 'READER' } })

    const wrapper = mountShell()
    await flushPromises()

    expect(wrapper.find('.app-shell').classes()).toContain('app-shell--home')
    expect(wrapper.find('.brand').text()).toBe('云图公共图书馆')
    expect(wrapper.get('[data-testid="app-nav"]').element.children).toHaveLength(2)
    expect(wrapper.get('[data-testid="personal-info-menu"] summary').text()).toBe('个人信息')
    expect(wrapper.find('.home-work-menu').text()).toContain('我的业务')
    expect(wrapper.find('.home-work-menu').text()).toContain('通知')

    Object.assign(routeState.value, { name: 'my-business', fullPath: '/me/business' })
    await flushPromises()

    expect(wrapper.find('.app-shell').classes()).not.toContain('app-shell--home')
    expect(wrapper.find('.brand').text()).toBe('云图公共图书馆')
    expect(wrapper.find('.home-work-menu').exists()).toBe(true)
    expect(wrapper.find('[data-testid="app-nav"]').text()).toContain('我的业务')
  })

  it('hides the banner when unread items have no valid reservation association', async () => {
    installNotifications([
      { notificationId: '7008', type: 'CLOSING_NOTICE', message: '普通通知', read: false, createdAt: '2026-10-02T12:00:00+08:00' },
      unreadNote({ notificationId: '7009', relatedTxId: '../admin', reservationDate: '2026-10-03', message: '关联字段异常' }),
      unreadNote({ notificationId: '7010', relatedTxId: '4010', reservationDate: '2026-02-31', message: '日期无效' }),
    ])
    login({ accessToken: 'token-banner-reader-empty', actor: { accountId: '1001', role: 'READER' } })

    const wrapper = mountShell()
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-banner"]').exists()).toBe(false)
    expect(routerPush).not.toHaveBeenCalled()
  })

  it('hides the banner when every notification is already read', async () => {
    installNotifications([
      { ...unreadNote({ notificationId: '7003', relatedTxId: '4003', reservationDate: '2026-10-03' }), read: true },
    ])
    login({ accessToken: 'token-banner-read-only', actor: { accountId: '1001', role: 'READER' } })

    const wrapper = mountShell()
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-banner"]').exists()).toBe(false)
  })

  it('continues unread pages past ordinary notices before choosing a banner', async () => {
    const cursors = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        if (operationId !== 'listNotifications') throw new Error(`Unexpected operation: ${operationId}`)
        cursors.push(options.query?.cursor ?? null)
        if (!options.query?.cursor) {
          return {
            code: 'MLS-DEL-S001', message: 'ok',
            data: {
              items: [{ notificationId: '7008', type: 'CLOSING_NOTICE', message: '普通通知', read: false, createdAt: '2026-10-02T12:00:00+08:00' }],
              nextCursor: 'notification-cursor-1',
            },
          }
        }
        return {
          code: 'MLS-DEL-S001', message: 'ok',
          data: { items: [unreadNote({ notificationId: '7010', relatedTxId: '4010', reservationDate: '2026-10-03' })], nextCursor: null },
        }
      },
    })
    login({ accessToken: 'token-banner-pages', actor: { accountId: '1001', role: 'READER' } })

    const wrapper = mountShell()
    await flushPromises()

    expect(cursors).toEqual([null, 'notification-cursor-1'])
    expect(wrapper.find('[data-testid="notification-banner"]').text()).toContain('4010')
  })

  it('refreshes on reader home/notification entry without replacing the source from center results', async () => {
    let unread = []
    const queries = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        if (operationId !== 'listNotifications') throw new Error(`Unexpected operation: ${operationId}`)
        const unreadOnly = options.query?.unreadOnly === true
        queries.push({ unreadOnly, cursor: options.query?.cursor ?? null })
        return {
          code: 'MLS-DEL-S001', message: 'ok',
          data: { items: unreadOnly ? unread : [], nextCursor: null },
        }
      },
    })
    Object.assign(routeState.value, { name: 'my-business', fullPath: '/me/business' })
    login({ accessToken: 'token-banner-route-refresh', actor: { accountId: '1001', role: 'READER' } })

    const shell = mountShell()
    await flushPromises()
    expect(shell.find('[data-testid="notification-banner"]').exists()).toBe(false)
    expect(queries).toEqual([{ unreadOnly: true, cursor: null }])

    unread = [unreadNote({ notificationId: '7201', relatedTxId: '4201', reservationDate: '2026-10-03' })]
    Object.assign(routeState.value, { name: 'notification-center', fullPath: '/notifications' })
    await flushPromises()
    expect(shell.find('[data-testid="notification-banner"]').text()).toContain('4201')

    const center = mount(NotificationCenterView, { global: { plugins: [ElementPlus] } })
    mountedWrappers.push(center)
    await flushPromises()
    expect(center.find('[data-testid="notification-empty"]').exists()).toBe(true)
    expect(shell.find('[data-testid="notification-banner"]').text()).toContain('4201')

    unread = [unreadNote({ notificationId: '7301', relatedTxId: '4301', reservationDate: '2026-10-04' })]
    Object.assign(routeState.value, { name: 'home', fullPath: '/' })
    await flushPromises()
    expect(shell.find('[data-testid="notification-banner"]').text()).toContain('4301')
    expect(queries.filter((query) => query.unreadOnly)).toHaveLength(3)
    expect(queries.filter((query) => !query.unreadOnly)).toHaveLength(1)
  })

  it('syncs a successful center read to the shell banner and selects the next cached notice', async () => {
    const markReadParams = []
    let unread = [
      unreadNote({ notificationId: '7002', relatedTxId: '4003', reservationDate: '2026-10-03' }),
      unreadNote({ notificationId: '7004', relatedTxId: '4012', reservationDate: '2026-10-04' }),
    ]
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        if (operationId === 'listNotifications') {
          return { code: 'MLS-DEL-S001', message: 'ok', data: { items: unread, nextCursor: null } }
        }
        if (operationId === 'markNotificationRead') {
          markReadParams.push(options.params)
          const notificationId = options.params?.notificationId
          unread = unread.filter((item) => item.notificationId !== notificationId)
          return { code: 'MLS-DEL-S002', message: 'read', data: { read: true } }
        }
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })
    login({ accessToken: 'token-banner-read-sync', actor: { accountId: '1001', role: 'READER' } })

    const shell = mountShell()
    await flushPromises()
    expect(shell.find('[data-testid="notification-banner"]').text()).toContain('4003')

    await markNotificationRead({ notificationId: '7002' })
    await flushPromises()

    expect(markReadParams).toEqual([{ notificationId: '7002' }])
    expect(shell.find('[data-testid="notification-banner"]').text()).toContain('4012')
    expect(shell.find('[data-testid="notification-banner"]').text()).not.toContain('4003')
  })

  it('ignores an earlier reader response after the session changes', async () => {
    let resolveFirst
    let calls = 0
    setTransport({
      mode: 'mock',
      async request(operationId) {
        if (operationId !== 'listNotifications') throw new Error(`Unexpected operation: ${operationId}`)
        calls += 1
        if (calls === 1) {
          return new Promise((resolve) => { resolveFirst = resolve })
        }
        return {
          code: 'MLS-DEL-S001', message: 'ok',
          data: { items: [unreadNote({ notificationId: '7102', relatedTxId: '5102', reservationDate: '2026-10-03', message: '读者乙通知' })], nextCursor: null },
        }
      },
    })
    login({ accessToken: 'token-reader-a', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = mountShell()
    await flushPromises()
    expect(calls).toBe(1)

    logout()
    login({ accessToken: 'token-reader-b', actor: { accountId: '1002', role: 'READER' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="notification-banner"]').text()).toContain('5102')

    resolveFirst({
      code: 'MLS-DEL-S001', message: 'ok',
      data: { items: [unreadNote({ notificationId: '7101', relatedTxId: '5101', reservationDate: '2026-10-02', message: '读者甲通知' })], nextCursor: null },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="notification-banner"]').text()).toContain('5102')
    expect(wrapper.find('[data-testid="notification-banner"]').text()).not.toContain('5101')
  })

  it('sends endCurrentSession while the token is still held, then clears the local session', async () => {
    endCurrentSessionMock.mockImplementation(async () => {
      tokenAtEndCall.value = getToken()
    })
    login({ accessToken: 'token-shell-1', expiresAt: null, actor: { role: 'READER' } })

    const wrapper = mountShell()
    await wrapper.find('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(endCurrentSessionMock).toHaveBeenCalledTimes(1)
    expect(tokenAtEndCall.value).toBe('token-shell-1')
    expect(isAuthenticated()).toBe(false)
    expect(getToken()).toBeNull()
    expect(routerPush).toHaveBeenCalledWith({ name: 'home' })
  })

  it('still clears the local session and navigates when the server rejects sign-out', async () => {
    endCurrentSessionMock.mockImplementation(async () => {
      tokenAtEndCall.value = getToken()
      throw new Error('server rejected')
    })
    login({ accessToken: 'token-shell-2', expiresAt: null, actor: { role: 'READER' } })

    const wrapper = mountShell()
    await wrapper.find('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(tokenAtEndCall.value).toBe('token-shell-2')
    expect(isAuthenticated()).toBe(false)
    expect(routerPush).toHaveBeenCalledWith({ name: 'home' })
  })

  it('offers a keyboard-operable collapsed navigation control for long role menus', async () => {
    login({ accessToken: 'token-shell-admin', expiresAt: null, actor: { role: 'ADMIN' } })
    const wrapper = mountShell()

    const toggle = wrapper.find('[data-testid="mobile-nav-toggle"]')
    expect(toggle.attributes('aria-controls')).toBe('app-nav')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[data-testid="app-nav"]').classes()).not.toContain('is-mobile-open')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('[data-testid="app-nav"]').classes()).toContain('is-mobile-open')
    expect(wrapper.find('[data-testid="app-nav"]').text()).toContain('账户管理')

    await wrapper.find('[data-testid="app-nav"]').trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[data-testid="app-nav"]').classes()).not.toContain('is-mobile-open')
  })
})
