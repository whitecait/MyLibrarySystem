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

const probes = readFileSync(file, 'utf8')
  .split(/\r?\n/)
  .filter((line) => line.trim())
  .map((line) => JSON.parse(line))

const DAY_MS = 86400000

// Aggregate recorded probes into interval slots. A slot is ok only when all
// its probes are ok; duplicate lines in one slot cannot mask a failure.
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
  const slot = slots.get(key) ?? { ok: true, any: false }
  slot.any = true
  if (!probe.ok) slot.ok = false
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
        firstSlotWithData, lastSlotWithData }
    : null,
  excludes: excludes.map((w) => ({ start: new Date(w.start).toISOString(), end: new Date(w.end).toISOString() })),
  dropped: { outOfWindow, unparseable },
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
  evidenceBoundary:
    'observation-window availability only; per MLS-NFR-PER-0002 this must not be extrapolated into an indefinite production commitment',
}

const text = JSON.stringify(report, null, 2)
if (outFile) writeFileSync(outFile, text)
else console.log(text)
