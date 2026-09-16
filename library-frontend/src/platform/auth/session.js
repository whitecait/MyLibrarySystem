// 会话状态（MD-0003）：登录响应 {accessToken, expiresAt, actor} 是唯一会话事实来源。
// 前端不构造权限，只保存与转述服务端返回的 actor；401 时由 onUnauthorized 处理器清理。
import { reactive } from 'vue'

const STORAGE_KEY = 'mls.session.v1'

function readPersisted() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (typeof parsed?.token !== 'string' || parsed.token.length === 0) return null
    return { token: parsed.token, expiresAt: typeof parsed.expiresAt === 'string' ? parsed.expiresAt : null, actor: parsed.actor ?? null }
  } catch {
    return null
  }
}

const state = reactive({ token: null, expiresAt: null, actor: null, ...(readPersisted() ?? {}) })

function persist() {
  if (!state.token) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: state.token, expiresAt: state.expiresAt, actor: state.actor }))
}

export function login({ accessToken, expiresAt = null, actor = null }) {
  if (typeof accessToken !== 'string' || accessToken.length === 0) {
    throw new Error('session login requires a non-empty accessToken')
  }
  state.token = accessToken
  state.expiresAt = expiresAt
  state.actor = actor
  persist()
}

export function logout() {
  state.token = null
  state.expiresAt = null
  state.actor = null
  persist()
}

export function getToken() {
  return state.token
}

export function getRole() {
  return state.actor?.role ?? null
}

export function isAuthenticated() {
  if (!state.token) return false
  if (!state.expiresAt) return true
  const expiresAtMs = Date.parse(state.expiresAt)
  return Number.isNaN(expiresAtMs) ? true : expiresAtMs > Date.now()
}

export function getSessionState() {
  return state
}
