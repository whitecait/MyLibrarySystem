// SETTLEMENT 模块公开请求接口：罚款、支付单、模拟结果调度、模拟回调、关闭、退款申请与对账。
import { apiRequest } from '@/platform/http'

export function listFines({ readerId, status, cursor, limit } = {}, options = {}) {
  return apiRequest('listFines', { query: { readerId, status, cursor, limit }, ...options })
}

export function createOrder({ fineId }, options = {}) {
  return apiRequest('createOrder', { params: { fineId }, ...options })
}

export function getOrder({ orderId }, options = {}) {
  return apiRequest('getOrder', { params: { orderId }, ...options })
}

export function scheduleSimulatedPayment({ orderId, result, delayMs, duplicateCount } = {}, options = {}) {
  return apiRequest('scheduleSimulatedPayment', {
    body: { orderId, result, delayMs, duplicateCount },
    ...options,
  })
}

// applyPaymentCallback 为模拟器凭证回调（机器对机器），前端无页面入口，仅为契约完整性保留门面函数。
export function applyPaymentCallback({ eventId, orderId, result, occurredAt }, options = {}) {
  return apiRequest('applyPaymentCallback', { body: { eventId, orderId, result, occurredAt }, ...options })
}

export function closeOrder({ orderId }, options = {}) {
  return apiRequest('closeOrder', { params: { orderId }, ...options })
}

export function requestRefund({ orderId, amountCents, reason }, options = {}) {
  return apiRequest('requestRefund', { params: { orderId }, body: { amountCents, reason }, ...options })
}

export function reconcileBusinessDate({ businessDate }, options = {}) {
  return apiRequest('reconcileBusinessDate', { body: { businessDate }, ...options })
}

export { default as FineSettlementView } from './internal/FineSettlementView.vue'
export { default as PaymentSimulatorView } from './internal/PaymentSimulatorView.vue'
export { default as ReconciliationView } from './internal/ReconciliationView.vue'
