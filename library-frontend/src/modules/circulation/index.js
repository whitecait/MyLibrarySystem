// CIRCULATION 模块公开请求接口：副本维护、预约、现场借阅、取书、人工处置、续借、归还、闭馆与恢复。
import { apiRequest } from '@/platform/http'

export function createCopy({ titleId, copyNo }, options = {}) {
  return apiRequest('createCopy', { params: { titleId }, body: { copyNo }, ...options })
}

export function listCopies({ titleId, status, cursor, limit } = {}, options = {}) {
  return apiRequest('listCopies', { query: { titleId, status, cursor, limit }, ...options })
}

export function changeCopyState({ copyId, action, version }, options = {}) {
  return apiRequest('changeCopyState', { params: { copyId }, body: { action, version }, ...options })
}

export function getReservationCalendar({ titleId, from, to }, options = {}) {
  return apiRequest('getReservationCalendar', { params: { titleId }, query: { from, to }, ...options })
}

export function reserveTitle({ titleId, reservationDate, pickupSlot, channel, onBehalfOf, confirmWaitlist = false }, options = {}) {
  return apiRequest('reserveTitle', {
    body: { titleId, reservationDate, pickupSlot, channel, onBehalfOf, confirmWaitlist },
    ...options,
  })
}

export function cancelReservation({ txId }, options = {}) {
  return apiRequest('cancelReservation', { params: { txId }, ...options })
}

export function createWalkInLoans({ readerId, items, channel }, options = {}) {
  return apiRequest('createWalkInLoans', { body: { readerId, items, channel }, ...options })
}

export function claimPickup({ txId, copyId }, options = {}) {
  return apiRequest('claimPickup', { params: { txId }, body: { copyId }, ...options })
}

export function applyManualAction({ txId, action, reason }, options = {}) {
  return apiRequest('applyManualAction', { params: { txId }, body: { action, reason }, ...options })
}

export function renewLoan({ txId, requestedDueDate }, options = {}) {
  return apiRequest('renewLoan', { params: { txId }, body: { requestedDueDate }, ...options })
}

export function returnCopy({ copyId, condition }, options = {}) {
  return apiRequest('returnCopy', { body: { copyId, condition }, ...options })
}

// runClosing 仅由 SYSTEM 触发，前端无业务入口；为契约完整性保留门面函数。
export function runClosing({ businessDate }, options = {}) {
  return apiRequest('runClosing', { body: { businessDate }, ...options })
}

export function reopenBusinessDate({ businessDate }, options = {}) {
  return apiRequest('reopenBusinessDate', { body: { businessDate }, ...options })
}

export { default as CopyAdminView } from './internal/CopyAdminView.vue'
export { default as ReservationCalendarView } from './internal/ReservationCalendarView.vue'
export { default as ReservePanel } from './internal/ReservePanel.vue'
export { default as WalkInLoansView } from './internal/WalkInLoansView.vue'
export { default as LibrarianDeskView } from './internal/LibrarianDeskView.vue'
export { default as ClosingAdminView } from './internal/ClosingAdminView.vue'
