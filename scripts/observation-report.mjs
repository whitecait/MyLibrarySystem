// Availability statistics over observation-probe.sh JSONL raw data
// (AT-0018 C02; formula per MLS-NFR-PER-0002: availability =
// (total probes - failed probes) / total probes).
//
//   node scripts/observation-report.mjs <probe.jsonl> \
//     --window-start <ISO> --window-end <ISO> [--interval 30] \
//     [--require-days 7] [--exclude <ISO-start>,<ISO-end>]... [--out <report.json>]
//
// A formal verdict (passesThreshold) requires the registered observation
// window: every interval slot inside it counts as one probe cycle, slots
// with no recorded probe count as FAILED probes (unplanned downtime is a
// failure, not a gap), and --exclude windows (PRE-REGISTERED planned
// maintenance) drop their slots entirely (整段剔除). A window shorter than
// --require-days, or without data at both ends, is window-incomplete and
// cannot pass regardless of the observed availability. Without a declared
// window the report is calibre-only and passesThreshold is false.
// Reports state the acceptance-period evidence boundary (no extrapolation
// beyond the window).
import { readFileSync, writeFileSync } from 'node:fs'

const args = process.argv.slice(2)
const file = args[0]
if (!file) {
  console.error(
    'usage: observation-report.mjs <probe.jsonl> --window-start <ISO> --window-end <ISO> ' +
      '[--interval 30] [--require-days 7] [--exclude <ISO-start>,<ISO-end>]... [--out report.json]')
  process.exit(2)
}
if (!/\.jsonl$/.test(file) && !/\.json$/.test(file) && file !== '-') {
  // raw evidence naming hint only; not fatal
}

const opt = (name) => {
  const i = args.indexOf(name)
  return i > -1 ? args[i + 1] : undefined
}

const excludes = []
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--exclude') {
    const [start, end] = (args[i + 1] ?? '').split(',').map((s) => new Date(s.trim()))
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      console.error(`invalid --exclude window: ${args[i + 1]}`)
      process.exit(2)
    }
    excludes.push({ start: start.getTime(), end: end.getTime() })
  }
}
const outIndex = args.indexOf('--out')
const outFile = outIndex > -1 ? args[outIndex + 1] : null

const intervalMs = Math.round(Number(opt('--interval') ?? 30) * 1000)
const requireDays = Number(opt('--require-days') ?? 7)
if (!(intervalMs > 0) || !(requireDays > 0)) {
  console.error('--interval and --require-days must be positive numbers')
  process.exit(2)
}

const windowStartRaw = opt('--window-start')
const windowEndRaw = opt('--window-end')
const hasWindow = windowStartRaw !== undefined || windowEndRaw !== undefined
if (hasWindow && (windowStartRaw === undefined || windowEndRaw === undefined)) {
  console.error('--window-start and --window-end must be given together')
  process.exit(2)
}
const windowStart = hasWindow ? new Date(windowStartRaw).getTime() : null
const windowEnd = hasWindow ? new Date(windowEndRaw).getTime() : null
if (hasWindow && (Number.isNaN(windowStart) || Number.isNaN(windowEnd) || windowEnd <= windowStart)) {
  console.error(`invalid window: ${windowStartRaw} .. ${windowEndRaw}`)
  process.exit(2)
}

// Parse line by line: one malformed line (e.g. a torn crash write — a
// zero-byte tail or a leading-zero number like "status":000) must not void
// the whole report. It is counted in badLines and skipped.
const probes = []
let badLines = 0
for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
  if (!line.trim()) continue
  try {
    probes.push(JSON.parse(line))
  } catch {
    badLines++
  }
}

const DAY_MS = 86400000

// status 0 = no HTTP response came back -> a failed probe, regardless of
// the ok flag. Counts at probe-cycle level and per-result level.
const isStatusZero = (v) =>
  (typeof v === 'number' || typeof v === 'string') && Number(v) === 0
const statusZero = (probe) => {
  if (probe === null || typeof probe !== 'object') return false
  if (isStatusZero(probe.status)) return true
  return Array.isArray(probe.results) &&
    probe.results.some((r) => r !== null && typeof r === 'object' && isStatusZero(r.status))
}

// Aggregate recorded probes into interval slots. A slot is ok only when all
// its probes are ok; duplicate lines in one slot cannot mask a failure.
// Each slot also records per-probe outcomes (R10): slot.probes[name] is the
// AND of that probe's results in the slot, so a duplicate line cannot mask a
// per-probe failure either. A probe absent from a recorded line stays absent
// (missing) for that slot.
const slots = new Map()
let outOfWindow = 0
let unparseable = 0
for (const probe of probes) {
  const ts = new Date(probe.ts).getTime()
  if (Number.isNaN(ts)) {
    unparseable++
    continue
  }
  if (hasWindow && (ts < windowStart || ts >= windowEnd)) {
    outOfWindow++
    continue
  }
  const key = hasWindow ? Math.floor((ts - windowStart) / intervalMs) : ts
  const slot = slots.get(key) ?? { ok: true, any: false, probes: {} }
  slot.any = true
  if (!probe.ok || statusZero(probe)) slot.ok = false
  if (Array.isArray(probe.results)) {
    for (const r of probe.results) {
      if (r === null || typeof r !== 'object' || typeof r.probe !== 'string') continue
      const okResult = r.ok === true && !isStatusZero(r.status)
      slot.probes[r.probe] = (slot.probes[r.probe] ?? true) && okResult
    }
  }
  slots.set(key, slot)
}

// A slot overlaps planned maintenance -> dropped entirely (整段剔除).
const slotExcluded = (slotIndex) => {
  if (!hasWindow) {
    return excludes.some((w) => {
      const t = slotIndex
      return t >= w.start && t < w.end
    })
  }
  const s = windowStart + slotIndex * intervalMs
  const e = s + intervalMs
  return excludes.some((w) => s < w.end && e > w.start)
}

let total = 0
let failed = 0
let excluded = 0
let missing = 0
let observed = 0
const perDay = new Map()
let firstSlotWithData = null
let lastSlotWithData = null

const slotTs = (slotIndex) => (hasWindow ? windowStart + slotIndex * intervalMs : slotIndex)
const dayKey = (ms) => new Date(ms).toISOString().slice(0, 10)

const expectedSlots = hasWindow ? Math.ceil((windowEnd - windowStart) / intervalMs) : null
const slotRange = hasWindow ? { from: 0, to: expectedSlots - 1 } : null

const tally = (slotIndex, outcome) => {
  // outcome: 'ok' | 'failed' | 'missing' | 'excluded'
  if (outcome === 'excluded') {
    excluded++
    return
  }
  const day = dayKey(slotTs(slotIndex))
  const bucket = perDay.get(day) ?? { total: 0, failed: 0, missing: 0 }
  bucket.total++
  total++
  if (outcome === 'failed') {
    failed++
    bucket.failed++
  } else if (outcome === 'missing') {
    failed++ // unplanned gap counts as a failed probe
    missing++
    bucket.missing++
  } else {
    observed++
  }
  perDay.set(day, bucket)
}

if (hasWindow) {
  for (let i = 0; i < expectedSlots; i++) {
    if (slotExcluded(i)) {
      tally(i, 'excluded')
      continue
    }
    const slot = slots.get(i)
    if (!slot?.any) {
      tally(i, 'missing')
      continue
    }
    if (firstSlotWithData === null) firstSlotWithData = i
    lastSlotWithData = i
    tally(i, slot.ok ? 'ok' : 'failed')
  }
} else {
  for (const [key, slot] of [...slots.entries()].sort((a, b) => a[0] - b[0])) {
    if (slotExcluded(key)) {
      excluded++
      continue
    }
    tally(key, slot.ok ? 'ok' : 'failed')
  }
}

const availability = total === 0 ? null : (total - failed) / total

// Window completeness anchors on the first/last NON-excluded slots: a
// pre-registered maintenance window covering the window edges must not make
// the window look incomplete. A small slack (2 slots) absorbs probe startup
// latency; anything beyond it is a coverage hole -> incomplete.
const windowComplete = (() => {
  if (!hasWindow) return null
  if ((windowEnd - windowStart) < requireDays * DAY_MS) return false
  if (firstSlotWithData === null || lastSlotWithData === null) return false
  let firstExpected = 0
  while (firstExpected < expectedSlots && slotExcluded(firstExpected)) firstExpected++
  let lastExpected = expectedSlots - 1
  while (lastExpected > firstExpected && slotExcluded(lastExpected)) lastExpected--
  const slack = 2
  return firstSlotWithData <= firstExpected + slack && lastSlotWithData >= lastExpected - slack
})()

// ---- Per-probe dimension (R10). Success rate, latency percentiles,
// coverage window, missing segments (缺测段) and consecutive-failure
// segments (连续失败段) per probe name. Scope: the declared window when
// given (slot based), else all recorded cycles in ts order (calibre-only).
// missing = the slot/cycle carries no recorded result for the probe (cycle
// line missing, line unparseable, or result absent); failure = a recorded
// result that is not ok. The two never overlap. Latency percentiles use
// successful results only (status:0 carries no measured latency).
const probeResultOk = (r) =>
  r !== undefined && r !== null && typeof r === 'object' && r.ok === true && !isStatusZero(r.status)

const cyclesInScope = []
for (const probe of probes) {
  const ts = new Date(probe.ts).getTime()
  if (Number.isNaN(ts)) continue
  if (hasWindow && (ts < windowStart || ts >= windowEnd)) continue
  cyclesInScope.push({ ts, probe })
}
cyclesInScope.sort((a, b) => a.ts - b.ts)

const perProbeAgg = new Map()
for (const { ts, probe } of cyclesInScope) {
  if (!Array.isArray(probe.results)) continue
  for (const r of probe.results) {
    if (r === null || typeof r !== 'object' || typeof r.probe !== 'string') continue
    let a = perProbeAgg.get(r.probe)
    if (!a) {
      a = { name: r.probe, total: 0, ok: 0, failed: 0, statusZero: 0, ms: [], firstMs: null, lastMs: null }
      perProbeAgg.set(r.probe, a)
    }
    a.total++
    if (probeResultOk(r)) a.ok++
    else a.failed++
    if (isStatusZero(r.status)) a.statusZero++
    if (probeResultOk(r) && typeof r.ms === 'number' && Number.isFinite(r.ms)) a.ms.push(r.ms)
    if (a.firstMs === null) a.firstMs = ts
    a.lastMs = ts
  }
}

const percentile = (values, p) => {
  if (values.length === 0) return null
  const sorted = [...values].sort((x, y) => x - y)
  return sorted[Math.max(0, Math.ceil((p / 100) * sorted.length) - 1)]
}
const iso = (ms) => new Date(ms).toISOString()

const failureSegments = new Map()
const missingSegmentsByProbe = new Map()
const missingSlotsByProbe = new Map()
const windowMissingSegments = []

if (hasWindow) {
  for (const name of perProbeAgg.keys()) missingSlotsByProbe.set(name, 0)
  let cycleMissRun = null
  const failRun = new Map()
  const missRunP = new Map()
  const closeRun = (runs, lists, name) => {
    const r = runs.get(name)
    if (!r) return
    const list = lists.get(name) ?? []
    list.push({ start: iso(r.startMs), end: iso(r.endMs), slots: r.slots })
    lists.set(name, list)
    runs.set(name, null)
  }
  for (let i = 0; i < expectedSlots; i++) {
    const s = windowStart + i * intervalMs
    const e = s + intervalMs
    if (slotExcluded(i)) {
      if (cycleMissRun) {
        windowMissingSegments.push({ start: iso(cycleMissRun.startMs), end: iso(cycleMissRun.endMs), slots: cycleMissRun.slots })
        cycleMissRun = null
      }
      for (const name of perProbeAgg.keys()) {
        closeRun(failRun, failureSegments, name)
        closeRun(missRunP, missingSegmentsByProbe, name)
      }
      continue
    }
    const slot = slots.get(i)
    if (!slot?.any) {
      if (!cycleMissRun) cycleMissRun = { startMs: s, endMs: e, slots: 0 }
      cycleMissRun.endMs = e
      cycleMissRun.slots++
    } else if (cycleMissRun) {
      windowMissingSegments.push({ start: iso(cycleMissRun.startMs), end: iso(cycleMissRun.endMs), slots: cycleMissRun.slots })
      cycleMissRun = null
    }
    for (const name of perProbeAgg.keys()) {
      const outcome = slot?.any
        ? (name in slot.probes ? (slot.probes[name] ? 'ok' : 'failed') : 'missing')
        : 'missing'
      if (outcome === 'missing') missingSlotsByProbe.set(name, missingSlotsByProbe.get(name) + 1)
      if (outcome === 'ok') {
        closeRun(failRun, failureSegments, name)
        closeRun(missRunP, missingSegmentsByProbe, name)
        continue
      }
      const runs = outcome === 'failed' ? failRun : missRunP
      const lists = outcome === 'failed' ? failureSegments : missingSegmentsByProbe
      let r = runs.get(name)
      if (!r) {
        r = { startMs: s, endMs: e, slots: 0 }
        runs.set(name, r)
      }
      r.endMs = e
      r.slots++
    }
  }
  if (cycleMissRun) {
    windowMissingSegments.push({ start: iso(cycleMissRun.startMs), end: iso(cycleMissRun.endMs), slots: cycleMissRun.slots })
  }
  for (const name of perProbeAgg.keys()) {
    closeRun(failRun, failureSegments, name)
    closeRun(missRunP, missingSegmentsByProbe, name)
  }
} else {
  // Calibre-only mode: failure segments over recorded cycles in ts order;
  // absence cannot be located in time without a declared window.
  for (const name of perProbeAgg.keys()) {
    const list = []
    let run = null
    let missingCycles = 0
    for (const { ts, probe } of cyclesInScope) {
      const r = Array.isArray(probe.results)
        ? probe.results.find((x) => x !== null && typeof x === 'object' && x.probe === name)
        : undefined
      if (r === undefined) {
        missingCycles++
        if (run) { list.push(run); run = null }
        continue
      }
      if (probeResultOk(r)) {
        if (run) { list.push(run); run = null }
        continue
      }
      if (!run) run = { start: iso(ts), end: iso(ts), count: 0 }
      run.end = iso(ts)
      run.count++
    }
    if (run) list.push(run)
    failureSegments.set(name, list)
    missingSegmentsByProbe.set(name, [])
    missingSlotsByProbe.set(name, missingCycles)
  }
}

const perProbe = [...perProbeAgg.values()].map((a) => ({
  probe: a.name,
  total: a.total,
  ok: a.ok,
  failed: a.failed,
  statusZero: a.statusZero,
  successRate: a.total === 0 ? null : a.ok / a.total,
  latencyMs: {
    samples: a.ms.length,
    p50: percentile(a.ms, 50),
    p95: percentile(a.ms, 95),
    p99: percentile(a.ms, 99),
    max: a.ms.length === 0 ? null : a.ms.reduce((m, v) => (v > m ? v : m), -Infinity),
  },
  window: {
    firstSeen: a.firstMs === null ? null : iso(a.firstMs),
    lastSeen: a.lastMs === null ? null : iso(a.lastMs),
  },
  missingSlots: missingSlotsByProbe.get(a.name) ?? 0,
  missingSegments: missingSegmentsByProbe.get(a.name) ?? [],
  failureSegments: failureSegments.get(a.name) ?? [],
}))

let verdict
if (!hasWindow) {
  verdict = 'insufficient: observation window not declared — calibre data only, not a formal C02 verdict'
} else if (!windowComplete) {
  verdict = `fail: window incomplete (needs >= ${requireDays} natural days and data at both window ends)`
} else if (availability === null || availability < 0.99) {
  verdict = 'fail: availability below 0.99 threshold'
} else {
  verdict = 'pass'
}

const report = {
  rawFile: file,
  generatedAt: new Date().toISOString(),
  window: hasWindow
    ? { start: new Date(windowStart).toISOString(), end: new Date(windowEnd).toISOString(),
        spanDays: (windowEnd - windowStart) / DAY_MS, requireDays,
        expectedSlots, intervalS: intervalMs / 1000, complete: windowComplete,
        firstSlotWithData, lastSlotWithData,
        missingSegments: windowMissingSegments }
    : null,
  excludes: excludes.map((w) => ({ start: new Date(w.start).toISOString(), end: new Date(w.end).toISOString() })),
  dropped: { outOfWindow, unparseable, badLines },
  totalProbes: total, // slot-based: every expected cycle inside the window counts
  failedProbes: failed,
  missingSlots: missing,
  observedSlots: observed,
  excludedPlannedMaintenance: excluded,
  availability,
  verdict,
  passesThreshold: verdict === 'pass',
  perDay: [...perDay.entries()].map(([day, b]) => ({
    day,
    total: b.total,
    failed: b.failed,
    missing: b.missing,
    availability: b.total === 0 ? null : (b.total - b.failed) / b.total,
  })),
  // Per-probe dimension (R10). scope: window-slots = window-filtered,
  // slot based (missing slots counted); recorded-cycles = calibre-only
  // report over recorded cycles in ts order (no expected-slot grid).
  perProbeScope: hasWindow ? 'window-slots' : 'recorded-cycles',
  perProbe,
  evidenceBoundary:
    'observation-window availability only; per MLS-NFR-PER-0002 this must not be extrapolated into an indefinite production commitment',
}

const text = JSON.stringify(report, null, 2)
if (outFile) writeFileSync(outFile, text)
else console.log(text)
