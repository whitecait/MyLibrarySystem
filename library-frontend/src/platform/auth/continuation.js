// 登录续行意图（MD-0003 / FR-0042）：只保存 {intent, path, params}，
// 不得保存权限结论、锁、副本占用或任何已通过的业务判定；登录后消费并重载显示数据。
const STORAGE_KEY = 'mls.continuation.v1'

function isValidContinuation(value) {
  return (
    value != null &&
    typeof value === 'object' &&
    typeof value.intent === 'string' &&
    typeof value.path === 'string' &&
    (value.params === null || typeof value.params === 'object')
  )
}

export function saveContinuation({ intent, path, params = null }) {
  if (typeof intent !== 'string' || typeof path !== 'string') {
    throw new Error('continuation requires string intent and path')
  }
  const continuation = { intent, path, params }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(continuation))
  return continuation
}

export function peekContinuation() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return isValidContinuation(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function consumeContinuation() {
  const continuation = peekContinuation()
  sessionStorage.removeItem(STORAGE_KEY)
  return continuation
}

export function clearContinuation() {
  sessionStorage.removeItem(STORAGE_KEY)
}
