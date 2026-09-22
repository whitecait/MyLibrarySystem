// Remove k6 setup data before a summary becomes acceptance evidence.
//
// k6 includes the value returned by setup() under the top-level `setup_data`
// field of --summary-export JSON. The performance scenarios return bearer
// tokens there so VUs can reuse authenticated sessions; those tokens are
// runtime state, not metrics, and must never be copied into long-lived
// evidence. Keep the sanitization at the evidence boundary so the scenario
// contract and every metric consumed by collect-verdicts/reconcile remain
// unchanged.

import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export function sanitizeK6Summary(summary) {
  if (summary === null || typeof summary !== 'object' || Array.isArray(summary)) {
    throw new TypeError('k6 summary must be a JSON object')
  }
  const sanitized = { ...summary }
  delete sanitized.setup_data
  return sanitized
}

function usage() {
  console.error('usage: node e2e/perf/lib/sanitize-k6-summary.mjs <raw-summary.json>')
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const input = process.argv[2]
  if (!input || process.argv.length !== 3) {
    usage()
    process.exitCode = 2
  } else {
    try {
      const parsed = JSON.parse(readFileSync(input, 'utf8'))
      process.stdout.write(`${JSON.stringify(sanitizeK6Summary(parsed), null, 2)}\n`)
    } catch (error) {
      console.error(`k6 summary sanitization failed: ${error.message}`)
      process.exitCode = 1
    }
  }
}
