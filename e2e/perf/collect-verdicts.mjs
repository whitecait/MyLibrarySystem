// Verdict collector for performance-protocol evidence (RA-0013 §11 /
// acceptance plan docs/agents/M6-ACCEPTANCE-PLAN-20260919.md §4).
//
//   node e2e/perf/collect-verdicts.mjs <evidence-dir>
//
// Reads every <evidence-dir>/run-N/*-summary.json + saturation-verdict.json
// and emits verdicts.json / verdicts-summary.md inside the evidence dir.
// Gate: every measured scenario summary must have
//   - no crossed threshold (k6 summary-export marks crossed = true),
//   - unexpected_failures.count == 0,
//   - zero failed checks,
//   - >= 100 valid samples for EVERY measured response class (§4.2 sample
//     floor, extended to all scenario classes — including steady's payment
//     class; a class below the floor gets no percentile conclusion and is
//     marked samples_insufficient).
// warmup is excluded from statistics by protocol (reported, not gated).
// Valid samples per class = per-class requests minus failed requests, from
// the `http_reqs{class:*}` / `http_req_failed{class:*}` sub-metrics that
// lib/common.js registers (observability thresholds). A summary without
// those sub-metrics cannot be gated per class and fails with an explicit
// reason instead of silently passing — regenerate it with the current
// harness rather than trusting an uncountable run.
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SAMPLE_MIN = 100

const evidenceDir = resolve(process.argv[2] ?? '')
if (!process.argv[2] || !existsSync(evidenceDir)) {
  console.error('usage: node e2e/perf/collect-verdicts.mjs <evidence-dir> (the dir run-protocol.sh wrote run-N/ into)')
  process.exit(2)
}

const runs = readdirSync(evidenceDir).filter((d) => /^run-\d+$/.test(d)).sort()
if (runs.length === 0) {
  console.error(`no run-N directories under ${evidenceDir} — nothing to collect`)
  process.exit(2)
}

const report = { evidenceDir, sampleMin: SAMPLE_MIN, runs: [], allGreen: true }

for (const run of runs) {
  const dir = join(evidenceDir, run)
  const files = readdirSync(dir).filter((f) => f.endsWith('-summary.json'))
  const scenarios = []
  for (const f of files) {
    const label = f.replace(/-summary\.json$/, '')
    const s = JSON.parse(readFileSync(join(dir, f), 'utf8'))
    const crossed = []
    for (const [metric, def] of Object.entries(s.metrics ?? {})) {
      for (const [expr, failed] of Object.entries(def.thresholds ?? {})) {
        if (failed === true) crossed.push(`${metric}: ${expr}`)
      }
    }
    const unexpected = s.metrics?.unexpected_failures?.count ?? null
    const checkFails = s.metrics?.checks?.fails ?? null
    const reqs = s.metrics?.http_reqs?.count ?? null

    // Per-class sample accounting: enumerate every class the scenario
    // measured (duration sub-metric with a threshold) or counted (reqs
    // sub-metric), in deterministic order.
    const classNames = [...new Set([
      ...Object.keys(s.metrics ?? {}).flatMap((m) => (m.startsWith('http_req_duration{class:') ? [m.slice('http_req_duration{class:'.length).replace('}', '')] : [])),
      ...Object.keys(s.metrics ?? {}).flatMap((m) => (m.startsWith('http_reqs{class:') ? [m.slice('http_reqs{class:'.length).replace('}', '')] : [])),
    ])]
    const classes = classNames.map((c) => {
      const count = s.metrics?.[`http_reqs{class:${c}}`]?.count ?? null
      const failedRate = s.metrics?.[`http_req_failed{class:${c}}`]?.value ?? null
      const dur = s.metrics?.[`http_req_duration{class:${c}}`] ?? null
      const unknown = count === null || failedRate === null
      const failed = unknown ? null : Math.round(failedRate * count)
      const validSamples = unknown ? null : count - failed
      const sufficient = !unknown && validSamples >= SAMPLE_MIN
      return {
        class: c,
        requests: count,
        failedRequests: failed,
        validSamples,
        sufficient,
        samplesInsufficient: !unknown && !sufficient,
        // Percentiles are only a conclusion when the sample floor is met.
        p95: sufficient && dur?.['p(95)'] !== undefined ? +dur['p(95)'].toFixed(1) : null,
        p99: sufficient && dur?.['p(99)'] !== undefined ? +dur['p(99)'].toFixed(1) : null,
        unknown,
      }
    })

    const measured = label !== 'warmup'
    const problems = []
    if (measured) {
      if (crossed.length > 0) problems.push(`thresholds crossed: ${crossed.join('; ')}`)
      if (unexpected !== 0) problems.push(`unexpected_failures=${unexpected}`)
      if (checkFails !== null && checkFails !== 0) problems.push(`failed checks=${checkFails}`)
      if (reqs === null) problems.push('no http_reqs count in summary')
      for (const c of classes) {
        if (c.unknown) problems.push(`${c.class}: per-class sample count unavailable in this summary (needs http_reqs{class:*}/http_req_failed{class:*} from the current lib/common.js)`)
        else if (!c.sufficient) problems.push(`${c.class}: samples_insufficient (valid=${c.validSamples} < ${SAMPLE_MIN}); no percentile conclusion`)
      }
    }
    const ok = !measured || problems.length === 0
    if (measured && !ok) report.allGreen = false
    scenarios.push({ label, measured, crossed, unexpected, checkFails, requests: reqs, classes, problems, ok })
  }
  const satPath = join(dir, 'saturation-verdict.json')
  const saturation = existsSync(satPath) ? JSON.parse(readFileSync(satPath, 'utf8')) : null
  if (saturation?.saturated) report.allGreen = false
  report.runs.push({ run, scenarios, saturation: saturation && { saturated: saturation.saturated, violationCount: saturation.violationCount, violations: saturation.violations } })
}

writeFileSync(join(evidenceDir, 'verdicts.json'), JSON.stringify(report, null, 2))

// Dataset identity comes from the generator manifest, not from this script.
let datasetLine = 'dataset manifest not found (e2e/data/out/manifest.json)'
const manifestPath = join(dirname(fileURLToPath(import.meta.url)), '..', 'data', 'out', 'manifest.json')
if (existsSync(manifestPath)) {
  try {
    const m = JSON.parse(readFileSync(manifestPath, 'utf8'))
    datasetLine = `generator ${m.generatorVersion} (seed ${m.seed}), dataset.sql SHA-256 \`${m.checksums?.['dataset.sql'] ?? 'n/a'}\``
  } catch {
    datasetLine = `dataset manifest unreadable: ${manifestPath}`
  }
}

let md = `# 负载协议判定汇总\n\n`
md += `- 证据目录：${evidenceDir}\n`
md += `- 数据集：${datasetLine}\n`
md += `- 样本门禁：每个实测场景的每个响应分类有效样本 ≥ ${SAMPLE_MIN}（不足的分类标记 samples_insufficient，不输出分位结论）\n`
md += `- 总体判定：${report.allGreen ? 'ALL GREEN（全部轮次门槛与饱和判定通过）' : '存在不通过项，见各轮明细'}\n`
for (const r of report.runs) {
  md += `\n## ${r.run}\n\n`
  md += `| 场景 | 计入统计 | 请求数 | 分类有效样本 | 非预期失败 | check 失败 | 分类 p95(ms，仅足样本分类) | 判定 |\n`
  md += `|--|--|--|--|--|--|--|--|\n`
  for (const s of r.scenarios) {
    const samples = s.classes.map((c) => c.unknown ? `${c.class}=?` : `${c.class}=${c.validSamples}${c.sufficient ? '' : '(不足)'}`).join(', ') || '-'
    const p95s = s.classes.filter((c) => c.p95 !== null).map((c) => `${c.class}=${c.p95}`).join(', ') || '-'
    md += `| ${s.label} | ${s.measured ? '是' : '否(预热)'} | ${s.requests ?? '-'} | ${samples} | ${s.unexpected ?? '-'} | ${s.checkFails ?? '-'} | ${p95s} | ${s.ok ? 'PASS' : 'FAIL'} |\n`
    for (const p of s.problems) md += `  - ${s.label}: ${p}\n`
  }
  md += `\n饱和判定：${r.saturation ? (r.saturation.saturated ? `饱和（${r.saturation.violationCount} 项：${JSON.stringify(r.saturation.violations)}）` : '清洁（零越限）') : '（无 saturation-verdict.json）'}\n`
}
writeFileSync(join(evidenceDir, 'verdicts-summary.md'), md)
console.log(md)
console.log('allGreen =', report.allGreen)
process.exit(report.allGreen ? 0 : 1)
