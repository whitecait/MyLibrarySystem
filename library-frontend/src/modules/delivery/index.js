// DELIVERY 模块公开请求接口：站内通知与审计事件查询。
import { readonly, ref } from 'vue'
import { getRole, getSessionState, isAuthenticated } from '@/platform/auth/session'
import { apiRequest } from '@/platform/http'

const unreadBusinessNotificationState = ref(null)
export const unreadBusinessNotification = readonly(unreadBusinessNotificationState)

let activeReaderSessionKey = null
let unreadItemsLoaded = []
let unreadRequestVersion = 0

export function listNotifications({ unreadOnly, cursor, limit } = {}, options = {}) {
  return apiRequest('listNotifications', { query: { unreadOnly, cursor, limit }, ...options })
}

export function getNotificationSessionKey() {
  if (!isAuthenticated()) return null
  const session = getSessionState()
  if (!session.token) return null
  return `${session.actor?.accountId ?? ''}:${session.token}`
}

export function getReaderNotificationSessionKey() {
  return getRole() === 'READER' ? getNotificationSessionKey() : null
}

export function notificationTransactionId(notification) {
  const value = notification?.relatedTxId
  return typeof value === 'string' && /^\d+$/.test(value) ? value : null
}

function isBusinessDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

function isUnreadReservationNotification(notification) {
  return Boolean(
    notification
    && typeof notification === 'object'
    && !notification.read
    && notificationTransactionId(notification) !== null
    && isBusinessDate(notification.reservationDate),
  )
}

function compareNotificationIds(left, right) {
  const leftId = String(left.notificationId ?? '')
  const rightId = String(right.notificationId ?? '')
  if (/^\d+$/.test(leftId) && /^\d+$/.test(rightId)) {
    const leftNumber = BigInt(leftId)
    const rightNumber = BigInt(rightId)
    return leftNumber < rightNumber ? -1 : leftNumber > rightNumber ? 1 : 0
  }
  return leftId.localeCompare(rightId)
}

function selectNearestUnreadReservation(items) {
  return items
    .filter(isUnreadReservationNotification)
    .sort((left, right) => left.reservationDate.localeCompare(right.reservationDate)
      || String(left.createdAt ?? '').localeCompare(String(right.createdAt ?? ''))
      || compareNotificationIds(left, right))[0] ?? null
}

function resetUnreadBusinessNotification(sessionKey = null) {
  unreadRequestVersion += 1
  activeReaderSessionKey = sessionKey
  unreadItemsLoaded = []
  unreadBusinessNotificationState.value = null
}

export function clearUnreadBusinessNotification() {
  resetUnreadBusinessNotification()
}

export async function refreshUnreadBusinessNotification(expectedSessionKey = getReaderNotificationSessionKey()) {
  const currentSessionKey = getReaderNotificationSessionKey()
  if (!expectedSessionKey || currentSessionKey !== expectedSessionKey) {
    if (!currentSessionKey) resetUnreadBusinessNotification()
    return null
  }

  if (activeReaderSessionKey !== expectedSessionKey) {
    resetUnreadBusinessNotification(expectedSessionKey)
  }
  const requestVersion = ++unreadRequestVersion
  const loaded = []
  let cursor
  const seenCursors = new Set()

  while (true) {
    if (cursor && seenCursors.has(cursor)) return unreadBusinessNotificationState.value
    if (cursor) seenCursors.add(cursor)

    const envelope = await listNotifications({ unreadOnly: true, cursor, limit: 100 })
    if (
      requestVersion !== unreadRequestVersion
      || activeReaderSessionKey !== expectedSessionKey
      || getReaderNotificationSessionKey() !== expectedSessionKey
    ) return null

    const page = envelope?.data
    if (!page || !Array.isArray(page.items)) return unreadBusinessNotificationState.value

    loaded.push(...page.items.filter((notification) => !notification.read))
    unreadItemsLoaded = loaded
    unreadBusinessNotificationState.value = selectNearestUnreadReservation(loaded)
    if (unreadBusinessNotificationState.value) return unreadBusinessNotificationState.value

    cursor = page.nextCursor
    if (!cursor) return null
  }
}

function updateUnreadNotificationAfterRead(notificationId, sessionKey) {
  if (sessionKey !== activeReaderSessionKey) return
  unreadItemsLoaded = unreadItemsLoaded.map((notification) => (
    String(notification.notificationId) === String(notificationId)
      ? { ...notification, read: true }
      : notification
  ))
  unreadBusinessNotificationState.value = selectNearestUnreadReservation(unreadItemsLoaded)
}

export async function markNotificationRead({ notificationId }, options = {}) {
  const sessionKey = getNotificationSessionKey()
  const envelope = await apiRequest('markNotificationRead', { params: { notificationId }, ...options })
  if (envelope?.data.read && sessionKey && getNotificationSessionKey() === sessionKey) {
    if (getReaderNotificationSessionKey() === sessionKey && activeReaderSessionKey === sessionKey) {
      updateUnreadNotificationAfterRead(notificationId, sessionKey)
      void refreshUnreadBusinessNotification(sessionKey).catch(() => {})
    }
  }
  return envelope
}

export function listAuditEvents({ relatedId, actorId, eventType, cursor, limit } = {}, options = {}) {
  return apiRequest('listAuditEvents', { query: { relatedId, actorId, eventType, cursor, limit }, ...options })
}

export { default as NotificationCenterView } from './internal/NotificationCenterView.vue'
export { default as AuditQueryView } from './internal/AuditQueryView.vue'
