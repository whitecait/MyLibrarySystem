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

  it('does not reveal a same-id fine when no owned borrow transaction matches', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return page([{ kind: 'FINE', id: '4500', status: 'OPEN', amountCents: 12345 }])
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

    pending[1].request.resolve(page([{ kind: 'RESERVATION', id: '5102', status: 'CONFIRMED', title: 'Current target' }]))
    await flushPromises()
    pending[0].request.resolve(page([{ kind: 'RESERVATION', id: '5101', status: 'CONFIRMED', title: 'Stale target' }]))
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

    pending[0].request.resolve(page([{ kind: 'RESERVATION', id: '5201', status: 'CONFIRMED', title: 'First reader data' }]))
    await flushPromises()
    expect(wrapper.find('[data-testid="business-detail"]').exists()).toBe(false)

    pending[1].request.resolve(page([{ kind: 'RESERVATION', id: '5201', status: 'CONFIRMED', title: 'Second reader data' }]))
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
    pending[0].request.resolve(page([{ kind: 'RESERVATION', id: '5301', status: 'CONFIRMED', title: 'Logged out data' }]))
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
          : page([{
              kind: 'RESERVATION',
              id: '4003',
              status: 'CONFIRMED',
              title: 'Reserved title',
              businessDate: '2026-10-03',
            }])
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
      ['LOAN|loan-next', page([{
        kind: 'LOAN',
        id: txId,
        status: 'RETURNED',
        title: 'Target title',
        businessDate: '2026-10-02',
      }])],
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
})
