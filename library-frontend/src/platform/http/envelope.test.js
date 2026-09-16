import { describe, expect, it } from 'vitest'

import { MalformedEnvelopeError, isSuccessCode, parseEnvelope } from './envelope'

describe('unified envelope parsing', () => {
  it('parses a well-formed envelope', () => {
    const envelope = parseEnvelope({ code: 'MLS-CAT-S001', message: 'ok', data: { items: [] } })
    expect(envelope).toEqual({ code: 'MLS-CAT-S001', message: 'ok', data: { items: [] } })
  })

  it.each([
    ['null payload', null],
    ['array payload', []],
    ['missing code', { message: 'ok', data: {} }],
    ['empty code', { code: '', message: 'ok', data: {} }],
    ['non-string message', { code: 'MLS-CAT-S001', message: 1, data: {} }],
    ['missing data', { code: 'MLS-CAT-S001', message: 'ok' }],
    ['array data', { code: 'MLS-CAT-S001', message: 'ok', data: [] }],
  ])('rejects %s', (_label, payload) => {
    expect(() => parseEnvelope(payload)).toThrow(MalformedEnvelopeError)
  })

  it('recognizes module success codes only', () => {
    expect(isSuccessCode('MLS-CAT-S001')).toBe(true)
    expect(isSuccessCode('MLS-AI-S001')).toBe(true)
    expect(isSuccessCode('MLS-PAY-001')).toBe(false)
    expect(isSuccessCode('MLS-ID-S1')).toBe(false)
    expect(isSuccessCode(42)).toBe(false)
  })
})
