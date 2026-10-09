import { ref } from 'vue'

export const loginPromptKey = Symbol('loginPrompt')

// The form stays mounted while one authentication request is pending.
export function createLoginPrompt() {
  const open = ref(false)
  let pending = null
  let resolveRequest = null
  function request() {
    if (pending) return pending
    pending = new Promise(resolve => { resolveRequest = resolve })
    open.value = true
    return pending
  }
  function finish(authenticated = false) {
    open.value = false
    const resolve = resolveRequest
    pending = resolveRequest = null
    resolve?.(authenticated)
  }
  return { open, request, finish }
}
