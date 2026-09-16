import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import { clearContinuation, peekContinuation } from '../platform/auth/continuation'
import { login, logout } from '../platform/auth/session'
import { createAppRouter } from './index'

const guardedRoute = {
  path: '/guarded',
  name: 'guarded',
  component: { template: '<div>guarded</div>' },
  meta: { module: 'identity', requiresAuth: true, roles: ['LIBRARIAN'], title: '受保护页' },
}

function buildRouter() {
  return createAppRouter({ history: createMemoryHistory(), extraRoutes: [guardedRoute] })
}

beforeEach(() => {
  logout()
  clearContinuation()
})

describe('router guards', () => {
  it('redirects anonymous visitors to login and saves only the continuation intent', async () => {
    const router = buildRouter()
    await router.push('/guarded?kind=LOAN')
    expect(router.currentRoute.value.name).toBe('login')
    expect(peekContinuation()).toEqual({ intent: 'navigate', path: '/guarded?kind=LOAN', params: null })
  })

  it('redirects authenticated but unauthorized roles to the forbidden page', async () => {
    login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } })
    const router = buildRouter()
    await router.push('/guarded')
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(peekContinuation()).toBeNull()
  })

  it('lets authorized roles through', async () => {
    login({ accessToken: 't', actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const router = buildRouter()
    await router.push('/guarded')
    expect(router.currentRoute.value.name).toBe('guarded')
  })

  it('keeps public routes public', async () => {
    const router = buildRouter()
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('home')
  })
})
