// DELIVERY 模块公开请求接口：站内通知与审计事件查询。
import { apiRequest } from '@/platform/http'

export function listNotifications({ unreadOnly, cursor, limit } = {}, options = {}) {
  return apiRequest('listNotifications', { query: { unreadOnly, cursor, limit }, ...options })
}

export function markNotificationRead({ notificationId }, options = {}) {
  return apiRequest('markNotificationRead', { params: { notificationId }, ...options })
}

export function listAuditEvents({ relatedId, actorId, eventType, cursor, limit } = {}, options = {}) {
  return apiRequest('listAuditEvents', { query: { relatedId, actorId, eventType, cursor, limit }, ...options })
}

export { default as NotificationCenterView } from './internal/NotificationCenterView.vue'
export { default as AuditQueryView } from './internal/AuditQueryView.vue'
