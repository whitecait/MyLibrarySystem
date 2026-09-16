import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import ReviewListView from './ReviewListView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1003', role: 'ADMIN' } }))
afterEach(() => logout())

async function mountReviews(fixtures) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(ReviewListView)
  await flushPromises()
  return wrapper
}

describe('ReviewListView', () => {
  it('lists pending reviews and opens the decision form', async () => {
    const wrapper = await mountReviews(sharedFixtures)

    const rows = wrapper.findAll('[data-testid="review-table"] tbody tr')
    expect(rows).toHaveLength(1)
    expect(wrapper.text()).toContain('ACCOUNT_CHANGE')

    await wrapper.find('[data-testid="review-decide-6001"]').trigger('click')
    expect(wrapper.find('[data-testid="review-target-line"]').text()).toContain('6001')
    expect(wrapper.text()).toContain('批准时系统将重新校验目标当前状态')
  })

  it('approves a review and shows the decision', async () => {
    const wrapper = await mountReviews(sharedFixtures)

    await wrapper.find('[data-testid="review-decide-6001"]').trigger('click')
    await wrapper.find('[data-testid="decision"]').setValue('APPROVED')
    await wrapper.find('[data-testid="decision-reason"]').setValue('核实无误')
    await wrapper.find('[data-testid="decision-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="decision-success"]').exists()).toBe(true)
  })

  it('presents target-changed conflicts with the re-initiation hint', async () => {
    const wrapper = await mountReviews({
      ...sharedFixtures,
      decideReview: {
        status: 409,
        code: 'MLS-REV-001',
        message: '目标状态已变',
        data: { reviewId: '6001', reInitiable: true },
      },
    })

    await wrapper.find('[data-testid="review-decide-6001"]').trigger('click')
    await wrapper.find('[data-testid="decision-reason"]').setValue('批准')
    await wrapper.find('[data-testid="decision-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="target-changed"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="decision-success"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('重新发起复核')
  })

  it('rejects a review without executing the target', async () => {
    const wrapper = await mountReviews({
      ...sharedFixtures,
      decideReview: { code: 'MLS-REV-S002', message: 'decided', data: { reviewId: '6001', status: 'REJECTED' } },
    })

    await wrapper.find('[data-testid="review-decide-6001"]').trigger('click')
    await wrapper.find('[data-testid="decision"]').setValue('REJECTED')
    await wrapper.find('[data-testid="decision-reason"]').setValue('材料不足')
    await wrapper.find('[data-testid="decision-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="decision-success"]').text()).toContain('REJECTED')
  })
})
