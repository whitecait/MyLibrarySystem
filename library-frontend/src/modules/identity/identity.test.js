import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import * as identity from './index'

afterEach(() => {
  setTransport(null)
})

function recordingTransport() {
  const calls = []
  setTransport({
    mode: 'mock',
    async request(operationId, options = {}) {
      calls.push({ operationId, options })
      return { code: 'MLS-T-S001', message: 'ok', data: {} }
    },
  })
  return calls
}

describe('identity module facade', () => {
  it('register posts the registration body', async () => {
    const calls = recordingTransport()
    await identity.register({ username: 'reader01', password: 'password1', displayName: 'Reader 01' })
    expect(calls[0].operationId).toBe('registerReader')
    expect(calls[0].options.body).toEqual({ username: 'reader01', password: 'password1', displayName: 'Reader 01' })
  })

  it('createSession posts credentials', async () => {
    const calls = recordingTransport()
    await identity.createSession({ username: 'reader01', password: 'password1' })
    expect(calls[0].operationId).toBe('createSession')
    expect(calls[0].options.body).toEqual({ username: 'reader01', password: 'password1' })
  })

  it('endCurrentSession calls the contract operation without a body', async () => {
    const calls = recordingTransport()
    await identity.endCurrentSession()
    expect(calls[0].operationId).toBe('endCurrentSession')
    expect(calls[0].options.body).toBeUndefined()
  })

  it('listAccounts maps filters to query and skips empty values', async () => {
    const calls = recordingTransport()
    await identity.listAccounts({ role: 'READER', status: 'ACTIVE', limit: 20 })
    expect(calls[0].operationId).toBe('listAccounts')
    expect(calls[0].options.query).toEqual({ role: 'READER', status: 'ACTIVE', cursor: undefined, limit: 20 })
  })

  it('requestAccountChange targets the account and carries action payload and reason', async () => {
    const calls = recordingTransport()
    await identity.requestAccountChange({ accountId: '1001', action: 'CHANGE_ROLE', payload: { role: 'LIBRARIAN' }, reason: '转岗' })
    expect(calls[0].operationId).toBe('requestAccountChange')
    expect(calls[0].options.params).toEqual({ accountId: '1001' })
    expect(calls[0].options.body).toEqual({ action: 'CHANGE_ROLE', payload: { role: 'LIBRARIAN' }, reason: '转岗' })
  })

  it('resetPassword and changeReaderStatus target their resources', async () => {
    const calls = recordingTransport()
    await identity.resetPassword({ accountId: '1001', reason: '遗忘密码' })
    await identity.changeReaderStatus({ readerId: '1001', status: 'SUSPENDED', reason: '违规' })
    expect(calls[0].operationId).toBe('resetPassword')
    expect(calls[0].options.body).toEqual({ reason: '遗忘密码' })
    expect(calls[1].operationId).toBe('changeReaderStatus')
    expect(calls[1].options.body).toEqual({ status: 'SUSPENDED', reason: '违规' })
  })

  it('exposes the module views through the public entry', () => {
    expect(identity.LoginView).toBeTruthy()
    expect(identity.RegisterView).toBeTruthy()
    expect(identity.AccountAdminView).toBeTruthy()
  })
})
