import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import AuditQueryView from './AuditQueryView.vue'
import NotificationCenterView from './NotificationCenterView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => logout())

describe('NotificationCenterView', () => {
  it('lists notifications and marks unread ones read once', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(NotificationCenterView)
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="notification-table"] tbody tr')
    expect(rows).toHaveLength(2)

    await wrapper.find('[data-testid="notification-read-7002"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-read-7002"]').exists()).toBe(false)
  })

  it('keeps the empty state when nothing matches', async () => {
    installMockTransport({
      fixtures: {
        listNotifications: { code: 'MLS-DEL-S001', message: 'ok', data: { items: [], nextCursor: null } },
      },
    })
    const wrapper = await mountView(NotificationCenterView)
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-empty"]').exists()).toBe(true)
  })
})

describe('AuditQueryView', () => {
  it('filters audit events by event type', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(AuditQueryView)
    await flushPromises()

    await wrapper.find('[data-testid="audit-event-type"]').setValue('ORDER_PAID')
    await wrapper.find('[data-testid="audit-filter"]').trigger('submit')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="audit-table"] tbody tr')
    expect(rows).toHaveLength(1)
    expect(wrapper.text()).toContain('ORDER_PAID')
  })

  it('keeps the empty state when nothing matches', async () => {
    installMockTransport({
      fixtures: {
        listAuditEvents: { code: 'MLS-DEL-S003', message: 'ok', data: { items: [], nextCursor: null } },
      },
    })
    const wrapper = await mountView(AuditQueryView)
    await flushPromises()

    expect(wrapper.find('[data-testid="audit-empty"]').exists()).toBe(true)
  })
})
