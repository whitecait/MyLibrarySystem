import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { clearContinuation, peekContinuation, saveContinuation } from '@/platform/auth/continuation'
import { getToken, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { createTestRouter, installMockTransport, mountView } from '../../../tests/helpers'
import LoginView from './LoginView.vue'

function buildRouter() {
  return createTestRouter([{ path: '/me/business', name: 'my-business', component: { template: '<div />' } }])
}

beforeEach(() => {
  logout()
  clearContinuation()
})

afterEach(() => {
  logout()
  clearContinuation()
})

describe('LoginView', () => {
  it('logs in, consumes the continuation intent and lands on the saved path', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const router = buildRouter()
    saveContinuation({ intent: 'navigate', path: '/me/business', params: null })
    const wrapper = await mountView(LoginView, { router })

    await wrapper.find('[data-testid="login-username"]').setValue('librarian01')
    await wrapper.find('[data-testid="login-password"]').setValue('password1')
    await wrapper.find('[data-testid="login-form"]').trigger('submit')
    await flushPromises()

    expect(getToken()).toBe('opaque-dev-token')
    expect(peekContinuation()).toBeNull()
    expect(router.currentRoute.value.fullPath).toBe('/me/business')
  })

  it('lands on home when no continuation intent exists', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const router = buildRouter()
    const wrapper = await mountView(LoginView, { router })

    await wrapper.find('[data-testid="login-username"]').setValue('librarian01')
    await wrapper.find('[data-testid="login-password"]').setValue('password1')
    await wrapper.find('[data-testid="login-form"]').trigger('submit')
    await flushPromises()

    expect(getToken()).toBe('opaque-dev-token')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('rejects wrong credentials without creating a session', async () => {
    installMockTransport({
      fixtures: {
        createSession: { status: 401, code: 'MLS-ID-004', message: '用户名或密码不正确', data: {} },
      },
    })
    const router = buildRouter()
    const wrapper = await mountView(LoginView, { router })

    await wrapper.find('[data-testid="login-username"]').setValue('reader01')
    await wrapper.find('[data-testid="login-password"]').setValue('wrong')
    await wrapper.find('[data-testid="login-form"]').trigger('submit')
    await flushPromises()

    expect(getToken()).toBeNull()
    expect(router.currentRoute.value.name).not.toBe('my-business')
    const alert = wrapper.find('[data-testid="error-alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('MLS-ID-004')
  })
})
