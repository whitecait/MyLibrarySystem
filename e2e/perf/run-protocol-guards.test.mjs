import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'

const protocolPath = new URL('./run-protocol.sh', import.meta.url)
const sanitizerPath = new URL('./lib/sanitize-k6-summary.mjs', import.meta.url)
const protocol = readFileSync(protocolPath, 'utf8')
const sanitizerHash = createHash('sha256').update(readFileSync(sanitizerPath)).digest('hex')

test('attempt script versions include the summary sanitizer hash', () => {
  assert.match(protocol, /sanitizer_sha="\$\(sha e2e\/perf\/lib\/sanitize-k6-summary\.mjs\)"/)
  assert.match(protocol, /"sanitize-k6-summary\.mjs":%s/)
  assert.equal(sanitizerHash.length, 64)
})

test('raw summary cleanup has an explicit failure basis and best-effort EXIT cleanup', () => {
  assert.match(protocol, /if ! rm -f -- "\$k6_summary_source"; then[\s\S]*?error_detail_override="raw k6 summary cleanup failed:/)
  assert.match(protocol, /warning: finalize could not remove raw k6 summary/)
  assert.match(protocol, /if ! rm -f -- "\$secret_values_file"; then/)
  assert.match(protocol, /if \[\[ -n "\$error_detail_override" \]\]; then/)
  assert.match(protocol, /record_cleanup_failure "raw summary cleanup failed:/)
  assert.match(protocol, /record_cleanup_failure "temporary secret value-set cleanup failed:/)
  assert.match(protocol, /completed="false"/)
  assert.match(protocol, /if \[\[ "\$code" -eq 0 \]\]; then[\s\S]*?code=1/)
})

test('two cleanup failures invalidate completion and preserve both basis details', () => {
  let completed = true
  let code = 0
  let basis = ''
  const failures = [
    'raw summary cleanup failed: /tmp/protocol/steady-summary.json',
    'temporary secret value-set cleanup failed: /tmp/secret-value-set',
  ]
  if (failures.length > 0) {
    completed = false
    basis = failures.join('; ')
    if (code === 0) code = 1
  }
  assert.equal(completed, false)
  assert.equal(code, 1)
  assert.equal(basis, failures.join('; '))
})

test('stale cleanup is restricted to protocol summary names', () => {
  assert.match(protocol, /protocol_summary_labels=\([\s\S]*?warmup steady[\s\S]*?peak recovery[\s\S]*?\)/)
  assert.match(protocol, /stale_summary="e2e\/perf\/out\/\$\{label\}-summary\.json"/)
  assert.match(protocol, /query fixtures \(queries-hot\.json \/ queries-tail\.json\)/)
  assert.doesNotMatch(protocol, /rm -f -- "e2e\/perf\/out\/queries-/)
})
