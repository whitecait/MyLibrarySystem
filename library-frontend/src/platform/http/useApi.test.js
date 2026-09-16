import { describe, expect, it } from 'vitest'

import { FailureKind, RequestFailure } from './errors'
import { useApiRequest } from './useApi'

describe('useApiRequest', () => {
  it('walks idle → loading → success and stores the envelope', async () => {
    const { state, run } = useApiRequest()
    expect(state.status).toBe('idle')
    const pending = run(async () => ({ code: 'MLS-T-S001', message: 'ok', data: {} }))
    expect(state.status).toBe('loading')
    const envelope = await pending
    expect(envelope.code).toBe('MLS-T-S001')
    expect(state.status).toBe('success')
    expect(state.envelope.data).toEqual({})
    expect(state.failure).toBeNull()
  })

  it('keeps typed failures observable for the page', async () => {
    const { state, run } = useApiRequest()
    const result = await run(async () => {
      throw new RequestFailure({ kind: FailureKind.CONFLICT, status: 409, code: 'MLS-CAT-003', message: 'version conflict' })
    })
    expect(result).toBeNull()
    expect(state.status).toBe('failure')
    expect(state.failure.code).toBe('MLS-CAT-003')
    expect(state.envelope).toBeNull()
  })

  it('wraps unexpected errors as system failures', async () => {
    const { state, run } = useApiRequest()
    await run(async () => {
      throw new Error('boom')
    })
    expect(state.status).toBe('failure')
    expect(state.failure.kind).toBe(FailureKind.SYSTEM)
  })

  it('reset returns to the idle state', async () => {
    const { state, run, reset } = useApiRequest()
    await run(async () => ({ code: 'MLS-T-S001', message: 'ok', data: {} }))
    reset()
    expect(state.status).toBe('idle')
    expect(state.envelope).toBeNull()
  })
})
