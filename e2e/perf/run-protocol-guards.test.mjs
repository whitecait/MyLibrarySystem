import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, statSync } from 'node:fs'
import { createHash } from 'node:crypto'

const protocolPath = new URL('./run-protocol.sh', import.meta.url)
const sanitizerPath = new URL('./lib/sanitize-k6-summary.mjs', import.meta.url)
const saturationPath = new URL('./check-saturation.mjs', import.meta.url)
const verdictsPath = new URL('./collect-verdicts.mjs', import.meta.url)
const judgePath = new URL('./judge-attempt.sh', import.meta.url)
const protocol = readFileSync(protocolPath, 'utf8')
const sanitizerHash = createHash('sha256').update(readFileSync(sanitizerPath)).digest('hex')
// Comment blocks flattened to one line so a pinned sentence can be matched
// regardless of where the wrap falls.
const protocolFlat = protocol.replace(/\n#\s*/g, ' ')

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

// --- 2026-09-22 reduced protocol window (lingzz ruling) ----------------------
// The durations are the ruling's numbers: warmup 60s, every stress stage at
// most 300s (steady 300s, retrieval four sub-scenarios 180s each, peak 120s
// ramp + 180s hold, recovery 300s). These tests pin them so a later edit
// cannot silently re-inflate the window the ruling reduced.
const readScenario = (name) => readFileSync(new URL(`./scenarios/${name}.js`, import.meta.url), 'utf8')
const toSeconds = (count, unit) => Number(count) * (unit === 'm' ? 60 : 1)
const durationsOf = (name) => [...readScenario(name).matchAll(/duration:\s*'(\d+)(s|m)'/g)]
  .map((m) => toSeconds(m[1], m[2]))

test('warmup runs the 60-second reduced window', () => {
  assert.deepEqual(durationsOf('warmup'), [60])
  assert.match(readScenario('warmup'), /2026-09-22 reduced protocol window/)
})

test('steady runs four 300-second scenarios', () => {
  assert.deepEqual(durationsOf('steady'), [300, 300, 300, 300])
})

test('retrieval sub-scenarios stay at 180 seconds', () => {
  assert.deepEqual(durationsOf('retrieval'), [180])
})

test('peak holds 180 seconds after a 120-second ramp to 50 VUs', () => {
  const peak = readScenario('peak')
  assert.deepEqual(durationsOf('peak'), [120, 180])
  const stages = [...peak.matchAll(/\{ duration: '(\d+)(s|m)', target: (\d+) \}/g)]
    .map((m) => [toSeconds(m[1], m[2]), Number(m[3])])
  assert.deepEqual(stages, [[120, 50], [180, 50]])
})

test('recovery observes 300 seconds', () => {
  assert.deepEqual(durationsOf('recovery'), [300])
})

test('no measured stress stage exceeds the ruled 300 seconds', () => {
  const over = []
  for (const name of ['warmup', 'steady', 'retrieval', 'peak', 'recovery']) {
    for (const seconds of durationsOf(name)) {
      if (seconds > 300) over.push(`${name}=${seconds}s`)
    }
  }
  assert.deepEqual(over, [])
})

test('run-protocol.sh records the reduced window and its stage comments', () => {
  assert.match(protocolFlat,
    /Protocol window \(2026-09-22 reduced set, lingzz ruling\): warmup 60s, steady 300s, retrieval four sub-scenarios 180s each, peak 120s ramp \+ 180s hold, recovery 300s/)
  assert.match(protocol, /# 1\) warmup 60s \(excluded from statistics\)/)
  assert.match(protocol, /# 2\) steady mixed load 300s/)
  assert.match(protocol, /# 4\) peak 180s hold after a 120s ramp \+ recovery 300s/)
  assert.doesNotMatch(protocol, /warmup 5m|steady mixed load 10m|peak 10m \+ recovery 10m/)
})

test('the reduction left the thresholds and the sampling semantics untouched', () => {
  const saturation = readFileSync(saturationPath, 'utf8')
  assert.match(saturation, /const WINDOW_S = 60\b/)
  assert.match(saturation, /const CPU_LIMIT = 0\.85/)
  assert.match(saturation, /const MEM_LIMIT = 0\.9/)
  assert.match(saturation, /const POOL_LIMIT = 0\.9/)
  assert.match(saturation, /const GC_SHARE_LIMIT = 0\.05/)
  assert.match(saturation, /const LOCK_SHARE_LIMIT = 0\.1/)
  assert.match(saturation,
    /const DIMENSIONS = \['cpu', 'memory', 'gc', 'thread-pool', 'connection-pool', 'db-lock-wait', 'db-deadlock'\]/)
  // a dimension with no samples fails the round instead of reading as clean
  assert.match(saturation, /const pass = !saturated && notEvaluated\.length === 0/)

  const collector = readFileSync(verdictsPath, 'utf8')
  assert.match(collector, /const SAMPLE_MIN = 100\b/)
  assert.match(collector, /insufficientFatal: false/) // sample floor never invalidates a round
  assert.match(collector, /const measured = label !== 'warmup'/)

  const common = readFileSync(new URL('./lib/common.js', import.meta.url), 'utf8')
  assert.match(common, /public_read: \{ p95: 2000, p99: 4000 \}/)
  assert.match(common, /circulation: \{ p95: 3000, p99: 5000 \}/)
  assert.match(common, /payment: \{ p95: 3000, p99: 6000 \}/)
  assert.match(common, /audit: \{ p95: 4000, p99: 8000 \}/)
  assert.match(common, /unexpected_failures: \['count==0'\]/)
})

// --- judge wrapper -----------------------------------------------------------
test('judge-attempt.sh wraps every post-hoc gate of one attempt', () => {
  const judge = readFileSync(judgePath, 'utf8')
  // gate 1: attempt.json validity
  assert.match(judge, /attempt\.json/)
  assert.match(judge, /process\.exit\(a\.valid === true \? 0 : 1\)/)
  assert.match(judge, /attempt-json\.exitcode/)
  // gate 2: collect-verdicts.mjs over a hard-linked run-N view
  assert.match(judge, /node e2e\/perf\/collect-verdicts\.mjs "\$out_dir\/verdicts"/)
  assert.match(judge, /ln -f "\$src"/)
  assert.match(judge, /retrieval-cold-hot retrieval-hot-hot retrieval-cold-tail retrieval-hot-tail/)
  // gate 3: the check-saturation.mjs invocation the protocol itself makes
  assert.match(judge,
    /node e2e\/perf\/check-saturation\.mjs "\$attempt_dir" --out "\$\{out_dir\}\/check-saturation-verdict\.json"/)
  // gate 4: reconcile.mjs with the round driver's argument shapes
  assert.match(judge, /node e2e\/perf\/reconcile\.mjs/)
  assert.match(judge, /--window-start "\$start" --window-end "\$end"/)
  assert.match(judge, /--prom-json "\$prom_json"/)
  assert.match(judge, /--request-log "\$request_log"/)
  assert.match(judge, /--out "\$report"/)
  assert.match(judge, /对账不完整/)
  // aggregate: exit 0 only when every gate passed
  assert.match(judge, /exit "\$worst"/)
  assert.match(judge, /aggregate exit=\$\{worst\}/)
})

test('judge-attempt.sh and its protocol inputs agree on the checker CLI', () => {
  assert.match(protocol,
    /node e2e\/perf\/check-saturation\.mjs "\$attempt_dir" --out "\$\{attempt_dir\}\/saturation-verdict\.json"/)
  assert.match(protocol, /node e2e\/perf\/collect-verdicts\.mjs "\$evidence"/)
})

test('judge-attempt.sh keeps its executable bit', () => {
  const mode = statSync(judgePath).mode
  assert.ok((mode & 0o111) !== 0, 'judge-attempt.sh must stay executable (chmod +x)')
})
