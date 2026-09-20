import { afterEach, describe, expect, it, vi } from 'vitest'

import { login, logout } from '../auth/session'
import { getTransport, setTransport } from './index'

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('default transport session wiring (DEF-M6-001)', () => {
  afterEach(() => {
    logout()
    setTransport(null)
    vi.unstubAllGlobals()
  })

  it('attaches the current session Bearer token to real requests', async () => {
    const calls = []
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
      calls.push({ url, init })
      return jsonResponse(200, { code: 'MLS-CAT-S001', message: 'ok', data: { items: [] } })
    }))
    login({ accessToken: 'token-wired-1', actor: { role: 'READER' } })
    await getTransport().request('searchTitles', { query: { q: 'x', limit: 5 } })
    expect(calls[0].init.headers.get('Authorization')).toBe('Bearer token-wired-1')
  })

  it('sends no Authorization header when not signed in', async () => {
    const calls = []
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
      calls.push({ url, init })
      return jsonResponse(200, { code: 'MLS-CAT-S001', message: 'ok', data: { items: [] } })
    }))
    await getTransport().request('searchTitles', { query: { q: 'x', limit: 5 } })
    expect(calls[0].init.headers.get('Authorization')).toBeNull()
  })

  it('reads the token at request time, not at transport creation time', async () => {
    const calls = []
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
      calls.push({ url, init })
      return jsonResponse(200, { code: 'MLS-CAT-S001', message: 'ok', data: { items: [] } })
    }))
    await getTransport().request('searchTitles', { query: { q: 'x', limit: 5 } })
    login({ accessToken: 'token-after-create', actor: { role: 'READER' } })
    await getTransport().request('searchTitles', { query: { q: 'y', limit: 5 } })
    expect(calls[0].init.headers.get('Authorization')).toBeNull()
    expect(calls[1].init.headers.get('Authorization')).toBe('Bearer token-after-create')
  })
})
