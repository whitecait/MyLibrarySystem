import { existsSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { dirname, isAbsolute, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
export const e2eRoot = resolve(here, '..')
const repoRoot = resolve(e2eRoot, '..')
const envPath = resolve(e2eRoot, '.env.p3')
const runIdPattern = /^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z-[a-f0-9]{8}$/

export function createP3RunId() {
  return new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8)
}

export function ensureP3RunId(env = process.env) {
  const runId = env.P3_RUN_ID || createP3RunId()
  if (!runIdPattern.test(runId)) {
    throw new Error('P3 regression BLOCKED: P3_RUN_ID has an invalid format.')
  }
  env.P3_RUN_ID = runId
  return runId
}

export function isListInvocation(argv = process.argv) {
  return argv.includes('--list') || argv.includes('--list-tests')
}

export function inferP3Suite(argv = process.argv) {
  if (argv.some((arg) => arg.includes('p3-fines.spec.js'))) return 'fines'
  if (argv.some((arg) => arg.includes('p3-ai-draft.spec.js'))) return 'ai'
  return 'all'
}

export function loadP3Environment() {
  assertWindowsNative()
  if (!existsSync(envPath)) {
    throw new Error('P3 regression BLOCKED: create e2e/.env.p3 from e2e/.env.p3.example.')
  }
  if (typeof process.loadEnvFile !== 'function') {
    throw new Error('P3 regression requires Node 22.12 or later for env-file loading.')
  }
  try {
    process.loadEnvFile(envPath)
  } catch {
    throw new Error('P3 regression BLOCKED: could not read e2e/.env.p3; check the local file without printing its values.')
  }
}

export function validateP3Environment(suite = 'all') {
  assertWindowsNative()
  if (!['all', 'fines', 'ai'].includes(suite)) {
    throw new Error('P3 regression BLOCKED: suite must be all, fines, or ai.')
  }

  const commonRequired = [
    'P3_BASE_URL',
    'P3_LIBRARIAN_USERNAME',
    'P3_LIBRARIAN_PASSWORD',
  ]
  const aiRequired = [
    'P3_READER_USERNAME',
    'P3_READER_PASSWORD',
    'P3_AI_READER_ID',
    'P3_AI_TITLE_ID',
    'P3_AI_COPY_ID',
    'P3_BUSINESS_DATE',
    'P3_AI_COPY_LOAN_ELIGIBLE_ON',
    'P3_AI_EXPLICIT_SUBMIT',
  ]
  const required = suite === 'fines'
    ? commonRequired
    : [...commonRequired, ...aiRequired]
  const missing = required.filter((name) => !process.env[name]?.trim())
  if (missing.length) {
    throw new Error(
      'P3 regression BLOCKED: runtime accounts and isolated fixture settings are required; suite was not run. Missing: '
      + missing.join(', '),
    )
  }

  try {
    const base = new URL(process.env.P3_BASE_URL)
    if (!['http:', 'https:'].includes(base.protocol)) throw new Error()
    if (base.username || base.password || base.search || base.hash) throw new Error()
  } catch {
    throw new Error('P3 regression BLOCKED: P3_BASE_URL must be an HTTP(S) URL without credentials, query, or fragment.')
  }

  if (suite !== 'fines') {
    const ids = ['P3_AI_READER_ID', 'P3_AI_TITLE_ID', 'P3_AI_COPY_ID']
    const invalidIds = ids.filter((name) => !/^[1-9][0-9]*$/.test(process.env[name]))
    if (invalidIds.length) {
      throw new Error('P3 regression BLOCKED: fixture IDs must be positive decimal IDs. Invalid: ' + invalidIds.join(', '))
    }

    const validDate = (value) => {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '')) return false
      const date = new Date(value + 'T00:00:00Z')
      return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value
    }
    const businessDate = process.env.P3_BUSINESS_DATE
    const eligibleOn = process.env.P3_AI_COPY_LOAN_ELIGIBLE_ON
    if (!validDate(businessDate) || !validDate(eligibleOn)) {
      throw new Error('P3 regression BLOCKED: AI fixture dates must be valid YYYY-MM-DD readback values.')
    }
    if (eligibleOn > businessDate) {
      throw new Error('P3 regression BLOCKED: selected copy is not eligible on the isolated stack business date.')
    }
    if (process.env.P3_AI_EXPLICIT_SUBMIT !== '1') {
      throw new Error('P3 regression BLOCKED: set P3_AI_EXPLICIT_SUBMIT=1 only when the isolated fixture may be borrowed once.')
    }
  }

  const outputBase = process.env.P3_OUTPUT_BASE || '../artifacts/m6-acceptance/p3-blockers'
  const outputPath = resolve(e2eRoot, outputBase)
  const evidenceRoot = resolve(repoRoot, 'artifacts/m6-acceptance')
  const pathFromEvidence = relative(evidenceRoot, outputPath)
  const isWithinEvidenceRoot = pathFromEvidence === ''
    || (!pathFromEvidence.startsWith('..') && !isAbsolute(pathFromEvidence))
  if (!isWithinEvidenceRoot) {
    throw new Error('P3 regression BLOCKED: P3_OUTPUT_BASE must stay under artifacts/m6-acceptance/.')
  }
  return { outputBase }
}

function assertWindowsNative() {
  if (process.platform !== 'win32') {
    throw new Error('P3 browser regression is Windows-native only; do not run it from WSL.')
  }
}
