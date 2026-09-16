// IDENTITY 模块公开请求接口：注册、会话、账户与读者状态办理。
// 页面只经这些函数调用；URL、头部与信封细节全部在 platform 层。
import { apiRequest } from '@/platform/http'

export function register({ username, password, displayName }, options = {}) {
  return apiRequest('registerReader', { body: { username, password, displayName }, ...options })
}

export function createSession({ username, password }, options = {}) {
  return apiRequest('createSession', { body: { username, password }, ...options })
}

export function endCurrentSession(options = {}) {
  return apiRequest('endCurrentSession', options)
}

export function listAccounts({ role, status, cursor, limit } = {}, options = {}) {
  return apiRequest('listAccounts', { query: { role, status, cursor, limit }, ...options })
}

export function requestAccountChange({ accountId, action, payload, reason }, options = {}) {
  return apiRequest('requestAccountChange', { params: { accountId }, body: { action, payload, reason }, ...options })
}

export function resetPassword({ accountId, reason }, options = {}) {
  return apiRequest('resetPassword', { params: { accountId }, body: { reason }, ...options })
}

export function changeReaderStatus({ readerId, status, reason }, options = {}) {
  return apiRequest('changeReaderStatus', { params: { readerId }, body: { status, reason }, ...options })
}

export { default as LoginView } from './internal/LoginView.vue'
export { default as RegisterView } from './internal/RegisterView.vue'
export { default as AccountAdminView } from './internal/AccountAdminView.vue'
