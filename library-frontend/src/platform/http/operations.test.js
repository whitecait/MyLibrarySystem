import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import process from 'node:process'
import { describe, expect, it } from 'vitest'
import { parse } from 'yaml'

import { BASE_PATH, buildPath, buildUrl, getOperation, listOperations, matchOperation } from './operations'

describe('generated operations manifest', () => {
  it('covers 46 unique operations with 29 idempotent writes', () => {
    const operations = listOperations()
    expect(operations).toHaveLength(46)
    expect(new Set(operations.map((operation) => operation.operationId)).size).toBe(46)
    expect(operations.filter((operation) => operation.requiresIdempotencyKey)).toHaveLength(29)
    expect(operations.filter((operation) => operation.method === 'GET' && operation.requiresIdempotencyKey)).toHaveLength(0)
  })

  it('marks exactly the nine anonymous operations from the contract', () => {
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
        'reconcileBusinessDate',
        'registerReader',
        'requestRefund',
        'searchTitles',
        'startAiSession',
      ].sort(),
    )
  })

  it('keeps retired settlement endpoints marked as unsupported compatibility paths', () => {
    const contract = parse(readFileSync(
      resolve(process.cwd(), '../docs/04-module-design/contracts/http/openapi.yaml'),
      'utf8',
    ))
    const retiredOperations = [
      { operationId: 'requestRefund', route: '/orders/{orderId}/refunds' },
      { operationId: 'reconcileBusinessDate', route: '/reconciliations' },
    ]

    for (const { operationId, route } of retiredOperations) {
      const contractOperation = contract.paths[route].post
      expect(contractOperation).toMatchObject({
        deprecated: true,
        security: [],
        'x-permission': 'UNSUPPORTED',
      })
      expect(contractOperation.parameters ?? []).toEqual([])

      expect(getOperation(operationId)).toMatchObject({
        deprecated: true,
        permission: 'UNSUPPORTED',
        public: true,
        requiresIdempotencyKey: false,
      })
    }
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
