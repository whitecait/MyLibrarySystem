import { describe, expect, it } from 'vitest'

import { BASE_PATH, buildPath, buildUrl, getOperation, listOperations, matchOperation } from './operations'

describe('generated operations manifest', () => {
  it('covers 45 unique operations with 31 idempotent writes', () => {
    const operations = listOperations()
    expect(operations).toHaveLength(45)
    expect(new Set(operations.map((operation) => operation.operationId)).size).toBe(45)
    expect(operations.filter((operation) => operation.requiresIdempotencyKey)).toHaveLength(31)
    expect(operations.filter((operation) => operation.method === 'GET' && operation.requiresIdempotencyKey)).toHaveLength(0)
  })

  it('marks exactly the seven public operations from the contract', () => {
    const publicIds = listOperations()
      .filter((operation) => operation.public)
      .map((operation) => operation.operationId)
      .sort()
    expect(publicIds).toEqual(
      [
        'createSession',
        'getReadingContent',
        'getReservationCalendar',
        'getTitle',
        'registerReader',
        'searchTitles',
        'startAiSession',
      ].sort(),
    )
  })

  it('carries permission metadata for every operation', () => {
    for (const operation of listOperations()) {
      expect(operation.permission, `${operation.operationId} lacks x-permission`).toBeTruthy()
    }
  })

  it('builds URLs from contract templates only', () => {
    expect(BASE_PATH).toBe('/api/v1')
    expect(buildPath('getTitle', { titleId: '2001' })).toBe('/titles/2001')
    expect(buildPath('getOrder', { orderId: '6001' })).toBe('/orders/6001')
    expect(buildUrl('searchTitles', { query: { q: 'database', limit: 20 } })).toBe('/api/v1/titles?q=database&limit=20')
    expect(() => buildPath('getTitle')).toThrow(/titleId/)
    expect(() => buildPath('not-an-operation')).toThrow(/unknown operationId/)
  })

  it('matches scenario request paths back to operations', () => {
    expect(matchOperation('GET', '/api/v1/titles?q=database&limit=20')?.operationId).toBe('searchTitles')
    expect(matchOperation('POST', '/api/v1/fines/5001/orders')?.operationId).toBe('createOrder')
    expect(matchOperation('POST', '/api/v1/ai/sessions/7001/messages')?.operationId).toBe('sendAiMessage')
    expect(matchOperation('GET', '/api/v1/definitely/not/there')).toBeNull()
  })

  it('looks operations up by id', () => {
    const searchTitles = getOperation('searchTitles')
    expect(searchTitles.method).toBe('GET')
    expect(searchTitles.path).toBe('/titles')
    expect(getOperation('nope')).toBeNull()
  })
})
