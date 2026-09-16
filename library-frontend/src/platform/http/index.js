// HTTP/Mock Adapter 的唯一选择缝：模块门面只经 getTransport()/apiRequest() 发请求。
// 默认模式由构建环境 VITE_API_MODE 决定；测试可经 setTransport() 覆盖。
import { createHttpTransport } from './adapter.js'
import { createMockTransport } from './mock/mockAdapter.js'

export function resolveDefaultMode() {
  return import.meta.env?.VITE_API_MODE === 'mock' ? 'mock' : 'http'
}

export function createTransport({ mode = resolveDefaultMode(), getToken = null, mock } = {}) {
  if (mode === 'mock') return createMockTransport(mock)
  return createHttpTransport({ getToken })
}

let currentTransport = null

export function getTransport() {
  if (!currentTransport) currentTransport = createTransport()
  return currentTransport
}

export function setTransport(transport) {
  currentTransport = transport
}

export function apiRequest(operationId, options) {
  return getTransport().request(operationId, options)
}

export { onUnauthorized } from './request.js'
export { FailureKind, RequestFailure } from './errors.js'
export { useApiRequest } from './useApi.js'
