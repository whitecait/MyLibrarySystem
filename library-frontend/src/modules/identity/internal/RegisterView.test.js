import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { logout } from '@/platform/auth/session'
import { installMockTransport, mountView } from '../../../tests/helpers'
import RegisterView from './RegisterView.vue'

beforeEach(() => logout())

afterEach(() => {
  logout()
})

describe('RegisterView', () => {
  it('replays the approved registration scenario and shows the created account', async () => {
    installMockTransport({ scenarioId: 'AUTH-REGISTER-SUCCESS' })
    const wrapper = await mountView(RegisterView)

    await wrapper.find('[data-testid="register-username"]').setValue('reader01')
    await wrapper.find('[data-testid="register-password"]').setValue('example-only')
    await wrapper.find('[data-testid="register-display-name"]').setValue('Reader 01')
    await wrapper.find('[data-testid="register-form"]').trigger('submit')
    await flushPromises()

    const success = wrapper.find('[data-testid="register-success"]')
    expect(success.exists()).toBe(true)
    expect(success.text()).toContain('1001')
    expect(success.text()).toContain('READER')
    expect(success.text()).toContain('ACTIVE')
    expect(wrapper.find('[data-testid="register-form"]').exists()).toBe(false)
  })

  it('presents duplicate-username conflicts without hiding the form', async () => {
    installMockTransport({
      fixtures: {
        registerReader: { status: 409, code: 'MLS-ID-002', message: '用户名已被占用', data: {} },
      },
    })
    const wrapper = await mountView(RegisterView)

    await wrapper.find('[data-testid="register-username"]').setValue('reader01')
    await wrapper.find('[data-testid="register-password"]').setValue('password1')
    await wrapper.find('[data-testid="register-display-name"]').setValue('Reader 01')
    await wrapper.find('[data-testid="register-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-ID-002')
    expect(wrapper.find('[data-testid="register-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-success"]').exists()).toBe(false)
  })
})
