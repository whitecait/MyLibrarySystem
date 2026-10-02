import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { createTestRouter, installMockTransport, mountView } from '../../../tests/helpers'
import AuditQueryView from './AuditQueryView.vue'
import NotificationCenterView from './NotificationCenterView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => logout())

function notificationRouter() {
  return createTestRouter([
    { path: '/me/business/:txId', name: 'my-business-detail', component: { template: '<div />' } },
  ])
}

describe('NotificationCenterView', () => {
  it('shows the related transaction and reservation date on the notification row', async () => {
    installMockTransport({
      fixtures: {
        listNotifications: {
          code: 'MLS-DEL-S001',
          message: 'ok',
          data: {
            items: [
              {
                notificationId: '7002',
                type: 'PICKUP_READY',
                message: '您预约的书已到台，请于明日取书',
                read: false,
                relatedTxId: '4003',
                reservationDate: '2026-10-03',
                createdAt: '2026-10-02T12:00:00+08:00',
              },
            ],
            nextCursor: null,
          },
        },
      },
    })
    const router = notificationRouter()
    const wrapper = await mountView(NotificationCenterView, { router })
    await flushPromises()

    const row = wrapper.find('[data-testid="notification-table"] tbody tr')
    expect(row.text()).toContain('4003')
    expect(row.text()).toContain('2026-10-03')
    await row.find('[data-testid="notification-detail-7002"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('my-business-detail')
    expect(router.currentRoute.value.params.txId).toBe('4003')
  })

  it('does not link an unrelated or malformed transaction id', async () => {
    installMockTransport({
      fixtures: {
        listNotifications: {
          code: 'MLS-DEL-S001',
          message: 'ok',
          data: {
            items: [
              { notificationId: '7008', type: 'CLOSING_NOTICE', message: '普通通知', read: false, createdAt: '2026-10-02T12:00:00+08:00' },
              { notificationId: '7009', type: 'PICKUP_READY', message: '关联字段异常', read: false, relatedTxId: '../admin', reservationDate: '2026-10-03', createdAt: '2026-10-02T13:00:00+08:00' },
            ],
            nextCursor: null,
          },
        },
      },
    })
    const router = notificationRouter()
    const wrapper = await mountView(NotificationCenterView, { router })
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-detail-7008"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notification-detail-7009"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('lists notifications and marks unread ones read once', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountView(NotificationCenterView, { router: notificationRouter() })
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
