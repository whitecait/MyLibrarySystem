import { beforeEach, describe, expect, it } from 'vitest'

import { getRole, getToken, isAuthenticated, login, logout } from './session'

describe('session state', () => {
  beforeEach(() => {
    logout()
  })

  it('stores login facts and exposes them', () => {
    login({ accessToken: 'opaque-token', expiresAt: '2099-01-01T00:00:00Z', actor: { accountId: '1001', role: 'READER' } })
    expect(getToken()).toBe('opaque-token')
    expect(getRole()).toBe('READER')
    expect(isAuthenticated()).toBe(true)
  })

  it('survives a page reload via sessionStorage', () => {
    login({ accessToken: 'opaque-token', expiresAt: null, actor: { accountId: '1002', role: 'LIBRARIAN' } })
    const raw = sessionStorage.getItem('mls.session.v1')
    expect(JSON.parse(raw)).toEqual({
      token: 'opaque-token',
      expiresAt: null,
      actor: { accountId: '1002', role: 'LIBRARIAN' },
    })
  })

  it('treats an expired token as unauthenticated', () => {
    login({ accessToken: 'stale', expiresAt: '2000-01-01T00:00:00Z', actor: { accountId: '1001', role: 'READER' } })
    expect(isAuthenticated()).toBe(false)
  })

  it('clears everything on logout', () => {
    login({ accessToken: 'opaque-token', actor: { accountId: '1001', role: 'READER' } })
    logout()
    expect(getToken()).toBeNull()
    expect(getRole()).toBeNull()
    expect(isAuthenticated()).toBe(false)
    expect(sessionStorage.getItem('mls.session.v1')).toBeNull()
  })

  it('rejects logins without a token', () => {
    expect(() => login({ accessToken: '', actor: null })).toThrow(/accessToken/)
  })
})
