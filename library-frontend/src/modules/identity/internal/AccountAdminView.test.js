import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import AccountAdminView from './AccountAdminView.vue'

beforeEach(() => logout())
afterEach(() => logout())

async function mountAdmin({ scenarioId, fixtures } = {}) {
  installMockTransport({ scenarioId, fixtures })
  const wrapper = await mountView(AccountAdminView)
  await flushPromises()
  return wrapper
}

describe('AccountAdminView', () => {
  it('lists accounts returned by the mock', async () => {
    const wrapper = await mountAdmin({ fixtures: sharedFixtures })
    const rows = wrapper.findAll('[data-testid="account-table"] tbody tr')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('reader01')
    expect(wrapper.text()).toContain('librarian01')
  })

  it('presents permission refusal when the list cannot be loaded', async () => {
    const wrapper = await mountAdmin({
      fixtures: { listAccounts: { status: 403, code: 'MLS-ID-001', message: 'forbidden', data: {} } },
    })
    const alert = wrapper.find('[data-testid="error-alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('MLS-ID-001')
  })

  it('shows the pending review reference after a high-risk change request', async () => {
    const wrapper = await mountAdmin({ fixtures: sharedFixtures })
    await wrapper.find('[data-testid="account-select-1001"]').trigger('click')

    await wrapper.find('[data-testid="change-action"]').setValue('SUSPEND')
    await wrapper.find('[data-testid="change-reason"]').setValue('违规借阅')
    await wrapper.find('[data-testid="change-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="change-review-result"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('6001')
    expect(result.text()).toContain('PENDING')
    expect(result.text()).toContain('批准前不生效')
  })

  it('shows the temporary password once after a password reset', async () => {
    const wrapper = await mountAdmin({ fixtures: sharedFixtures })
    await wrapper.find('[data-testid="account-select-1001"]').trigger('click')

    await wrapper.find('[data-testid="reset-reason"]').setValue('遗忘密码')
    await wrapper.find('[data-testid="reset-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="reset-result"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('tmp-Abc12345')
    expect(result.text()).toContain('必须修改密码')
  })

  it('records a reader status change', async () => {
    const wrapper = await mountAdmin({ fixtures: sharedFixtures })
    await wrapper.find('[data-testid="account-select-1001"]').trigger('click')

    await wrapper.find('[data-testid="reader-status"]').setValue('SUSPENDED')
    await wrapper.find('[data-testid="reader-status-reason"]').setValue('证件挂失')
    await wrapper.find('[data-testid="reader-status-form"]').trigger('submit')
    await flushPromises()

    const result = wrapper.find('[data-testid="reader-status-result"]')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('SUSPENDED')
  })

  it('surfaces a change-request rejection from the mock', async () => {
    const wrapper = await mountAdmin({
      fixtures: {
        ...sharedFixtures,
        requestAccountChange: { status: 422, code: 'MLS-ID-006', message: '状态冲突', data: {} },
      },
    })
    await wrapper.find('[data-testid="account-select-1002"]').trigger('click')
    await wrapper.find('[data-testid="change-reason"]').setValue('x')
    await wrapper.find('[data-testid="change-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="change-review-result"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-ID-006')
  })
})
