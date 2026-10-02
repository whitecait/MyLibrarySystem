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

  it('denies readers the librarian walk-in route and allows librarians', async () => {
    login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } })
    const readerRouter = buildRouter()
    await readerRouter.push('/walk-in')
    expect(readerRouter.currentRoute.value.name).toBe('forbidden')

    login({ accessToken: 't', actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const librarianRouter = buildRouter()
    await librarianRouter.push('/walk-in')
    expect(librarianRouter.currentRoute.value.name).toBe('walk-in')
  })

  it('keeps public routes public', async () => {
    const router = buildRouter()
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('lets an anonymous visitor open the AI assistant without creating a login continuation', async () => {
    const router = buildRouter()
    await router.push('/assistant')
    expect(router.currentRoute.value.name).toBe('ai-assistant')
    expect(peekContinuation()).toBeNull()
  })

  it('routes the retired reconciliation URL to the not-found page', async () => {
    const router = buildRouter()
    await router.push('/admin/reconciliation')
    expect(router.currentRoute.value.name).toBe('not-found')
  })
})
