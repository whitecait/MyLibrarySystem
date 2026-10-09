import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import { installMockTransport } from '../../tests/helpers'
import * as circulation from './index'

afterEach(() => setTransport(null))

describe('circulation module facade', () => {
  it('replays the waitlist confirmation scenario with txId null', async () => {
    installMockTransport({ scenarioId: 'RESERVATION-WAITLIST-CONFIRMATION' })
    const envelope = await circulation.reserveTitle({
      titleId: '2001',
      reservationDate: '2026-09-15',
      pickupSlot: '09:00-10:30',
      channel: 'ONLINE',
    })
    expect(envelope.code).toBe('MLS-CIRC-S001')
    expect(envelope.data.txId).toBeNull()
    expect(envelope.data.outcome).toBe('WAITLIST_PENDING_CONFIRMATION')
  })

  it('replays the mixed walk-in batch with per-item outcomes', async () => {
    installMockTransport({ scenarioId: 'WALKIN-MIXED-BATCH' })
    const envelope = await circulation.createWalkInLoans({
      channel: 'DESK',
      items: [
        { titleId: '2001', copyId: '3001' },
        { titleId: '2002', copyId: '3002' },
      ],
    })
    const [lent, rejected] = envelope.data.items
    expect(lent).toEqual({ itemRef: '3001', txId: '4001', outcome: 'LENT' })
    expect(rejected.outcome).toBe('REJECTED')
    expect(rejected.txId).toBeNull()
    expect(rejected.reasons).toEqual(['COPY_NOT_AVAILABLE'])
  })

  it('replays the return-with-fine scenario including the formed fine', async () => {
    installMockTransport({ scenarioId: 'RETURN-WITH-FINE' })
    const envelope = await circulation.returnCopy({ copyId: '3001', condition: 'INTACT' })
    expect(envelope.data).toEqual({ txId: '4001', outcome: 'RETURNED', fineId: '5001' })
  })

  it('maps desk operations to their contract operations', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: {} }
      },
    })

    await circulation.createCopy({ titleId: '2001', copyNo: 'C-003' })
    await circulation.listCopies({ titleId: '2001', status: 'AVAILABLE' })
    await circulation.changeCopyState({ copyId: '3001', action: 'MAINTAIN', version: 2 })
    await circulation.cancelReservation({ txId: '4002', reason: '读者自助取消预约' })
    await circulation.claimPickup({ txId: '4002', copyId: '3001' })
    await circulation.applyManualAction({ txId: '4002', action: 'REJECT', reason: '条件不符' })
    await circulation.renewLoan({ txId: '4001' })
    await circulation.getReservationCalendar({ titleId: '2001', from: '2026-09-16', to: '2026-09-20' })

    expect(calls.map((call) => call.operationId)).toEqual([
      'createCopy',
      'listCopies',
      'changeCopyState',
      'cancelReservation',
      'claimPickup',
      'applyManualAction',
      'renewLoan',
      'getReservationCalendar',
    ])
    expect(calls[2].options.params).toEqual({ copyId: '3001' })
    expect(calls[2].options.body).toEqual({ action: 'MAINTAIN', version: 2 })
    expect(calls[3].options.params).toEqual({ txId: '4002' })
    expect(calls[3].options.body).toEqual({ reason: '读者自助取消预约' })
    expect(calls[5].options.body).toEqual({ action: 'REJECT', reason: '条件不符' })
    expect(calls[7].options.query).toEqual({ from: '2026-09-16', to: '2026-09-20' })
  })

  it('sends confirmWaitlist only when the reader confirms the waitlist reservation', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: {} }
      },
    })
    await circulation.reserveTitle({ titleId: '2001', reservationDate: '2026-09-16', pickupSlot: '09:00-10:30', channel: 'ONLINE' })
    await circulation.reserveTitle({
      titleId: '2001',
      reservationDate: '2026-09-16',
      pickupSlot: '09:00-10:30',
      channel: 'ONLINE',
      confirmWaitlist: true,
    })
    expect(calls[0].options.body.confirmWaitlist).toBe(false)
    expect(calls[1].options.body.confirmWaitlist).toBe(true)
  })
})
