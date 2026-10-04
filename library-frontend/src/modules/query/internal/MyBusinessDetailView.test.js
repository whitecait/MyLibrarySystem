import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import { mountView } from '../../../tests/helpers'
import MyBusinessDetailView from './MyBusinessDetailView.vue'

const txId = '9007199254740993'
const mounted = []

function page(items, nextCursor = null) {
  return { code: 'MLS-QUERY-S002', message: 'ok', data: { items, nextCursor } }
}

function businessItem(kind, id, overrides = {}) {
  return {
    kind,
    id,
    status: 'CONFIRMED',
    title: '测试书目',
    businessDate: '2026-10-03',
    guaranteeType: null,
    reservationDate: null,
    pickupSlot: null,
    copyId: null,
    notifications: [],
    ...overrides,
  }
}

function deferred() {
  let resolve
  const promise = new Promise((complete) => {
    resolve = complete
  })
  return { promise, resolve }
}

beforeEach(() => login({ accessToken: 'reader-token', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  logout()
})

async function mountDetail(txId) {
  const wrapper = await mountView(MyBusinessDetailView, { props: { txId } })
  mounted.push(wrapper)
  return wrapper
}

describe('MyBusinessDetailView', () => {
  it('rejects a malformed transaction id without querying business records', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return page([])
      },
    })

    const wrapper = await mountDetail('40x3')
    await flushPromises()

    expect(calls).toHaveLength(0)
    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-not-found"]').exists()).toBe(true)
  })

  it('does not reveal same-id fine or order rows when no owned borrow transaction matches', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return page([
          { kind: 'FINE', id: '4500', status: 'OPEN', amountCents: 12345 },
          { kind: 'ORDER', id: '4500', status: 'OPEN', amountCents: 12345 },
        ])
      },
    })

    const wrapper = await mountDetail('4500')
    await flushPromises()

    expect(calls.map(({ options }) => options.query.kind)).toEqual(['RESERVATION', 'LOAN'])
    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-not-found"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('4500')
    expect(wrapper.text()).not.toContain('123.45')
  })

  it('fails closed when a business pagination cursor repeats', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return page([], options.query.cursor == null ? 'repeat-me' : 'repeat-me')
      },
    })

    const wrapper = await mountDetail('4501')
    await flushPromises()

    expect(calls).toHaveLength(2)
    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('响应异常')
  })

  it('does not let a slower previous transaction response replace the current route target', async () => {
    const pending = []
    setTransport({
      mode: 'mock',
      request(operationId, options = {}) {
        const request = deferred()
        pending.push({ operationId, options, request })
        return request.promise
      },
    })

    const wrapper = await mountDetail('5101')
    expect(pending).toHaveLength(1)
    await wrapper.setProps({ txId: '5102' })
    await flushPromises()
    expect(pending).toHaveLength(2)

    pending[1].request.resolve(page([businessItem('RESERVATION', '5102', { title: 'Current target' })]))
    await flushPromises()
    pending[0].request.resolve(page([businessItem('RESERVATION', '5101', { title: 'Stale target' })]))
    await flushPromises()

    const detail = wrapper.find('[data-testid="business-detail"]')
    expect(detail.text()).toContain('Current target')
    expect(detail.text()).not.toContain('Stale target')
  })

  it('drops an old reader response after the session changes to another reader', async () => {
    const pending = []
    setTransport({
      mode: 'mock',
      request(operationId, options = {}) {
        const request = deferred()
        pending.push({ operationId, options, request })
        return request.promise
      },
    })

    const wrapper = await mountDetail('5201')
    login({ accessToken: 'second-reader-token', actor: { accountId: '1002', role: 'READER' } })
    await flushPromises()
    expect(pending).toHaveLength(2)

    pending[0].request.resolve(page([businessItem('RESERVATION', '5201', { title: 'First reader data' })]))
    await flushPromises()
    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)

    pending[1].request.resolve(page([businessItem('RESERVATION', '5201', { title: 'Second reader data' })]))
    await flushPromises()
    const detail = wrapper.find('[data-testid="business-detail"]')
    expect(detail.text()).toContain('Second reader data')
    expect(detail.text()).not.toContain('First reader data')
  })

  it('hides a pending reader response after logout', async () => {
    const pending = []
    setTransport({
      mode: 'mock',
      request(operationId, options = {}) {
        const request = deferred()
        pending.push({ operationId, options, request })
        return request.promise
      },
    })

    const wrapper = await mountDetail('5301')
    logout()
    await flushPromises()
    pending[0].request.resolve(page([businessItem('RESERVATION', '5301', { title: 'Logged out data' })]))
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Logged out data')
    expect(wrapper.find('[data-testid="business-detail-not-found"]').exists()).toBe(true)
  })

  it('finds a reservation transaction on a later reservation page', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return options.query.cursor == null
          ? page([{ kind: 'RESERVATION', id: '41', status: 'WAITLIST' }], 'next-reservation-page')
          : page([businessItem('RESERVATION', '4003', { title: 'Reserved title' })])
      },
    })

    const wrapper = await mountDetail('4003')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail"]').text()).toContain('Reserved title')
    expect(calls.map(({ options }) => options.query)).toEqual([
      { kind: 'RESERVATION', status: undefined, cursor: undefined, limit: 20 },
      { kind: 'RESERVATION', status: undefined, cursor: 'next-reservation-page', limit: 20 },
    ])
  })

  it('finds the exact borrow transaction across paged reservation and loan projections', async () => {
    const calls = []
    const responses = new Map([
      ['RESERVATION|', page([{ kind: 'FINE', id: txId, amountCents: 9999 }], 'reservation-next')],
      ['RESERVATION|reservation-next', page([{ kind: 'RESERVATION', id: '42', status: 'WAITLIST' }])],
      ['LOAN|', page([{ kind: 'FINE', id: txId, amountCents: 9999 }], 'loan-next')],
      ['LOAN|loan-next', page([businessItem('LOAN', txId, {
        status: 'RETURNED',
        title: 'Target title',
        businessDate: '2026-10-02',
      })])],
    ])
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        const { kind, cursor } = options.query
        return responses.get(`${kind}|${cursor ?? ''}`)
      },
    })

    const wrapper = await mountDetail(txId)
    await flushPromises()

    const detail = wrapper.find('[data-testid="business-detail"]')
    expect(detail.exists()).toBe(true)
    expect(detail.text()).toContain(txId)
    expect(detail.text()).toContain('Target title')
    expect(detail.text()).toContain('RETURNED')
    expect(detail.text()).not.toContain('99.99')
    expect(calls.map(({ options }) => [options.query.kind, options.query.cursor, options.query.limit])).toEqual([
      ['RESERVATION', undefined, 20],
      ['RESERVATION', 'reservation-next', 20],
      ['LOAN', undefined, 20],
      ['LOAN', 'loan-next', 20],
    ])
  })

  it('shows B2 details and only the notices attached to the matched reservation', async () => {
    const calls = []
    const visitNotification = {
      notificationId: '7101',
      type: 'VISIT',
      read: false,
      reservationDate: '2026-10-03',
      createdAt: '2026-10-02T08:00:00Z',
    }
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return page([
          businessItem('RESERVATION', '6000', {
            notifications: [{ notificationId: '7000', type: 'NO_VISIT', read: true }],
          }),
          businessItem('FINE', '6001', {
            notifications: [{ notificationId: '7001', type: 'NO_VISIT', read: true }],
          }),
          businessItem('ORDER', '6001', {
            notifications: [{ notificationId: '7002', type: 'NO_VISIT', read: true }],
          }),
          businessItem('RESERVATION', '6001', {
            title: 'B2 已确认预约',
            guaranteeType: 'DIRECT_CONFIRMATION',
            reservationDate: '2026-10-03',
            pickupSlot: 'PM',
            copyId: 'copy-202',
            notifications: [visitNotification],
          }),
        ])
      },
    })

    const wrapper = await mountDetail('6001')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail-status"]').text()).toBe('CONFIRMED')
    expect(wrapper.find('[data-testid="business-detail-guarantee-type"]').text()).toBe('直接确认')
    expect(wrapper.find('[data-testid="business-detail-guarantee-type"]').element.parentElement.textContent)
      .toContain('创建时保证类型')
    expect(wrapper.find('[data-testid="business-detail-reservation-date"]').text()).toBe('2026-10-03')
    expect(wrapper.find('[data-testid="business-detail-pickup-slot"]').text()).toBe('下午（PM）')
    expect(wrapper.find('[data-testid="business-detail-copy"]').text()).toBe('copy-202')

    const renderedNotification = wrapper.find('[data-testid="business-detail-notification-7101"]')
    expect(renderedNotification.text()).toContain('7101')
    expect(renderedNotification.text()).toContain('到馆办理')
    expect(renderedNotification.text()).toContain('2026-10-03')
    expect(renderedNotification.text()).toContain('2026-10-02T08:00:00Z')
    expect(renderedNotification.text()).toContain('未读')
    expect(wrapper.find('[data-testid="business-detail-notification-7000"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-notification-7001"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-notification-7002"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-notifications-empty"]').exists()).toBe(false)
    expect(renderedNotification.find('button').exists()).toBe(false)
    expect(calls).toHaveLength(1)
  })

  it('shows a concrete pickup interval exactly as recorded', async () => {
    setTransport({
      mode: 'mock',
      async request() {
        return page([businessItem('RESERVATION', '6002', { pickupSlot: '14:00-14:30' })])
      },
    })

    const wrapper = await mountDetail('6002')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail-pickup-slot"]').text()).toBe('14:00-14:30')
  })

  it('shows B3 waitlist state, explicit unknown fields, no copy binding, and the empty notice state', async () => {
    setTransport({
      mode: 'mock',
      async request() {
        return page([businessItem('RESERVATION', '6003', {
          status: 'WAITLIST',
          title: 'B3 候补预约',
          guaranteeType: 'WAITLIST',
          reservationDate: null,
          pickupSlot: null,
          copyId: null,
          notifications: [],
        })])
      },
    })

    const wrapper = await mountDetail('6003')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail-status"]').text()).toBe('WAITLIST')
    expect(wrapper.find('[data-testid="business-detail-guarantee-type"]').text()).toBe('候补')
    expect(wrapper.find('[data-testid="business-detail-reservation-date"]').text()).toBe('未知')
    expect(wrapper.find('[data-testid="business-detail-pickup-slot"]').text()).toBe('未知')
    expect(wrapper.find('[data-testid="business-detail-copy"]').text()).toBe('未绑定副本')
    expect(wrapper.find('[data-testid="business-detail-notifications-empty"]').text()).toContain('暂无关联通知')
  })

  it('shows B5 as not borrowed and displays its no-visit notice', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return options.query.kind === 'RESERVATION'
          ? page([])
          : page([businessItem('LOAN', '6005', {
              status: 'NOT_BORROWED',
              title: 'B5 未借出历史事务',
              notifications: [{
                notificationId: '7105',
                type: 'NO_VISIT',
                read: true,
                reservationDate: '2026-10-03',
                createdAt: '2026-10-03T20:00:00Z',
              }],
            })])
      },
    })

    const wrapper = await mountDetail('6005')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail-status"]').text()).toBe('NOT_BORROWED')
    expect(wrapper.find('[data-testid="business-detail-guarantee-type"]').text()).toBe('未记录')
    expect(wrapper.find('[data-testid="business-detail-notifications-empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-notification-7105"]').text()).toContain('无需到馆')
    expect(calls.map(({ options }) => options.query.kind)).toEqual(['RESERVATION', 'LOAN'])
  })

  it('fails when a matched transaction omits its notice collection', async () => {
    setTransport({
      mode: 'mock',
      async request() {
        return page([{
          kind: 'RESERVATION',
          id: '6006',
          status: 'CONFIRMED',
          title: '通知查询字段缺失',
        }])
      },
    })

    const wrapper = await mountDetail('6006')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-notifications-empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('响应异常')
  })

  it('does not report an empty notice state when the business query fails', async () => {
    setTransport({
      mode: 'mock',
      async request() {
        throw new Error('query unavailable')
      },
    })

    const wrapper = await mountDetail('6007')
    await flushPromises()

    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="business-detail-notifications-empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
  })
})
