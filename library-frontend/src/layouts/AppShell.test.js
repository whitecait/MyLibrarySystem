import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import { login, logout, getToken, isAuthenticated } from '@/platform/auth/session'

// AppShell 只消费 identity 的 endCurrentSession；在此 mock 以捕获
// 「注销请求发出时会话令牌是否仍在」——令牌被清后请求不再携带 Bearer，
// 服务端将按 MLS-ID-001 拒绝且不会真正终结会话。
const { endCurrentSessionMock, tokenAtEndCall } = vi.hoisted(() => ({
  endCurrentSessionMock: vi.fn(),
  tokenAtEndCall: { value: 'unset' },
}))
const { routerPush } = vi.hoisted(() => ({ routerPush: vi.fn() }))

vi.mock('@/modules/identity', () => ({ endCurrentSession: endCurrentSessionMock }))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: { template: '<div />' },
  useRouter: () => ({ push: routerPush }),
}))

import AppShell from './AppShell.vue'

const mountShell = () =>
  mount(AppShell, {
    global: { components: { 'el-button': { template: '<button><slot /></button>' } } },
  })

describe('AppShell onLogout session teardown order (DEF-M6-004 reopen)', () => {
  beforeEach(() => {
    logout()
    sessionStorage.clear()
    routerPush.mockClear()
    endCurrentSessionMock.mockReset()
    tokenAtEndCall.value = 'unset'
  })
  afterEach(() => {
    logout()
    sessionStorage.clear()
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
})
