// 真实 HTTP Adapter：模块门面经此调用 generated 操作，不感知 URL 与头部细节。
import { executeRequest } from './request.js'

export function createHttpTransport({ getToken = null } = {}) {
  return {
    mode: 'http',
    request(operationId, options = {}) {
      return executeRequest({ operationId, getToken, ...options })
    },
  }
}
