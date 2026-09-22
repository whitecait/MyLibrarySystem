// Verdict collector for performance-protocol evidence (RA-0013 §11 /
// acceptance plan docs/agents/M6-ACCEPTANCE-PLAN-20260919.md §4).
//
//   node e2e/perf/collect-verdicts.mjs <evidence-dir>
//
// Reads every <evidence-dir>/run-N/*-summary.json + saturation-verdict.json
// and emits verdicts.json / verdicts-summary.md inside the evidence dir.
//
// Judgement (plan §4.4 + §4.2 sample floor; R13 completion criteria):
//   - Valid samples of a response class = the class's requests minus the
//     responses that were NOT declared: unexpected 4xx + 5xx + timeouts
//     (§4.4: 非预期失败分子＝非预期 4xx＋5xx＋超时，分母＝全部请求). A declared
//     expected 4xx is a measured response of the class and therefore counts as
//     a valid sample of it.
//   - A class below the sample floor (100) is registered as
//     samples_insufficient: no percentile conclusion for that class, and the
//     round is NOT judged invalid because of it (§4.4: 样本不足的场景不输出
//     分位值并如实登记). Under the 2026-09-22 reduced protocol window the
//     steady scenario's low-rate classes are the standing case — at the
//     unchanged arrival rates a 300-second window yields ~11 payment samples
//     (2/minute) and ~60 audit samples (6/minute × 2 calls), so both are
//     registered samples_insufficient as a consequence of the protocol's own
//     arithmetic rather than a defect; the retrieval sub-scenarios §4.2 names
//     keep their floor (180s at 100/minute).
//   - warmup is excluded from statistics by protocol (reported, not gated).
//   - A summary that lacks the per-class count sub-metrics
//     (http_reqs{class:*} / http_req_failed{class:*} from lib/common.js)
//     cannot be gated per class and fails with an explicit reason instead of
//     silently passing — regenerate it with the current harness rather than
//     trusting an uncountable run.
//   - Exit code 0 when every measured scenario passed; 1 only for a crossed
//     threshold (`unexpected_failures: count==0` is one of them), a failed
//     check, a per-class count missing from the summary, or unreadable
//     evidence. The sample floor never produces exit 1.
//
// Per-class split of the undeclared failures (D3): lib/common.js's four-class
// counters (expected_4xx / unexpected_4xx / server_5xx / timeouts) are
// run-level, while k6's http_req_failed{class:*} counts every non-2xx/3xx
// response — the declared ones included. The two are reconciled through the
// closing identity
//   Σ_class http_req_failed(class) === expected_4xx + unexpected_failures.
// When that closes and unexpected_failures === 0, no response in the run was
// undeclared and nothing is deducted. Otherwise (undeclared failures exist, or
// the run-level counters do not close) the per-class split is not recoverable
// from the summary, so the class's whole k6 failure count is deducted — the
// conservative direction: valid samples are never added that were not
// measured. Such a run has already crossed the `unexpected_failures: count==0`
// threshold of its scenario, so this only keeps the registered per-class
// numbers honest. The old `requests - http_req_failed` destroyed declared
// responses: peak's normal_read class (1627 requests, k6 failed rate 1.0,
// harness expected_4xx 1627, unexpected_failures 0) came out as 0 valid
// samples and turned the round into a failure.
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

const report = {
  evidenceDir,
  sampleMin: SAMPLE_MIN,
  // §4.4: the floor decides whether a percentile conclusion may be stated, not
  // whether the round is valid.
  sampleFloor: { min: SAMPLE_MIN, insufficientFatal: false },
  runs: [],
  allGreen: true,
}

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

    // --- run-level four-class counters -------------------------------------
    // k6 omits zero-valued metrics from the summary export (lib/common.js
    // handleSummary notes it), so an absent counter means "never incremented".
    const metricCount = (name) => {
      const m = s.metrics?.[name]
      if (!m) return null
      return m.count ?? m.values?.count ?? null
    }
    const declaredTotal = metricCount('expected_4xx') ?? 0
    const undeclaredTotal = metricCount('unexpected_failures')
      ?? ['unexpected_4xx', 'server_5xx', 'timeouts'].reduce((a, n) => a + (metricCount(n) ?? 0), 0)

    const rows = classNames.map((c) => ({
      class: c,
      requests: s.metrics?.[`http_reqs{class:${c}}`]?.count ?? null,
      failedRate: s.metrics?.[`http_req_failed{class:${c}}`]?.value ?? null,
      duration: s.metrics?.[`http_req_duration{class:${c}}`] ?? null,
    }))
    for (const r of rows) {
      r.failedRequests = r.requests === null || r.failedRate === null ? null : Math.round(r.failedRate * r.requests)
    }
    // How many k6-level failures the four-class counters do not account for.
    // 0 = every failure of the run is classified and the per-class split below
    // is exact.
    const failedSum = rows.every((r) => r.failedRequests !== null) ? rows.reduce((a, r) => a + r.failedRequests, 0) : null
    const accountingGap = failedSum === null ? null : failedSum - declaredTotal - undeclaredTotal
    const closable = accountingGap === 0 && undeclaredTotal === 0

    const classes = rows.map((r) => {
      const unknown = r.requests === null || r.failedRate === null
      const undeclaredFailures = unknown ? null : closable ? 0 : r.failedRequests
      const validSamples = unknown ? null : r.requests - undeclaredFailures
      const sufficient = validSamples !== null && validSamples >= SAMPLE_MIN
      return {
        class: r.class,
        requests: r.requests,
        failedRequests: r.failedRequests,
        undeclaredFailures,
        validSamples,
        sufficient,
        samplesInsufficient: validSamples !== null && !sufficient,
        // Percentiles are only a conclusion when the sample floor is met.
        p95: sufficient && r.duration?.['p(95)'] !== undefined ? +r.duration['p(95)'].toFixed(1) : null,
        p99: sufficient && r.duration?.['p(99)'] !== undefined ? +r.duration['p(99)'].toFixed(1) : null,
        unknown,
      }
    })

    const accounting = failedSum === null
      ? 'unavailable'
      : closable ? 'four_class_counters_closed' : 'conservative_full_deduction'

    const measured = label !== 'warmup'
    const problems = []
    if (measured) {
      if (crossed.length > 0) problems.push(`thresholds crossed: ${crossed.join('; ')}`)
      if (unexpected !== 0) problems.push(`unexpected_failures=${unexpected}`)
      if (checkFails !== null && checkFails !== 0) problems.push(`failed checks=${checkFails}`)
      if (reqs === null) problems.push('no http_reqs count in summary')
      for (const c of classes) {
        if (c.unknown) problems.push(`${c.class}: per-class sample count unavailable in this summary (needs http_reqs{class:*}/http_req_failed{class:*} from the current lib/common.js)`)
      }
    }
    // The sample floor is registered, not sanctioned (§4.4).
    const insufficient = classes.filter((c) => c.samplesInsufficient)
      .map((c) => ({ class: c.class, validSamples: c.validSamples, requests: c.requests, undeclaredFailures: c.undeclaredFailures }))
    const ok = !measured || problems.length === 0
    if (measured && !ok) report.allGreen = false
    scenarios.push({
      label,
      measured,
      crossed,
      unexpected,
      checkFails,
      requests: reqs,
      declaredExpected4xx: declaredTotal,
      undeclaredFailures: undeclaredTotal,
      k6FailedSum: failedSum,
      accounting,
      accountingGap,
      classes,
      insufficient,
      problems,
      ok,
    })
  }
  const satPath = join(dir, 'saturation-verdict.json')
  const saturation = existsSync(satPath) ? JSON.parse(readFileSync(satPath, 'utf8')) : null
  // check-saturation.mjs judges the round too: a saturated round and a round
  // with an unevaluated dimension (collection gap) are both not a pass, so the
  // collector must not report a clean saturation line for either.
  const saturationFails = saturation === null
    ? false
    : saturation.pass === undefined ? saturation.saturated === true : saturation.pass === false
  if (saturationFails) report.allGreen = false
  report.runs.push({
    run,
    scenarios,
    saturation: saturation && {
      checkerRevision: saturation.checkerRevision,
      conclusion: saturation.conclusion,
      pass: saturation.pass,
      failureReasons: saturation.failureReasons,
      saturated: saturation.saturated,
      violationCount: saturation.violationCount,
      violations: saturation.violations,
      notEvaluated: saturation.notEvaluated,
    },
  })
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

const ACCOUNTING_TEXT = {
  four_class_counters_closed: '四类计数闭合，零未声明失败',
  conservative_full_deduction: '存在未声明失败或四类计数不闭合：按该类 k6 失败数保守扣减',
  unavailable: '逐类失败数不可读',
}

let md = `# 负载协议判定汇总\n\n`
md += `- 证据目录：${evidenceDir}\n`
md += `- 数据集：${datasetLine}\n`
md += `- 有效样本口径：该类请求数 − 该类未声明失败（非预期 4xx＋5xx＋超时）；已声明的预期 4xx 计入该类有效样本（计划 §4.4）\n`
md += `- 样本门禁：每个实测场景的每个响应分类有效样本 ≥ ${SAMPLE_MIN}；不足者标记 samples_insufficient、不输出该类分位结论并如实登记，不因此判轮无效\n`
md += `- 总体判定：${report.allGreen ? 'ALL GREEN（全部轮次门槛与饱和判定通过）' : '存在不通过项，见各轮明细'}\n`
for (const r of report.runs) {
  md += `\n## ${r.run}\n\n`
  md += `| 场景 | 计入统计 | 请求数 | 分类有效样本 | 分类未声明失败 | 非预期失败 | check 失败 | 分类 p95(ms，仅足样本分类) | 判定 |\n`
  md += `|--|--|--|--|--|--|--|--|--|\n`
  for (const s of r.scenarios) {
    const samples = s.classes.map((c) => c.unknown ? `${c.class}=?` : `${c.class}=${c.validSamples}${c.sufficient ? '' : '(不足)'}`).join(', ') || '-'
    const undeclared = s.classes.map((c) => c.unknown ? `${c.class}=?` : `${c.class}=${c.undeclaredFailures}`).join(', ') || '-'
    const p95s = s.classes.filter((c) => c.p95 !== null).map((c) => `${c.class}=${c.p95}`).join(', ') || '-'
    md += `| ${s.label} | ${s.measured ? '是' : '否(预热)'} | ${s.requests ?? '-'} | ${samples} | ${undeclared} | ${s.unexpected ?? '-'} | ${s.checkFails ?? '-'} | ${p95s} | ${s.ok ? 'PASS' : 'FAIL'} |\n`
  }
  for (const s of r.scenarios) {
    for (const p of s.problems) md += `  - ${s.label}: ${p}\n`
    for (const c of s.insufficient) {
      md += `  - ${s.label}: ${c.class} samples_insufficient（有效样本 ${c.validSamples} < ${SAMPLE_MIN}，请求 ${c.requests}，未声明失败 ${c.undeclaredFailures}）——不出分位结论，如实登记，不判轮无效\n`
    }
    md += `  - ${s.label}: 逐类失败归账＝${ACCOUNTING_TEXT[s.accounting]}${s.accountingGap ? `（gap=${s.accountingGap}）` : ''}\n`
  }
  md += `\n饱和判定：${r.saturation
    ? (r.saturation.pass === false
      ? `不通过（${r.saturation.failureReasons?.join(', ') ?? '?'}；越限 ${r.saturation.violationCount} 项，未评估维 ${JSON.stringify(r.saturation.notEvaluated ?? [])}）`
      : `清洁（零越限、判据全维已评估；checkerRevision=${r.saturation.checkerRevision ?? '?'}）`)
    : '（无 saturation-verdict.json）'}\n`
}
writeFileSync(join(evidenceDir, 'verdicts-summary.md'), md)
console.log(md)
console.log('allGreen =', report.allGreen)
process.exit(report.allGreen ? 0 : 1)
