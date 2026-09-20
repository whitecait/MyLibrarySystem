// Saturation verdict over the samples taken by sample-resources.sh
// (RA-0013 §11 thresholds, 60-second judgement windows; any violation marks
// the round "环境饱和、不通过" and names the saturated dimensions).
//
//   node e2e/perf/check-saturation.mjs <samples-dir> --out verdict.json
//
// Quotas follow the acceptance plan §4.1 (cpus in cores, memory in MiB).
// GC/judgement parses the metrics log best-effort: Full GC pause share per
// 60s window, Hikari active/max, Tomcat busy/max.
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
const windowKey = (ts) => Math.floor(new Date(ts.replace(' ', 'T') + 'Z').getTime() / (WINDOW_S * 1000))

// --- docker stats -------------------------------------------------------------
const stats = readFileSync(join(samplesDir, 'docker-stats.csv'), 'utf8')
  .split(/\r?\n/).slice(1).filter(Boolean)
  .map((line) => {
    const [ts, container, cpu, memUsed] = line.split(',')
    return { ts, container, cpu: parsePct(cpu), memMiB: parseMem(memUsed) }
  })

const violations = []
const svc = (container) => Object.keys(QUOTAS).find((s) => container.includes(`-${s}-`) || container.endsWith(`-${s}`)) ?? null

const windows = new Map()
for (const s of stats) {
  const name = svc(s.container)
  if (!name) continue
  const w = windows.get(`${windowKey(s.ts)}|${name}`) ?? { cpu: [], mem: [] }
  w.cpu.push(s.cpu)
  w.mem.push(s.memMiB)
  windows.set(`${windowKey(s.ts)}|${name}`, w)
}
for (const [key, w] of windows) {
  const name = key.split('|')[1]
  const q = QUOTAS[name]
  const avg = (a) => a.reduce((x, y) => x + y, 0) / a.length
  const cpuPct = (avg(w.cpu) / 100) / q.cpus
  const memPct = avg(w.mem) / q.memMiB
  if (cpuPct >= CPU_LIMIT) violations.push({ dimension: 'cpu', container: name, window: key, ratio: +cpuPct.toFixed(3), limit: CPU_LIMIT })
  if (memPct >= MEM_LIMIT) violations.push({ dimension: 'memory', container: name, window: key, ratio: +memPct.toFixed(3), limit: MEM_LIMIT })
}

// --- db lock waits -------------------------------------------------------------
const dbRows = readFileSync(join(samplesDir, 'db-samples.csv'), 'utf8')
  .split(/\r?\n/).slice(1).filter(Boolean)
  .map((line) => { const [ts, waiters, deadlocks] = line.split(','); return { ts, waiters: parseInt(waiters || '0', 10), deadlocks: parseInt(deadlocks || '0', 10) } })
const dbWindows = new Map()
for (const r of dbRows) {
  const key = windowKey(r.ts)
  const w = dbWindows.get(key) ?? { waiterSamples: 0, deadlocksMin: Infinity, deadlocksMax: -Infinity }
  w.waiterSamples += r.waiters
  w.deadlocksMin = Math.min(w.deadlocksMin, r.deadlocks)
  w.deadlocksMax = Math.max(w.deadlocksMax, r.deadlocks)
  dbWindows.set(key, w)
}
for (const [key, w] of dbWindows) {
  // each sample covers ~interval seconds; with 5s sampling, one waiter
  // sample ≈ 5s of waiting (documented approximation)
  const waitSeconds = w.waiterSamples * 5
  if (waitSeconds >= LOCK_SHARE_LIMIT * WINDOW_S) violations.push({ dimension: 'db-lock-wait', window: key, waitSeconds })
  if (w.deadlocksMax > w.deadlocksMin) violations.push({ dimension: 'db-deadlock', window: key })
}

// --- GC / pools (best-effort from metrics log) ----------------------------------
const metricFiles = readdirSync(samplesDir).filter((f) => f.startsWith('metrics-'))
if (metricFiles.length > 0) {
  const log = readFileSync(join(samplesDir, metricFiles[0]), 'utf8')
  const blocks = log.split('# SAMPLE ').slice(1)
  const gcWindows = new Map()
  const poolWindows = new Map()
  for (const block of blocks) {
    const lines = block.split(/\r?\n/)
    const ts = lines[0]
    const key = windowKey(ts)
    const gcSum = lines.filter((l) => l.startsWith('jvm_gc_pause_seconds_sum{') && l.includes('end_of_major_gc')).reduce((acc, l) => acc + (parseFloat(l.split(' ').pop()) || 0), 0)
    if (gcSum > 0) gcWindows.set(key, (gcWindows.get(key) ?? 0) + gcSum)
    const busy = lines.find((l) => l.startsWith('tomcat_threads_busy{'))
    const max = lines.find((l) => l.startsWith('tomcat_threads_config_max{'))
    const hikActive = lines.find((l) => l.startsWith('hikaricp_connections_active{'))
    const hikMax = lines.find((l) => l.startsWith('hikaricp_connections_max{'))
    const w = poolWindows.get(key) ?? { busy: 0, tomcatMax: 0, hikActive: 0, hikMax: 0 }
    if (busy) w.busy = Math.max(w.busy, parseFloat(busy.split(' ').pop()) || 0)
    if (max) w.tomcatMax = Math.max(w.tomcatMax, parseFloat(max.split(' ').pop()) || 0)
    if (hikActive) w.hikActive = Math.max(w.hikActive, parseFloat(hikActive.split(' ').pop()) || 0)
    if (hikMax) w.hikMax = Math.max(w.hikMax, parseFloat(hikMax.split(' ').pop()) || 0)
    poolWindows.set(key, w)
  }
  for (const [key, gcSeconds] of gcWindows) {
    if (gcSeconds >= GC_SHARE_LIMIT * WINDOW_S) violations.push({ dimension: 'gc', window: key, fullGcSeconds: +gcSeconds.toFixed(2) })
  }
  for (const [key, w] of poolWindows) {
    if (w.tomcatMax > 0 && w.busy / w.tomcatMax >= POOL_LIMIT) violations.push({ dimension: 'thread-pool', window: key, ratio: +(w.busy / w.tomcatMax).toFixed(3) })
    if (w.hikMax > 0 && w.hikActive / w.hikMax >= POOL_LIMIT) violations.push({ dimension: 'connection-pool', window: key, ratio: +(w.hikActive / w.hikMax).toFixed(3) })
  }
}

const verdict = {
  samplesDir,
  windowSeconds: WINDOW_S,
  quotas: QUOTAS,
  violationCount: violations.length,
  saturated: violations.length > 0,
  violations,
}
writeFileSync(outFile, JSON.stringify(verdict, null, 2))
console.log(JSON.stringify(verdict, null, 2))
process.exit(verdict.saturated ? 1 : 0)
