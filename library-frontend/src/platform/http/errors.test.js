import { describe, expect, it } from 'vitest'

import { FailureKind, RequestFailure, failureFromHttpStatus, failureKindForStatus } from './errors'

describe('request failure taxonomy', () => {
  it.each([
    [400, FailureKind.INVALID],
    [401, FailureKind.UNAUTHENTICATED],
    [403, FailureKind.FORBIDDEN],
    [404, FailureKind.NOT_FOUND],
    [409, FailureKind.CONFLICT],
    [422, FailureKind.REJECTED],
    [429, FailureKind.QUOTA_EXHAUSTED],
    [500, FailureKind.SYSTEM],
    [503, FailureKind.SYSTEM],
  ])('maps HTTP %i to %s', (status, kind) => {
    expect(failureKindForStatus(status)).toBe(kind)
  })

  it('builds a failure from an error envelope body', () => {
    const failure = failureFromHttpStatus(409, {
      code: 'MLS-PAY-001',
      message: 'fine already has an unreleased order',
      data: { fineId: '5001' },
    })
    expect(failure).toBeInstanceOf(RequestFailure)
    expect(failure.kind).toBe(FailureKind.CONFLICT)
    expect(failure.status).toBe(409)
    expect(failure.code).toBe('MLS-PAY-001')
    expect(failure.message).toBe('fine already has an unreleased order')
    expect(failure.data).toEqual({ fineId: '5001' })
  })

  it('falls back to generic failure facts for non-envelope bodies', () => {
    const failure = failureFromHttpStatus(500, null)
    expect(failure.kind).toBe(FailureKind.SYSTEM)
    expect(failure.code).toBeNull()
    expect(failure.message).toBe('HTTP 500')
  })
})
