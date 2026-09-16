// 统一响应信封解析（MD-0003）：成功与错误响应同为 {code, message, data}。
// 错误不得放进成功响应伪装办理成功：2xx 信封的成功码必须形如 MLS-XXX-S001。
const SUCCESS_CODE_PATTERN = /^MLS-[A-Z]+-S\d{3}$/

export class MalformedEnvelopeError extends Error {
  constructor(message) {
    super(message)
    this.name = 'MalformedEnvelopeError'
  }
}

export function isSuccessCode(code) {
  return typeof code === 'string' && SUCCESS_CODE_PATTERN.test(code)
}

export function parseEnvelope(payload) {
  if (payload == null || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new MalformedEnvelopeError('envelope must be a JSON object')
  }
  const { code, message, data } = payload
  if (typeof code !== 'string' || code.length === 0) {
    throw new MalformedEnvelopeError('envelope.code must be a non-empty string')
  }
  if (typeof message !== 'string') {
    throw new MalformedEnvelopeError('envelope.message must be a string')
  }
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new MalformedEnvelopeError('envelope.data must be a JSON object')
  }
  return { code, message, data }
}
