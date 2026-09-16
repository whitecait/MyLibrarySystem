import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { createAppRouter } from './router'
import { login, logout } from './platform/auth/session'
import App from './App.vue'

async function mountApp() {
  const router = createAppRouter({ history: createMemoryHistory() })
  await router.push('/')
  await router.isReady()
  return mount(App, { global: { plugins: [router, ElementPlus] } })
}

describe('application shell', () => {
  it('renders the shell with brand, navigation and an anonymous session area', async () => {
    logout()
    const wrapper = await mountApp()
    expect(wrapper.text()).toContain('MyLibrarySystem')
    expect(wrapper.find('[data-testid="app-nav"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="login-link"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('适配器模式')
  })

  it('shows the actor role and logout control after login', async () => {
    login({ accessToken: 'opaque-token', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = await mountApp()
    expect(wrapper.find('[data-testid="logout-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="session-area"]').text()).toContain('READER')
    logout()
  })
})
