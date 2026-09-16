// AI 模块公开请求接口：会话、消息与事件流。
// 生产模型模式 thinking=disabled（MLS-DEC-0030）；模型返回的角色、对象、金额与判定均不可信，
// 仅作呈现与预填，业务事实一律经 B1～B4 现行接口提交并重新鉴权。
import { apiRequest } from '@/platform/http'

export function startAiSession({ clientSessionId } = {}, options = {}) {
  return apiRequest('startAiSession', { body: { clientSessionId }, ...options })
}

export function sendAiMessage({ sessionId, message }, options = {}) {
  return apiRequest('sendAiMessage', { params: { sessionId }, body: { message }, ...options })
}

export function listAiEvents({ cursor, limit } = {}, options = {}) {
  return apiRequest('listAiEvents', { query: { cursor, limit }, ...options })
}

export { default as AiAssistantView } from './internal/AiAssistantView.vue'
