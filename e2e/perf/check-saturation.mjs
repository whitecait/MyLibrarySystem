// Saturation verdict over the samples taken by sample-resources.sh
// (RA-0013 §11 thresholds, 60-second judgement windows; any violation marks
// the round "环境饱和、不通过" and names the saturated dimensions).
//
//   node e2e/perf/check-saturation.mjs <samples-dir> --out verdict.json
//
// Quotas follow the acceptance plan §4.1 (cpus in cores, memory in MiB).
//
// Verdict semantics (R15 revision):
//   - Sample timestamps are ISO8601 UTC ("2026-09-20T13:59:26Z") and are
//     parsed with Date.parse as-is; an unparseable timestamp aborts the
//     check. (The old replace(' ','T')+'Z' turned the already-"Z"
//     timestamps into "...ZZ" -> NaN, so every sample collapsed into one
//     "NaN" window and each 60s judgement window was silently the whole
//     run average.)
//   - Every dimension is reported. A dimension with no samples at all is
//     "not_evaluated" and the overall conclusion is "fail": a collection
//     gap must not read as a pass. (The old code skipped its tomcat block
//     whenever no tomcat_threads_* lines existed.)
//   - Full-GC seconds per window are the first-to-last delta of
//     jvm_gc_pause_seconds_sum series whose action label is
//     "end of major GC" — the value as actually emitted (the old
//     'end_of_major_gc' never matched). _sum is cumulative, so adding the
//     raw per-sample values (old behaviour) scaled the share with the
//     sample count instead of the pause time.
//   - Pool usage is the window AVERAGE of busy/active over the pool max
//     (the old Math.max let a single spike stand for the whole window).
//   - db lock-wait seconds accumulate waiter counts across the ACTUAL time
//     delta between adjacent samples. The sampler loop runs at ~7s (6-8s
//     observed) because docker stats/psql/curl execute inside the loop, so
//     the old fixed *5 mis-scaled the wait time.
//   - Counter regressions (a cumulative value goes down: process restart,
//     OOM kill, or counter reset) mark the affected window invalid; such
//     windows are registered under invalidWindows and excluded from the
//     verdict instead of counting as violations or as clean.
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'

const samplesDir = resolve(process.argv[2])
const outIndex = process.argv.indexOf('--out')
const outFile = outIndex > -1 ? resolve(process.argv[outIndex + 1]) : join(samplesDir, 'saturation-verdict.json')

const QUOTAS = {
  backend: { cpus: 1.5, memMiB: 2560 },
  database: { cpus: 1, memMiB: 2048 },
  frontend: { cpus: 0.25, memMiB: 256 },
  k6: { cpus: 0.25, memMiB: 512 },
  prometheus: { cpus: 0.5, memMiB: 1024 },
  grafana: { cpus: 0.25, memMiB: 512 },
  'otel-collector': { cpus: 0.25, memMiB: 512 },
}
const WINDOW_S = 60
const CPU_LIMIT = 0.85 // of quota
const MEM_LIMIT = 0.9 // of quota
const POOL_LIMIT = 0.9
const GC_SHARE_LIMIT = 0.05
const LOCK_SHARE_LIMIT = 0.1 // of the 60s window

const parsePct = (s) => parseFloat(String(s).replace('%', '')) || 0
const parseMem = (s) => {
  const m = String(s).match(/^([\d.]+)(KiB|MiB|GiB)/)
  if (!m) return 0
  const n = parseFloat(m[1])
  return m[2] === 'GiB' ? n * 1024 : m[2] === 'KiB' ? n / 1024 : n
}
const avg = (a) => a.reduce((x, y) => x + y, 0) / a.length

// --- timestamps / windows -------------------------------------------------------
// Samples are ISO8601 UTC ("2026-09-20T13:59:26Z"): Date.parse as-is. The
// legacy "YYYY-MM-DD HH:MM:SS" shape is tolerated without appending a second
// zone designator. Unparseable timestamps are a data defect — abort loudly.
const toMs = (ts) => {
  let s = String(ts).trim()
  if (s.includes(' ')) s = s.replace(' ', 'T')
  if (!/(?:[Zz]|[+-]\d{2}:?\d{2})$/.test(s)) s += 'Z'
  const ms = Date.parse(s)
  if (Number.isNaN(ms)) throw new Error(`unparseable sample timestamp: ${JSON.stringify(String(ts))}`)
  return ms
}
const windowKey = (ts) => Math.floor(toMs(ts) / (WINDOW_S * 1000))
const windowStart = (key) => new Date(key * WINDOW_S * 1000).toISOString()

// --- verdict bookkeeping --------------------------------------------------------
const DIMENSIONS = ['cpu', 'memory', 'gc', 'thread-pool', 'connection-pool', 'db-lock-wait', 'db-deadlock']
const dims = new Map(DIMENSIONS.map((n) => [n, { status: 'not_evaluated', samples: 0, windowsEvaluated: 0, invalidWindows: 0 }]))
const notEvaluatedReasons = {}
const violations = []
const invalidWindows = []
const evaluate = (name, samples, windows) => {
  const d = dims.get(name)
  d.status = 'evaluated'
  d.samples += samples
  d.windowsEvaluated += windows
}

// --- docker stats: cpu / memory --------------------------------------------------
let stats = []
try {
  stats = readFileSync(join(samplesDir, 'docker-stats.csv'), 'utf8')
    .split(/\r?\n/).slice(1).filter(Boolean)
    .map((line) => {
      const [ts, container, cpu, memUsed] = line.split(',')
      return { ts, container, cpu: parsePct(cpu), memMiB: parseMem(memUsed) }
    })
} catch (err) {
  if (err.code !== 'ENOENT') throw err
  notEvaluatedReasons.cpu = 'docker-stats.csv missing'
  notEvaluatedReasons.memory = 'docker-stats.csv missing'
}
if (stats.length === 0) {
  notEvaluatedReasons.cpu ??= 'docker-stats.csv has no rows'
  notEvaluatedReasons.memory ??= 'docker-stats.csv has no rows'
} else {
  const svc = (container) => Object.keys(QUOTAS).find((s) => container.includes(`-${s}-`) || container.endsWith(`-${s}`)) ?? null
  const windows = new Map() // `${key}|${service}` -> { key, name, cpu[], mem[] }
  const keys = new Set()
  for (const s of stats) {
    const name = svc(s.container)
    if (!name) continue
    const key = windowKey(s.ts)
    keys.add(key)
    const id = `${key}|${name}`
    const w = windows.get(id) ?? { key, name, cpu: [], mem: [] }
    w.cpu.push(s.cpu)
    w.mem.push(s.memMiB)
    windows.set(id, w)
  }
  for (const w of windows.values()) {
    const q = QUOTAS[w.name]
    const cpuPct = (avg(w.cpu) / 100) / q.cpus
    const memPct = avg(w.mem) / q.memMiB
    if (cpuPct >= CPU_LIMIT) violations.push({ dimension: 'cpu', container: w.name, window: w.key, windowStart: windowStart(w.key), ratio: +cpuPct.toFixed(3), limit: CPU_LIMIT })
    if (memPct >= MEM_LIMIT) violations.push({ dimension: 'memory', container: w.name, window: w.key, windowStart: windowStart(w.key), ratio: +memPct.toFixed(3), limit: MEM_LIMIT })
  }
  evaluate('cpu', stats.length, keys.size)
  evaluate('memory', stats.length, keys.size)
}

// --- db samples: lock waits / deadlocks -------------------------------------------
let dbRows = []
try {
  dbRows = readFileSync(join(samplesDir, 'db-samples.csv'), 'utf8')
    .split(/\r?\n/).slice(1).filter(Boolean)
    .map((line) => { const [ts, waiters, deadlocks] = line.split(','); return { ts, waiters: parseInt(waiters, 10), deadlocks: parseInt(deadlocks, 10) } })
} catch (err) {
  if (err.code !== 'ENOENT') throw err
  notEvaluatedReasons['db-lock-wait'] = 'db-samples.csv missing'
  notEvaluatedReasons['db-deadlock'] = 'db-samples.csv missing'
}
if (dbRows.length === 0) {
  notEvaluatedReasons['db-lock-wait'] ??= 'db-samples.csv has no rows'
  notEvaluatedReasons['db-deadlock'] ??= 'db-samples.csv has no rows'
} else if (dbRows.every((r) => Number.isNaN(r.waiters) && Number.isNaN(r.deadlocks))) {
  notEvaluatedReasons['db-lock-wait'] = 'db-samples.csv has no parseable rows'
  notEvaluatedReasons['db-deadlock'] = 'db-samples.csv has no parseable rows'
} else if (dbRows.length < 2) {
  notEvaluatedReasons['db-lock-wait'] = 'only one sample: no interval to judge'
  notEvaluatedReasons['db-deadlock'] = 'only one sample: no interval to judge'
} else {
  dbRows.sort((a, b) => toMs(a.ts) - toMs(b.ts))
  const lockByWindow = new Map() // key -> accumulated wait seconds
  const keys = new Set()
  for (let i = 1; i < dbRows.length; i++) {
    const prev = dbRows[i - 1]
    const cur = dbRows[i]
    const key = windowKey(cur.ts)
    keys.add(key)
    // Waiter state observed at the previous sample is taken to hold across
    // [prev, cur); the interval is the sampler's real loop period (~6-8s),
    // not a nominal 5s.
    if (!Number.isNaN(prev.waiters) && !Number.isNaN(cur.waiters)) {
      lockByWindow.set(key, (lockByWindow.get(key) ?? 0) + prev.waiters * ((toMs(cur.ts) - toMs(prev.ts)) / 1000))
    }
    // pg_stat_database.deadlocks is cumulative: an increase between adjacent
    // samples is a deadlock; a decrease is a counter reset (e.g. the
    // retrieval scenario's deliberate database restart) -> invalid window.
    if (!Number.isNaN(prev.deadlocks) && !Number.isNaN(cur.deadlocks)) {
      if (cur.deadlocks > prev.deadlocks) {
        violations.push({ dimension: 'db-deadlock', window: key, windowStart: windowStart(key), from: prev.deadlocks, to: cur.deadlocks })
      } else if (cur.deadlocks < prev.deadlocks) {
        invalidWindows.push({ dimension: 'db-deadlock', window: key, windowStart: windowStart(key), reason: 'deadlocks counter regression (counter reset or database restart); interval excluded', from: prev.deadlocks, to: cur.deadlocks })
        dims.get('db-deadlock').invalidWindows += 1
      }
    }
  }
  for (const [key, waitSeconds] of lockByWindow) {
    if (waitSeconds >= LOCK_SHARE_LIMIT * WINDOW_S) {
      violations.push({ dimension: 'db-lock-wait', window: key, windowStart: windowStart(key), waitSeconds: +waitSeconds.toFixed(1), limitSeconds: LOCK_SHARE_LIMIT * WINDOW_S })
    }
  }
  evaluate('db-lock-wait', dbRows.length, keys.size)
  evaluate('db-deadlock', dbRows.length, keys.size)
}

// --- metrics log: gc / thread pool / connection pool -------------------------------
let metricFile = null
let blocks = []
try {
  const metricFiles = readdirSync(samplesDir).filter((f) => f.startsWith('metrics-')).sort()
  metricFile = metricFiles[0] ?? null
  if (metricFile) blocks = readFileSync(join(samplesDir, metricFile), 'utf8').split('# SAMPLE ').slice(1)
} catch (err) {
  if (err.code !== 'ENOENT') throw err
}
if (!metricFile) {
  notEvaluatedReasons.gc = 'no metrics-*.log in samples dir'
  notEvaluatedReasons['thread-pool'] = 'no metrics-*.log in samples dir'
  notEvaluatedReasons['connection-pool'] = 'no metrics-*.log in samples dir'
} else if (blocks.length === 0) {
  notEvaluatedReasons.gc = `${metricFile} has no "# SAMPLE" blocks`
  notEvaluatedReasons['thread-pool'] = `${metricFile} has no "# SAMPLE" blocks`
  notEvaluatedReasons['connection-pool'] = `${metricFile} has no "# SAMPLE" blocks`
} else {
  const gcByWindowSeries = new Map() // `${key}|${labels}` -> { key, series, first, last }
  const poolByWindow = new Map() // key -> { busy[], tomcatMax[], hikActive[], hikMax[] }
  const keys = new Set()
  const lastGcSum = new Map() // labels -> previous block's cumulative _sum
  let hasGcLines = false
  let hasTomcat = false
  let hasHikari = false
  for (const block of blocks) {
    const lines = block.split(/\r?\n/)
    const ts = lines[0].trim()
    const key = windowKey(ts)
    keys.add(key)
    for (const l of lines) {
      const gc = l.match(/^jvm_gc_pause_seconds_sum\{(.*)\}\s+(\S+)\s*$/)
      if (gc) {
        hasGcLines = true
        const [, labels, raw] = gc
        const value = parseFloat(raw)
        const prevBlock = lastGcSum.get(labels)
        if (prevBlock !== undefined && value < prevBlock) {
          // cumulative _sum went down between adjacent blocks: process
          // restart / OOM kill / counter reset -> the window around it is
          // invalid and excluded from the verdict
          invalidWindows.push({ dimension: 'gc', window: key, windowStart: windowStart(key), reason: 'jvm_gc_pause_seconds_sum regression between samples (counter reset or process restart); excluded', series: labels, from: prevBlock, to: value })
          dims.get('gc').invalidWindows += 1
        }
        lastGcSum.set(labels, value)
        if (!labels.includes('action="end of major GC"')) continue
        const id = `${key}|${labels}`
        const g = gcByWindowSeries.get(id) ?? { key, series: labels, first: value, last: value }
        g.last = value
        gcByWindowSeries.set(id, g)
        continue
      }
      if (l.startsWith('tomcat_threads_busy{') || l.startsWith('tomcat_threads_config_max{')) {
        hasTomcat = true
        const w = poolByWindow.get(key) ?? { key, busy: [], tomcatMax: [], hikActive: [], hikMax: [] }
        const v = parseFloat(l.split(' ').pop()) || 0
        if (l.startsWith('tomcat_threads_busy{')) w.busy.push(v)
        else w.tomcatMax.push(v)
        poolByWindow.set(key, w)
        continue
      }
      if (l.startsWith('hikaricp_connections_active{') || l.startsWith('hikaricp_connections_max{')) {
        hasHikari = true
        const w = poolByWindow.get(key) ?? { key, busy: [], tomcatMax: [], hikActive: [], hikMax: [] }
        const v = parseFloat(l.split(' ').pop()) || 0
        if (l.startsWith('hikaricp_connections_active{')) w.hikActive.push(v)
        else w.hikMax.push(v)
        poolByWindow.set(key, w)
      }
    }
  }

  // full-GC pause seconds per window: per-series first-to-last delta of the
  // cumulative _sum (single-sample windows yield delta 0 — nothing observable)
  const gcSecondsByWindow = new Map()
  for (const g of gcByWindowSeries.values()) {
    const delta = g.last - g.first
    if (delta < 0) {
      invalidWindows.push({ dimension: 'gc', window: g.key, windowStart: windowStart(g.key), reason: 'in-window jvm_gc_pause_seconds_sum regression (counter reset); excluded', series: g.series, from: g.first, to: g.last })
      dims.get('gc').invalidWindows += 1
      continue
    }
    gcSecondsByWindow.set(g.key, (gcSecondsByWindow.get(g.key) ?? 0) + delta)
  }
  if (hasGcLines) {
    evaluate('gc', blocks.length, keys.size)
    for (const [key, seconds] of gcSecondsByWindow) {
      if (seconds >= GC_SHARE_LIMIT * WINDOW_S) {
        violations.push({ dimension: 'gc', window: key, windowStart: windowStart(key), fullGcSeconds: +seconds.toFixed(2), limitSeconds: GC_SHARE_LIMIT * WINDOW_S })
      }
    }
  } else {
    notEvaluatedReasons.gc = `${metricFile} has no jvm_gc_pause_seconds_* samples`
  }

  // pool usage: window average of busy/active over the pool config max
  if (hasTomcat) {
    let windowsEvaluated = 0
    for (const w of poolByWindow.values()) {
      if (w.busy.length === 0 || w.tomcatMax.length === 0) continue
      const tomcatMax = Math.max(...w.tomcatMax)
      if (tomcatMax <= 0) continue
      windowsEvaluated++
      const busyAvg = avg(w.busy)
      const ratio = busyAvg / tomcatMax
      if (ratio >= POOL_LIMIT) violations.push({ dimension: 'thread-pool', window: w.key, windowStart: windowStart(w.key), ratio: +ratio.toFixed(3), busyAvg: +busyAvg.toFixed(2), tomcatMax, limit: POOL_LIMIT })
    }
    evaluate('thread-pool', blocks.length, windowsEvaluated)
  } else {
    notEvaluatedReasons['thread-pool'] = `${metricFile} has no tomcat_threads_* samples`
  }
  if (hasHikari) {
    let windowsEvaluated = 0
    for (const w of poolByWindow.values()) {
      if (w.hikActive.length === 0 || w.hikMax.length === 0) continue
      const hikMax = Math.max(...w.hikMax)
      if (hikMax <= 0) continue
      windowsEvaluated++
      const activeAvg = avg(w.hikActive)
      const ratio = activeAvg / hikMax
      if (ratio >= POOL_LIMIT) violations.push({ dimension: 'connection-pool', window: w.key, windowStart: windowStart(w.key), ratio: +ratio.toFixed(3), activeAvg: +activeAvg.toFixed(2), hikMax, limit: POOL_LIMIT })
    }
    evaluate('connection-pool', blocks.length, windowsEvaluated)
  } else {
    notEvaluatedReasons['connection-pool'] = `${metricFile} has no hikaricp_connections_* samples`
  }
}

// --- verdict -----------------------------------------------------------------------
const notEvaluated = DIMENSIONS.filter((n) => dims.get(n).status !== 'evaluated')
for (const n of notEvaluated) notEvaluatedReasons[n] ??= 'no samples'
const saturated = violations.length > 0
const pass = !saturated && notEvaluated.length === 0
const verdict = {
  checkerRevision: 'r15',
  samplesDir,
  windowSeconds: WINDOW_S,
  quotas: QUOTAS,
  conclusion: pass ? 'pass' : 'fail',
  failureReasons: [
    ...(saturated ? ['saturation_violation'] : []),
    ...(notEvaluated.length > 0 ? ['collection_missing'] : []),
  ],
  pass,
  saturated,
  violationCount: violations.length,
  violations,
  notEvaluated,
  notEvaluatedReasons,
  invalidWindows,
  dimensions: Object.fromEntries(DIMENSIONS.map((n) => [n, dims.get(n)])),
}
writeFileSync(outFile, JSON.stringify(verdict, null, 2))
console.log(JSON.stringify(verdict, null, 2))
process.exit(pass ? 0 : 1)
