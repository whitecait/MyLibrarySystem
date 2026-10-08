import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ReviewInitiationView from './ReviewInitiationView.vue'

beforeEach(() => logout())
afterEach(() => logout())

async function mountInitiation({ fixtures } = {}) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(ReviewInitiationView)
  await flushPromises()
  await wrapper.get('[data-testid="initiation-open"]').trigger('click')
  await flushPromises()
  return wrapper
}

describe('ReviewInitiationView', () => {
  it('submits a review request from a manually entered account id', async () => {
    const wrapper = await mountInitiation({ fixtures: sharedFixtures })
    await wrapper.find('[data-testid="initiation-account-id"]').setValue('1001')
    await wrapper.find('[data-testid="initiation-action"]').setValue('SUSPEND')
    await wrapper.find('[data-testid="initiation-reason"]').setValue('违规借阅')
    await wrapper.find('[data-testid="initiation-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="initiation-result"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('6001')
    expect(result.text()).toContain('PENDING')
    expect(result.text()).toContain('批准前不生效')
  })

  it('asks for a target role only for CHANGE_ROLE', async () => {
    const wrapper = await mountInitiation({ fixtures: sharedFixtures })
    expect(wrapper.find('[data-testid="initiation-role"]').exists()).toBe(false)

    await wrapper.find('[data-testid="initiation-account-id"]').setValue('1002')
    await wrapper.find('[data-testid="initiation-action"]').setValue('CHANGE_ROLE')
    expect(wrapper.find('[data-testid="initiation-role"]').exists()).toBe(true)
    await wrapper.find('[data-testid="initiation-role"]').setValue('LIBRARIAN')
    await wrapper.find('[data-testid="initiation-reason"]').setValue('转岗')
    await wrapper.find('[data-testid="initiation-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="initiation-result"]').exists()).toBe(true)
  })

  it('surfaces a permission refusal from the mock', async () => {
    const wrapper = await mountInitiation({
      fixtures: { requestAccountChange: { status: 403, code: 'MLS-ID-005', message: 'forbidden', data: {} } },
    })
    await wrapper.find('[data-testid="initiation-account-id"]').setValue('1001')
    await wrapper.find('[data-testid="initiation-reason"]').setValue('违规借阅')
    await wrapper.find('[data-testid="initiation-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="initiation-result"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-ID-005')
  })
})
