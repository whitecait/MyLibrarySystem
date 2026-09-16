import { beforeEach, describe, expect, it } from 'vitest'

import { clearContinuation, consumeContinuation, peekContinuation, saveContinuation } from './continuation'

describe('login continuation intent', () => {
  beforeEach(() => {
    clearContinuation()
  })

  it('saves only intent, path and params', () => {
    saveContinuation({ intent: 'navigate', path: '/me/business', params: null })
    expect(peekContinuation()).toEqual({ intent: 'navigate', path: '/me/business', params: null })
    const raw = JSON.parse(sessionStorage.getItem('mls.continuation.v1'))
    expect(Object.keys(raw).sort()).toEqual(['intent', 'params', 'path'])
  })

  it('consume returns the intent and clears it', () => {
    saveContinuation({ intent: 'reauthenticate', path: '/titles/2001', params: null })
    expect(consumeContinuation()).toEqual({ intent: 'reauthenticate', path: '/titles/2001', params: null })
    expect(peekContinuation()).toBeNull()
    expect(sessionStorage.getItem('mls.continuation.v1')).toBeNull()
  })

  it('ignores persisted values with an unexpected shape', () => {
    sessionStorage.setItem('mls.continuation.v1', JSON.stringify({ role: 'ADMIN', token: 'x' }))
    expect(peekContinuation()).toBeNull()
    sessionStorage.setItem('mls.continuation.v1', 'not-json')
    expect(peekContinuation()).toBeNull()
  })

  it('requires string intent and path', () => {
    expect(() => saveContinuation({ intent: null, path: '/x' })).toThrow(/intent/)
    expect(() => saveContinuation({ intent: 'navigate' })).toThrow(/path/)
  })
})
