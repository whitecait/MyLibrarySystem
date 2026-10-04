import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import { createTestRouter, mountView } from '../../../tests/helpers'
import { getMyBusiness, queryRoutes } from '../index'
import MyBusinessView from './MyBusinessView.vue'

const GROUPS = ['CURRENT_LOANS', 'RESERVATIONS', 'HISTORY']

function page(items, nextCursor = null) {
  return { code: 'MLS-QUERY-S002', message: 'ok', data: { items, nextCursor } }
}

function transaction(kind, id, status, title = id) {
  return { kind, id, status, title }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((complete, fail) => {
    resolve = complete
    reject = fail
  })
  return { promise, resolve, reject }
}

function useTransport(respond) {
  const calls = []
  setTransport({
    mode: 'mock',
    async request(operationId, options = {}) {
      calls.push({ operationId, options })
      if (operationId === 'startAiSession') {
        return {
          code: 'MLS-AI-S001',
          message: 'ok',
          data: { sessionId: 'ai-test-session', role: 'READER', quota: { remaining: 5 } },
        }
      }
      return respond(operationId, options)
    },
  })
  return calls
}

async function mountBusiness(router = createTestRouter(queryRoutes)) {
  const wrapper = await mountView(MyBusinessView, { router })
  await flushPromises()
  return wrapper
}

async function selectTab(wrapper, group) {
  await wrapper.find(`[data-testid="my-business-tab-${group}"]`).trigger('click')
  await flushPromises()
}

beforeEach(() => logout())
afterEach(() => {
  setTransport(null)
  logout()
})

describe('MyBusinessView tabs', () => {
  it('loads three exclusive server groups and keeps fine and order queries outside the tabs', async () => {
    const calls = useTransport((_operationId, options) => {
      const query = options.query
      if (query.group === 'CURRENT_LOANS') return page([transaction('LOAN', '101', 'LENT', 'B1')])
      if (query.group === 'RESERVATIONS') {
        return page([
          transaction('RESERVATION', '202', 'CONFIRMED', 'B2'),
          transaction('RESERVATION', '203', 'WAITLIST', 'B3'),
        ])
      }
      if (query.group === 'HISTORY') {
        return page([
          transaction('LOAN', '404', 'RETURNED', 'B4'),
          transaction('RESERVATION', '505', 'NOT_BORROWED', 'B5'),
        ])
      }
      if (query.kind === 'FINE') return page([transaction('FINE', '202', 'DUE', '罚款 202')])
      if (query.kind === 'ORDER') return page([transaction('ORDER', '202', 'OCCUPIED', '支付单 202')])
      throw new Error(`unexpected business query: ${JSON.stringify(query)}`)
    })

    const wrapper = await mountBusiness()
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('B1')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).not.toContain('B2')
    expect(wrapper.find('[data-testid="business-detail-link-101"]').attributes('href')).toBe('/me/business/101')

    await selectTab(wrapper, 'RESERVATIONS')
    const reservationTable = wrapper.find('[data-testid="my-business-table"]')
    expect(reservationTable.text()).toContain('B2')
    expect(reservationTable.text()).toContain('B3')
    expect(reservationTable.text()).not.toContain('B1')
    expect(wrapper.find('[data-testid="business-detail-link-202"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="business-detail-link-203"]').exists()).toBe(true)

    await selectTab(wrapper, 'HISTORY')
    const historyTable = wrapper.find('[data-testid="my-business-table"]')
    expect(historyTable.text()).toContain('B4')
    expect(historyTable.text()).toContain('RETURNED')
    expect(historyTable.text()).toContain('B5')
    expect(historyTable.text()).toContain('NOT_BORROWED')

    await wrapper.find('[data-testid="my-business-other-kind"]').setValue('FINE')
    await flushPromises()
    const fineTable = wrapper.find('[data-testid="my-business-other-table"]')
    expect(fineTable.text()).toContain('罚款 202')
    expect(fineTable.find('[data-testid="business-detail-link-202"]').exists()).toBe(false)
    await wrapper.find('[data-testid="my-business-other-kind"]').setValue('ORDER')
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-other-table"]').text()).toContain('支付单 202')
    expect(wrapper.find('[data-testid="my-business-other-table"] [data-testid="business-detail-link-202"]').exists())
      .toBe(false)

    const groupCalls = calls.filter((call) => call.operationId === 'getMyBusiness' && call.options.query.group != null)
    expect(groupCalls.map((call) => call.options.query.group)).toEqual(GROUPS)
    for (const call of groupCalls) {
      expect(call.options.query).not.toHaveProperty('kind')
      expect(call.options.query.limit).toBe(20)
    }
    const businessCalls = calls.filter((call) => call.operationId === 'getMyBusiness')
    const fineCall = businessCalls.find((call) => call.options.query.kind === 'FINE')
    const orderCall = businessCalls.find((call) => call.options.query.kind === 'ORDER')
    expect(fineCall.options.query).not.toHaveProperty('group')
    expect(orderCall.options.query).not.toHaveProperty('group')
  })

  it('keeps an independent cursor and loaded rows for each tab', async () => {
    const calls = useTransport((_operationId, options) => {
      const { group, cursor } = options.query
      if (group === 'CURRENT_LOANS' && !cursor) return page([transaction('LOAN', 'L1', 'LENT')], 'loan-next')
      if (group === 'CURRENT_LOANS' && cursor === 'loan-next') return page([transaction('LOAN', 'L2', 'LENT')])
      if (group === 'RESERVATIONS' && !cursor) return page([transaction('RESERVATION', 'R1', 'AWAIT')], 'reservation-next')
      if (group === 'RESERVATIONS' && cursor === 'reservation-next') {
        return page([transaction('RESERVATION', 'R2', 'WAITLIST')])
      }
      if (group === 'HISTORY' && !cursor) return page([transaction('LOAN', 'H1', 'RETURNED')], 'history-next')
      if (group === 'HISTORY' && cursor === 'history-next') return page([transaction('LOAN', 'H2', 'NOT_BORROWED')])
      throw new Error(`unexpected cursor: ${group}/${cursor}`)
    })

    const wrapper = await mountBusiness()
    await wrapper.find('[data-testid="my-business-load-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('L2')

    await selectTab(wrapper, 'RESERVATIONS')
    await wrapper.find('[data-testid="my-business-load-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('R2')

    await selectTab(wrapper, 'HISTORY')
    await wrapper.find('[data-testid="my-business-load-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('H2')

    await selectTab(wrapper, 'CURRENT_LOANS')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('L1')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('L2')
    const pageCalls = calls.filter((call) => call.operationId === 'getMyBusiness')
    expect(pageCalls.map((call) => [call.options.query.group, call.options.query.cursor])).toEqual([
      ['CURRENT_LOANS', undefined],
      ['CURRENT_LOANS', 'loan-next'],
      ['RESERVATIONS', undefined],
      ['RESERVATIONS', 'reservation-next'],
      ['HISTORY', undefined],
      ['HISTORY', 'history-next'],
    ])
  })

  it('keeps a late response in its original tab and shows loading, empty, and failure states', async () => {
    const currentResponse = deferred()
    useTransport((_operationId, options) => {
      if (options.query.group === 'CURRENT_LOANS') return currentResponse.promise
      if (options.query.group === 'RESERVATIONS') return page([])
      if (options.query.group === 'HISTORY') throw new Error('history unavailable')
      throw new Error('unexpected group')
    })

    const wrapper = await mountBusiness()
    expect(wrapper.find('[data-testid="my-business-loading"]').exists()).toBe(true)
    await selectTab(wrapper, 'RESERVATIONS')
    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(true)

    currentResponse.resolve(page([transaction('LOAN', 'late-loan', 'LENT')]))
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(true)
    await selectTab(wrapper, 'CURRENT_LOANS')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('late-loan')

    await selectTab(wrapper, 'HISTORY')
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(false)
  })

  it('discards an old reader response after the session changes', async () => {
    const firstReaderResponse = deferred()
    let currentRequest = 0
    useTransport((_operationId, options) => {
      if (options.query.group !== 'CURRENT_LOANS') return page([])
      currentRequest += 1
      if (currentRequest === 1) return firstReaderResponse.promise
      return page([transaction('LOAN', 'reader-two-loan', 'LENT')])
    })
    login({ accessToken: 'reader-one-token', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = await mountBusiness()
    expect(wrapper.find('[data-testid="my-business-loading"]').exists()).toBe(true)

    login({ accessToken: 'reader-two-token', actor: { accountId: '1002', role: 'READER' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('reader-two-loan')

    firstReaderResponse.resolve(page([transaction('LOAN', 'reader-one-loan', 'LENT')]))
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('reader-two-loan')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).not.toContain('reader-one-loan')
  })

  it('clears pending list data on logout', async () => {
    const pendingResponse = deferred()
    useTransport(() => pendingResponse.promise)
    login({ accessToken: 'reader-token', actor: { accountId: '1001', role: 'READER' } })
    const wrapper = await mountBusiness()
    expect(wrapper.find('[data-testid="my-business-loading"]').exists()).toBe(true)

    logout()
    await flushPromises()
    pendingResponse.resolve(page([transaction('LOAN', 'logged-out-loan', 'LENT')]))
    await flushPromises()

    expect(wrapper.find('[data-testid="my-business-table"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(false)
  })

  it('refreshes the active server group after renew and cancel actions', async () => {
    const calls = []
    let reservationPage = 0
    useTransport((operationId, options) => {
      if (operationId === 'renewLoan') {
        return { code: 'MLS-CIRC-S005', message: 'renewed', data: { txId: '601', dueDate: '2026-10-20' } }
      }
      if (operationId === 'cancelReservation') {
        return { code: 'MLS-CIRC-S006', message: 'canceled', data: { txId: '701', outcome: 'CANCELED' } }
      }
      calls.push(options.query)
      if (options.query.group === 'CURRENT_LOANS') return page([transaction('LOAN', '601', 'LENT')])
      if (options.query.group === 'RESERVATIONS') {
        reservationPage += 1
        return reservationPage === 1
          ? page([transaction('RESERVATION', '701', 'WAITLIST')])
          : page([])
      }
      throw new Error(`unexpected group: ${options.query.group}`)
    })

    const wrapper = await mountBusiness()
    await wrapper.find('[data-testid="renew-601"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="renew-result"]').text()).toContain('2026-10-20')
    expect(calls.filter((query) => query.group === 'CURRENT_LOANS')).toHaveLength(2)

    await selectTab(wrapper, 'RESERVATIONS')
    await wrapper.find('[data-testid="cancel-701"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="my-business-empty"]').exists()).toBe(true)
    expect(calls.filter((query) => query.group === 'RESERVATIONS')).toHaveLength(2)
  })

  it('reloads previously visited history after a reservation becomes not borrowed', async () => {
    let historyCalls = 0
    let reservationCalls = 0
    useTransport((operationId, options) => {
      if (operationId === 'cancelReservation') {
        return { code: 'MLS-CIRC-S006', message: 'canceled', data: { txId: '701', outcome: 'CANCELED' } }
      }
      const { group } = options.query
      if (group === 'CURRENT_LOANS') return page([])
      if (group === 'RESERVATIONS') {
        reservationCalls += 1
        return reservationCalls === 1
          ? page([transaction('RESERVATION', '701', 'WAITLIST')])
          : page([])
      }
      if (group === 'HISTORY') {
        historyCalls += 1
        return historyCalls === 1
          ? page([transaction('LOAN', '404', 'RETURNED', 'B4')])
          : page([
              transaction('LOAN', '404', 'RETURNED', 'B4'),
              transaction('RESERVATION', '701', 'NOT_BORROWED', 'B5'),
            ])
      }
      throw new Error(`unexpected group: ${group}`)
    })

    const wrapper = await mountBusiness()
    await selectTab(wrapper, 'HISTORY')
    await selectTab(wrapper, 'RESERVATIONS')
    await wrapper.find('[data-testid="cancel-701"]').trigger('click')
    await flushPromises()

    await selectTab(wrapper, 'HISTORY')
    expect(historyCalls).toBe(2)
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('B5')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('NOT_BORROWED')
  })

  it('reloads history if cancellation succeeds after the reader switches to history', async () => {
    const cancellation = deferred()
    let historyCalls = 0
    useTransport((operationId, options) => {
      if (operationId === 'cancelReservation') return cancellation.promise
      const { group } = options.query
      if (group === 'CURRENT_LOANS') return page([])
      if (group === 'RESERVATIONS') return page([transaction('RESERVATION', '701', 'WAITLIST')])
      if (group === 'HISTORY') {
        historyCalls += 1
        return historyCalls === 1
          ? page([transaction('LOAN', '404', 'RETURNED', 'B4')])
          : page([
              transaction('LOAN', '404', 'RETURNED', 'B4'),
              transaction('RESERVATION', '701', 'NOT_BORROWED', 'B5'),
            ])
      }
      throw new Error(`unexpected group: ${group}`)
    })

    const wrapper = await mountBusiness()
    await selectTab(wrapper, 'HISTORY')
    await selectTab(wrapper, 'RESERVATIONS')
    await wrapper.find('[data-testid="cancel-701"]').trigger('click')
    await selectTab(wrapper, 'HISTORY')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).not.toContain('B5')

    cancellation.resolve({
      code: 'MLS-CIRC-S006',
      message: 'canceled',
      data: { txId: '701', outcome: 'CANCELED' },
    })
    await flushPromises()

    expect(historyCalls).toBe(2)
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('B5')
  })

  it('does not let an old history cursor response restore pre-cancel rows or cursor', async () => {
    const oldHistoryPage = deferred()
    const historyCursors = []
    let reservationCalls = 0
    let historyCalls = 0
    useTransport((operationId, options) => {
      if (operationId === 'cancelReservation') {
        return { code: 'MLS-CIRC-S006', message: 'canceled', data: { txId: '701', outcome: 'CANCELED' } }
      }
      const { group, cursor } = options.query
      if (group === 'CURRENT_LOANS') return page([])
      if (group === 'RESERVATIONS') {
        reservationCalls += 1
        return reservationCalls === 1
          ? page([transaction('RESERVATION', '701', 'WAITLIST')])
          : page([])
      }
      if (group === 'HISTORY') {
        historyCalls += 1
        historyCursors.push(cursor)
        if (historyCalls === 1) return page([transaction('LOAN', '404', 'RETURNED', 'B4')], 'old-history-cursor')
        if (cursor === 'old-history-cursor') return oldHistoryPage.promise
        return page([
          transaction('LOAN', '404', 'RETURNED', 'B4'),
          transaction('RESERVATION', '701', 'NOT_BORROWED', 'B5'),
        ])
      }
      throw new Error(`unexpected group: ${group}`)
    })

    const wrapper = await mountBusiness()
    await selectTab(wrapper, 'HISTORY')
    await wrapper.find('[data-testid="my-business-load-more"]').trigger('click')
    await selectTab(wrapper, 'RESERVATIONS')
    await wrapper.find('[data-testid="cancel-701"]').trigger('click')
    await flushPromises()

    oldHistoryPage.resolve(page([transaction('LOAN', 'stale-history-row', 'RETURNED', 'STALE')], 'stale-cursor'))
    await flushPromises()
    await selectTab(wrapper, 'HISTORY')

    expect(historyCursors).toEqual([undefined, 'old-history-cursor', undefined])
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('B5')
    expect(wrapper.find('[data-testid="my-business-table"]').text()).not.toContain('STALE')
  })

  it('keeps loaded history cached when cancellation fails', async () => {
    let historyCalls = 0
    useTransport((operationId, options) => {
      if (operationId === 'cancelReservation') throw new Error('cancel rejected')
      const { group } = options.query
      if (group === 'CURRENT_LOANS') return page([])
      if (group === 'RESERVATIONS') return page([transaction('RESERVATION', '701', 'WAITLIST')])
      if (group === 'HISTORY') {
        historyCalls += 1
        return page([transaction('LOAN', '404', 'RETURNED', 'B4')])
      }
      throw new Error(`unexpected group: ${group}`)
    })

    const wrapper = await mountBusiness()
    await selectTab(wrapper, 'HISTORY')
    await selectTab(wrapper, 'RESERVATIONS')
    await wrapper.find('[data-testid="cancel-701"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)

    await selectTab(wrapper, 'HISTORY')
    expect(historyCalls).toBe(1)
    expect(wrapper.find('[data-testid="my-business-table"]').text()).toContain('B4')
  })

  it('passes group pagination without kind and keeps legacy kind pagination compatible', async () => {
    const calls = useTransport(() => page([]))

    await getMyBusiness({ group: 'HISTORY', cursor: 'history-c2', limit: 20 })
    await getMyBusiness({ kind: 'FINE', cursor: 'fine-c3', limit: 20 })

    expect(calls[0].options.query.group).toBe('HISTORY')
    expect(calls[0].options.query.cursor).toBe('history-c2')
    expect(calls[0].options.query.limit).toBe(20)
    expect(calls[0].options.query).not.toHaveProperty('kind')
    expect(calls[1].options.query.kind).toBe('FINE')
    expect(calls[1].options.query.cursor).toBe('fine-c3')
    expect(calls[1].options.query).not.toHaveProperty('group')
  })
})
