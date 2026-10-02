import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const safeAnnotationTypes = new Set([
  'browser-version',
  'actual-viewport',
  'P3-12-regression',
  'P3-14-real-model',
  'P3-14-attempts',
  'P3-14-prefill',
  'P3-14-confirmation',
  'business-side-effects',
  'fixture-state',
])

export default class P3SafeSummaryReporter {
  constructor() {
    this.tests = []
  }

  onTestEnd(test, result) {
    this.tests.push({
      title: test.title,
      status: result.status,
      durationMs: result.duration,
      annotations: test.annotations
        .filter((annotation) => safeAnnotationTypes.has(annotation.type))
        .map(({ type, description }) => ({ type, description: description ?? '' })),
    })
  }

  onEnd(result) {
    if (process.argv.includes('--list') || process.argv.includes('--list-tests')) return
    const reportPath = process.env.P3_REPORT
    if (!reportPath) {
      throw new Error('P3 safe summary path was not configured.')
    }
    const outputPath = resolve(reportPath)
    mkdirSync(dirname(outputPath), { recursive: true })
    const summary = {
      format: 'p3-safe-summary-v1',
      generatedAt: new Date().toISOString(),
      status: result.status,
      tests: this.tests,
    }
    writeFileSync(outputPath, JSON.stringify(summary, null, 2) + '\n', 'utf8')
    const passed = this.tests.filter((test) => test.status === 'passed').length
    console.log('P3 safe summary: ' + result.status + ', ' + passed + '/' + this.tests.length + ' tests passed.')
  }
}
