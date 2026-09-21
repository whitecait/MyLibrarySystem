#!/usr/bin/env node
// 三方对账（R14）：同一测量窗口内，把 k6 侧、应用侧（Prometheus
// http_server_requests 按 status 计数）、后端请求日志侧的请求量并表对照，
// 输出对账表与差异列，供性能协议证据归档（RA-0013 §11、验收计划 §4.4）。
//
//   node e2e/perf/reconcile.mjs --summary <k6-summary.json> [--summary ...] \
//     --window-start <ISO-8601> --window-end <ISO-8601> \
//     [--prom-url <http://host:port> | --prom-json <saved-response.json>] \
//     [--request-log <backend.log>] [--request-log ...] \
//     [--out <report.md>]
//
// 侧别输入（均可缺席；缺席一侧该侧记「缺失」，报告标注「对账不完整」，
// k6 侧统计照常输出，进程仍以 0 退出）：
// - k6 侧：k6 --summary-export 原始 JSON（可传多个，跨文件求和）。四类
//   计数器（expected_4xx/unexpected_4xx/server_5xx/timeouts）自 R14 起存在；
//   更早的导出没有这四个计数器，相应行记「不可用」。
// - 应用侧：--prom-url 时本脚本对 Prometheus 发即时查询
//     sum by (status) (increase(http_server_requests_seconds_count[<窗口秒>s]))
//   求值时点为窗口终点；--prom-json 时读取同一查询已保存的 API 响应
//   （{data:{result:[{metric:{status},value:[ts,"n"]}]}}；range 响应则按
//   每序列首尾差求增量）。timeout 为客户端侧现象，应用侧不可见。
// - 日志侧：--request-log 可传多个。可解析的行格式：
//     1) nginx/combined 访问日志行（"METHOD /path HTTP/1.1" STATUS，带
//        [dd/Mon/yyyy:HH:MM:SS +ZZZZ] 时间戳）；
//     2) JSON 行（status/statusCode + ts/timestamp/time/@timestamp）。
//   窗口过滤只作用于时间戳可解析的行；文件集合里若没有任何带时间戳的行，
//   则全量计数并在报告中注明该局限。Spring 默认控制台日志行不带状态码，
//   无法用于本对账。
//
// 退出码：0 = k6 侧可算（应用侧/日志侧缺失不算失败，标注「对账不完整」）；
// 2 = 用法或输入错误（缺 --summary、文件不可读、summary 无 http_reqs、
// 窗口参数缺失或起止颠倒）。
import { readFileSync, writeFileSync } from 'node:fs'

const FOUR_CLASSES = ['expected_4xx', 'unexpected_4xx', 'server_5xx', 'timeouts']

function usage() {
  console.error('usage: node e2e/perf/reconcile.mjs --summary <file> [--summary ...] \\\n' +
    '  --window-start <ISO> --window-end <ISO> [--prom-url <url> | --prom-json <file>] \\\n' +
    '  [--request-log <file> ...] [--out <report.md>]')
}

function parseArgs(argv) {
  const args = { summary: [], requestLog: [] }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const val = () => { if (i + 1 >= argv.length) throw new Error(`缺少参数值: ${a}`); return argv[++i] }
    if (a === '--summary') args.summary.push(val())
    else if (a === '--request-log') args.requestLog.push(val())
    else if (a === '--window-start') args.windowStart = val()
    else if (a === '--window-end') args.windowEnd = val()
    else if (a === '--prom-url') args.promUrl = val()
    else if (a === '--prom-json') args.promJson = val()
    else if (a === '--out') args.out = val()
    else if (a === '--help' || a === '-h') { usage(); process.exit(0) }
    else throw new Error(`未知参数: ${a}`)
  }
  return args
}

const metricCount = (summary, name) => {
  const m = summary?.metrics?.[name]
  return m?.values?.count ?? m?.count ?? null
}

// --- k6 侧 --------------------------------------------------------------------
function k6Side(files) {
  const perFile = []
  const totals = { http_reqs: 0, unexpected_failures: 0 }
  const classes = { expected_4xx: null, unexpected_4xx: null, server_5xx: null, timeouts: null }
  for (const f of files) {
    let parsed
    try {
      parsed = JSON.parse(readFileSync(f, 'utf8'))
    } catch (e) {
      throw new Error(`k6 summary 不可读: ${f}（${e.message}）`)
    }
    const reqs = metricCount(parsed, 'http_reqs')
    if (reqs === null) throw new Error(`k6 summary 缺少 http_reqs，k6 侧不可算: ${f}`)
    totals.http_reqs += reqs
    totals.unexpected_failures += metricCount(parsed, 'unexpected_failures') ?? 0
    for (const c of FOUR_CLASSES) {
      const v = metricCount(parsed, c)
      if (v !== null) classes[c] = (classes[c] ?? 0) + v
    }
    perFile.push({ file: f, http_reqs: reqs })
  }
  return { totals, classes, perFile }
}

// --- 应用侧（Prometheus）-------------------------------------------------------
function parseInstantResult(apiJson) {
  const rows = apiJson?.data?.result
  if (!Array.isArray(rows)) return null
  const byStatus = {}
  for (const r of rows) {
    const status = String(r?.metric?.status ?? '')
    if (!/^\d{3}$/.test(status)) continue
    let n = null
    if (Array.isArray(r?.value) && r.value.length === 2) n = parseFloat(r.value[1])
    else if (Array.isArray(r?.values) && r.values.length > 0) {
      // range 响应退化为首尾差（increase 语义的离线近似）
      n = parseFloat(r.values[r.values.length - 1][1]) - parseFloat(r.values[0][1])
    }
    if (n === null || Number.isNaN(n)) continue
    byStatus[status] = (byStatus[status] ?? 0) + Math.max(n, 0)
  }
  return byStatus
}

async function appSide(args, windowSeconds, windowEnd) {
  if (args.promJson) {
    let parsed
    try {
      parsed = JSON.parse(readFileSync(args.promJson, 'utf8'))
    } catch (e) {
      throw new Error(`--prom-json 不可读: ${args.promJson}（${e.message}）`)
    }
    const byStatus = parseInstantResult(parsed)
    if (!byStatus) throw new Error('--prom-json 不是可识别的 Prometheus 查询响应结构')
    return { byStatus, source: args.promJson }
  }
  if (args.promUrl) {
    const url = new URL('/api/v1/query', args.promUrl)
    url.searchParams.set('query', `sum by (status) (increase(http_server_requests_seconds_count[${windowSeconds}s]))`)
    url.searchParams.set('time', windowEnd)
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`Prometheus 查询失败 HTTP ${resp.status}: ${url}`)
    const body = await resp.json()
    if (body?.status !== 'success') throw new Error(`Prometheus 查询未成功: ${JSON.stringify(body?.error ?? body?.status)}`)
    const byStatus = parseInstantResult(body)
    if (!byStatus) throw new Error('Prometheus 响应缺少可识别的 result 结构')
    return { byStatus, source: url.toString() }
  }
  return null
}

// --- 日志侧 ---------------------------------------------------------------------
const APACHE_RE = /"(\w+)\s+(\S+)\s+HTTP\/[\d.]+"\s+(\d{3})\b/
const APACHE_TS_RE = /\[(\d{2})\/(\w{3})\/(\d{4}):(\d{2}):(\d{2}):(\d{2})\s+([+-])(\d{2})(\d{2})\]/
const MONTHS = { Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5, Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11 }

function lineStatus(line) {
  if (/^\s*\{/.test(line)) {
    try {
      const j = JSON.parse(line)
      const status = j?.status ?? j?.statusCode
      if (typeof status !== 'number' && typeof status !== 'string') return { status: null, ts: null }
      const tsRaw = j.ts ?? j.timestamp ?? j.time ?? j['@timestamp'] ?? null
      const ts = tsRaw !== null ? Date.parse(String(tsRaw)) : NaN
      return { status: Number(status), ts: Number.isNaN(ts) ? null : ts }
    } catch {
      return { status: null, ts: null }
    }
  }
  const m = APACHE_RE.exec(line)
  if (!m) return { status: null, ts: null }
  const t = APACHE_TS_RE.exec(line)
  if (!t) return { status: Number(m[3]), ts: null }
  const [, dd, mon, yyyy, hh, mm, ss, sign, oh, om] = t
  if (MONTHS[mon] === undefined) return { status: Number(m[3]), ts: null }
  const offsetMin = (Number(oh) * 60 + Number(om)) * (sign === '-' ? -1 : 1)
  const ts = Date.UTC(Number(yyyy), MONTHS[mon], Number(dd), Number(hh), Number(mm), Number(ss)) - offsetMin * 60000
  return { status: Number(m[3]), ts }
}

function logSide(files, windowStartMs, windowEndMs) {
  const byStatus = {}
  let counted = 0
  let skippedOutsideWindow = 0
  let unparsedLines = 0
  let totalLines = 0
  let timestampedSeen = false
  const perFile = []
  for (const f of files) {
    let text
    try {
      text = readFileSync(f, 'utf8')
    } catch (e) {
      throw new Error(`请求日志不可读: ${f}（${e.message}）`)
    }
    const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0)
    let fileCounted = 0
    let fileTs = false
    for (const line of lines) {
      totalLines++
      const { status, ts } = lineStatus(line)
      if (ts !== null) fileTs = true
      if (status === null || Number.isNaN(status) || status < 100 || status > 599) { unparsedLines++; continue }
      if (ts !== null) {
        if (ts < windowStartMs || ts > windowEndMs) { skippedOutsideWindow++; continue }
      } else if (timestampedSeen) {
        // 文件集合里存在带时间戳的行时，无时间戳的行无法定位窗口，不计入
        skippedOutsideWindow++
        continue
      }
      byStatus[status] = (byStatus[status] ?? 0) + 1
      counted++
      fileCounted++
    }
    if (fileTs) timestampedSeen = true
    perFile.push({ file: f, counted: fileCounted, lines: lines.length })
  }
  return { byStatus, counted, skippedOutsideWindow, unparsedLines, totalLines, perFile, timestampedSeen }
}

// --- 汇总与呈现 -------------------------------------------------------------------
const sumRange = (byStatus, lo, hi) => Object.entries(byStatus ?? {})
  .filter(([s]) => Number(s) >= lo && Number(s) <= hi)
  .reduce((acc, [, n]) => acc + n, 0)

const fmt = (v) => (v === null || v === undefined ? '不可用' : String(v))
const diffCell = (k6v, other) => (k6v === null || other === null ? 'N/A' : String(k6v - other))

function buildReport(args, windowSeconds, k6, app, appError, logs, logError) {
  const k6Total = k6.totals.http_reqs
  const k6ClassSum = FOUR_CLASSES.reduce((acc, c) => acc + (k6.classes[c] ?? 0), 0)
  const appByStatus = app ? app.byStatus : null
  const logByStatus = logs ? logs.byStatus : null
  const app4xx = appByStatus ? sumRange(appByStatus, 400, 499) : null
  const app5xx = appByStatus ? sumRange(appByStatus, 500, 599) : null
  const appTotal = appByStatus ? Object.values(appByStatus).reduce((a, b) => a + b, 0) : null
  const log4xx = logByStatus ? sumRange(logByStatus, 400, 499) : null
  const log5xx = logByStatus ? sumRange(logByStatus, 500, 599) : null
  const logTotal = logs ? logs.counted : null

  const complete = app !== null && logs !== null
  const missing = []
  if (app === null) missing.push(appError ? `应用侧获取失败（${appError}）` : '应用侧未提供（缺 --prom-url/--prom-json）')
  if (logs === null) missing.push(logError ? `日志侧解析失败（${logError}）` : '日志侧未提供（缺 --request-log）')

  const L = []
  L.push('# 三方对账（k6 / 应用 / 日志）')
  L.push('')
  L.push(`- 测量窗口：${args.windowStart} ~ ${args.windowEnd}（${windowSeconds}s）`)
  L.push(`- k6 summary：${args.summary.join(', ')}（${k6.perFile.length} 个文件）`)
  if (app !== null) L.push(`- 应用侧来源：${app.source}`)
  if (logs !== null) {
    L.push(`- 日志侧来源：${args.requestLog.join(', ')}；解析计 ${logs.counted} 行，窗口外/无时间戳跳过 ${logs.skippedOutsideWindow} 行，未解析 ${logs.unparsedLines} 行（共 ${logs.totalLines} 行）`)
    if (!logs.timestampedSeen) L.push('- 注意：日志无任何可解析时间戳，未做窗口过滤（全量计数）。')
  }
  L.push(`- 对账状态：${complete ? '完整' : `对账不完整（${missing.join('；')}）；k6 侧统计照常输出`}`)
  L.push('')
  L.push('| 指标 | k6 侧 | 应用侧 (http_server_requests) | 日志侧 | 差异 k6−应用 | 差异 k6−日志 |')
  L.push('| --- | --- | --- | --- | --- | --- |')
  L.push(`| 总请求数 | ${k6Total} | ${fmt(appTotal)} | ${fmt(logTotal)} | ${diffCell(k6Total, appTotal)} | ${diffCell(k6Total, logTotal)} |`)
  L.push(`| 4xx 合计（expected_4xx+unexpected_4xx） | ${k6.classes.expected_4xx === null ? '不可用' : k6ClassSum} | ${fmt(app4xx)} | ${fmt(log4xx)} | ${diffCell(k6.classes.expected_4xx === null ? null : k6ClassSum, app4xx)} | ${diffCell(k6.classes.expected_4xx === null ? null : k6ClassSum, log4xx)} |`)
  L.push(`| — expected_4xx | ${fmt(k6.classes.expected_4xx)} | N/A（应用侧只按 status 分桶，不区分声明） | ${fmt(log4xx)} | N/A | N/A |`)
  L.push(`| — unexpected_4xx | ${fmt(k6.classes.unexpected_4xx)} | N/A（同上） | N/A（日志不含声明） | N/A | N/A |`)
  L.push(`| 5xx 合计（server_5xx） | ${fmt(k6.classes.server_5xx)} | ${fmt(app5xx)} | ${fmt(log5xx)} | ${diffCell(k6.classes.server_5xx, app5xx)} | ${diffCell(k6.classes.server_5xx, log5xx)} |`)
  L.push(`| timeouts（status===0） | ${fmt(k6.classes.timeouts)} | N/A（客户端侧现象，应用不可见） | N/A（同左） | N/A | N/A |`)
  L.push(`| unexpected_failures（派生汇总） | ${k6.totals.unexpected_failures} | N/A | N/A | N/A | N/A |`)
  const statuses = [...new Set([...Object.keys(appByStatus ?? {}), ...Object.keys(logByStatus ?? {})])].sort()
  if (statuses.length > 0) {
    L.push('')
    L.push('按 status 明细（应用侧/日志侧；k6 原始导出无 per-status 计数，故无 k6 列）：')
    L.push('')
    L.push('| status | 应用侧 | 日志侧 |')
    L.push('| --- | --- | --- |')
    for (const s of statuses) {
      L.push(`| ${s} | ${fmt(appByStatus?.[s] ?? null)} | ${fmt(logByStatus?.[s] ?? null)} |`)
    }
  }
  L.push('')
  L.push('口径说明：应用侧查询为 sum by (status) (increase(http_server_requests_seconds_count[<窗口秒>s]))，求值时点为窗口终点；timeout 不产生应用侧请求；k6 侧含未到达后端的请求（连接失败等），与后端两侧存在固有口径差。')
  return L.join('\n')
}

// --- 主流程 ----------------------------------------------------------------------
try {
  const args = parseArgs(process.argv.slice(2))
  if (args.summary.length === 0) { usage(); console.error('错误: 至少需要一个 --summary'); process.exit(2) }
  if (!args.windowStart || !args.windowEnd) { usage(); console.error('错误: 需要 --window-start 与 --window-end'); process.exit(2) }
  const startMs = Date.parse(args.windowStart)
  const endMs = Date.parse(args.windowEnd)
  if (Number.isNaN(startMs) || Number.isNaN(endMs) || endMs < startMs) {
    console.error('错误: 窗口起止无效或起止颠倒（需 ISO-8601，且 start <= end）')
    process.exit(2)
  }
  const windowSeconds = Math.max(1, Math.ceil((endMs - startMs) / 1000))

  const k6 = k6Side(args.summary)

  let app = null
  let appError = null
  try {
    app = await appSide(args, windowSeconds, args.windowEnd)
  } catch (e) {
    appError = e.message
  }

  let logs = null
  let logError = null
  if (args.requestLog.length > 0) {
    try {
      logs = logSide(args.requestLog, startMs, endMs)
    } catch (e) {
      logError = e.message
    }
  }

  const report = buildReport(args, windowSeconds, k6, app, appError, logs, logError)
  console.log(report)
  if (args.out) {
    writeFileSync(args.out, report + '\n')
    console.error(`报告已写入: ${args.out}`)
  }
  process.exit(0)
} catch (e) {
  console.error(`错误: ${e.message}`)
  process.exit(2)
}
