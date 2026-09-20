// Availability statistics over observation-probe.sh JSONL raw data
// (AT-0018 C02; formula per MLS-NFR-PER-0002: availability =
// (total probes - failed probes) / total probes).
//
//   node scripts/observation-report.mjs <probe.jsonl> \
//     [--exclude <ISO-start>,<ISO-end>]... [--out <report.json>]
//
// --exclude windows are PRE-REGISTERED planned maintenance: probes inside a
// window are dropped entirely (整段剔除). Unplanned downtime is NOT excluded;
// its probes count as failures. Reports state the acceptance-period evidence
// boundary (no extrapolation beyond the window).
import { readFileSync, writeFileSync } from 'node:fs'

const args = process.argv.slice(2)
const file = args[0]
if (!file) {
  console.error('usage: observation-report.mjs <probe.jsonl> [--exclude <ISO-start>,<ISO-end>]... [--out report.json]')
  process.exit(2)
}

const excludes = []
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--exclude') {
    const [start, end] = args[++i].split(',').map((s) => new Date(s.trim()))
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      console.error(`invalid --exclude window: ${args[i]}`)
      process.exit(2)
    }
    excludes.push({ start, end })
  }
}
const outIndex = args.indexOf('--out')
const outFile = outIndex > -1 ? args[outIndex + 1] : null

const probes = readFileSync(file, 'utf8')
  .split(/\r?\n/)
  .filter((line) => line.trim())
  .map((line) => JSON.parse(line))

const inWindow = (ts) => excludes.some((w) => ts >= w.start && ts < w.end)

const perDay = new Map()
let total = 0
let failed = 0
let excluded = 0
for (const probe of probes) {
  const ts = new Date(probe.ts)
  if (inWindow(ts)) {
    excluded++
    continue
  }
  const day = ts.toISOString().slice(0, 10)
  const bucket = perDay.get(day) ?? { total: 0, failed: 0 }
  bucket.total++
  if (!probe.ok) bucket.failed++
  perDay.set(day, bucket)
  total++
  if (!probe.ok) failed++
}

const availability = total === 0 ? null : (total - failed) / total
const report = {
  rawFile: file,
  generatedAt: new Date().toISOString(),
  excludes: excludes.map((w) => ({ start: w.start.toISOString(), end: w.end.toISOString() })),
  totalProbes: total,
  failedProbes: failed,
  excludedPlannedMaintenance: excluded,
  availability,
  perDay: [...perDay.entries()].map(([day, b]) => ({
    day,
    total: b.total,
    failed: b.failed,
    availability: b.total === 0 ? null : (b.total - b.failed) / b.total,
  })),
  evidenceBoundary:
    'observation-window availability only; per MLS-NFR-PER-0002 this must not be extrapolated into an indefinite production commitment',
}
report.passesThreshold = availability !== null && availability >= 0.99

const text = JSON.stringify(report, null, 2)
if (outFile) writeFileSync(outFile, text)
else console.log(text)
