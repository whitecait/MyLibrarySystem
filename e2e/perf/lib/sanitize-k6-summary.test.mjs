import test from 'node:test'
import assert from 'node:assert/strict'
import { sanitizeK6Summary } from './sanitize-k6-summary.mjs'

test('removes dynamic setup_data tokens without changing metrics', () => {
  const readerTokens = Array.from({ length: 10 }, (_, i) => `reader-token-${i}-runtime`)
  const librarianToken = 'librarian-token-runtime'
  const summary = {
    setup_data: { tokens: readerTokens, librarianToken },
    root_group: { checks: { passes: 3, fails: 0 } },
    metrics: {
      http_reqs: { count: 1234 },
      'http_reqs{class:normal_read}': { count: 321 },
      unexpected_failures: { count: 0 },
    },
  }

  const sanitized = sanitizeK6Summary(summary)

  assert.equal(Object.hasOwn(sanitized, 'setup_data'), false)
  assert.deepEqual(sanitized.root_group, summary.root_group)
  assert.deepEqual(sanitized.metrics, summary.metrics)
  const serialized = JSON.stringify(sanitized)
  for (const token of [...readerTokens, librarianToken]) {
    assert.equal(serialized.includes(token), false)
  }
  assert.deepEqual(summary.setup_data, { tokens: readerTokens, librarianToken })
})

test('leaves metric-only summaries structurally unchanged', () => {
  const summary = { root_group: {}, metrics: { http_reqs: { count: 0 } } }
  assert.deepEqual(sanitizeK6Summary(summary), summary)
})

test('rejects non-object summaries instead of emitting unverifiable evidence', () => {
  assert.throws(() => sanitizeK6Summary(null), /JSON object/)
  assert.throws(() => sanitizeK6Summary([]), /JSON object/)
})
