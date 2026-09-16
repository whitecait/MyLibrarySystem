import { describe, expect, it } from 'vitest'

import { CONTRACT_SAMPLE_SCHEMA_VERSION, getScenario, listScenarioIds, listScenarios } from './index'

const EXPECTED_IDS = [
  'AUTH-REGISTER-SUCCESS',
  'CATALOG-PUBLIC-SEARCH',
  'RESERVATION-WAITLIST-CONFIRMATION',
  'WALKIN-MIXED-BATCH',
  'RETURN-WITH-FINE',
  'ORDER-CONCURRENT-CONFLICT',
  'REVIEW-TARGET-CHANGED',
  'AI-L2-PREFILL',
  'AI-QUOTA-EXHAUSTED',
]

describe('contract scenario samples', () => {
  it('exposes the nine approved scenarios', () => {
    expect(listScenarioIds()).toEqual(EXPECTED_IDS)
    expect(listScenarios()).toHaveLength(9)
    expect(CONTRACT_SAMPLE_SCHEMA_VERSION).toBe('0.2')
  })

  it.each(EXPECTED_IDS)('%s carries a request and a contract envelope response', (id) => {
    const scenario = getScenario(id)
    expect(scenario.request.method).toMatch(/^(GET|POST|PATCH|DELETE)$/)
    expect(scenario.request.path).toMatch(/^\/api\/v1\//)
    expect(scenario.response.httpStatus).toBeGreaterThanOrEqual(200)
    expect(typeof scenario.response.body.code).toBe('string')
    expect(typeof scenario.response.body.message).toBe('string')
    expect(scenario.response.body.data).toBeTypeOf('object')
    expect(Array.isArray(scenario.expect)).toBe(true)
    expect(scenario.expect.length).toBeGreaterThan(0)
  })

  it('fails fast on unknown scenario ids', () => {
    expect(() => getScenario('NOT-A-SCENARIO')).toThrow(/unknown contract scenario/)
  })
})
