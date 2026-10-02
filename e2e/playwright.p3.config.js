import { defineConfig } from '@playwright/test'
import { resolve } from 'node:path'
import {
  ensureP3RunId,
  e2eRoot,
  inferP3Suite,
  isListInvocation,
  loadP3Environment,
  validateP3Environment,
} from './scripts/p3-runtime.mjs'

const listOnly = isListInvocation(process.argv)
let outputBase = process.env.P3_OUTPUT_BASE || '../artifacts/m6-acceptance/p3-blockers'
if (!listOnly) {
  loadP3Environment()
  outputBase = validateP3Environment(inferP3Suite(process.argv)).outputBase
}

const runId = listOnly ? 'list-only' : ensureP3RunId()
const evidenceDir = resolve(e2eRoot, outputBase, runId)
const outputDir = resolve(e2eRoot, 'test-results', 'p3', runId)
const reportFile = resolve(evidenceDir, 'summary.json')
if (!listOnly) {
  process.env.P3_OUTPUT_DIR = outputDir
  process.env.P3_EVIDENCE_DIR = evidenceDir
  process.env.P3_REPORT = reportFile
  process.env.P3_RUN_ID = runId
  process.env.P3_RUNNER_CONFIG = '1'
}

export default defineConfig({
  testDir: './scenarios',
  testMatch: ['p3-fines.spec.js', 'p3-ai-draft.spec.js'],
  outputDir,
  globalSetup: './scripts/p3-global-setup.mjs',
  timeout: 45_000,
  retries: 0,
  fullyParallel: false,
  workers: 1,
  reporter: [['./reporters/p3-safe-summary.mjs']],
  use: {
    baseURL: process.env.P3_BASE_URL ?? 'http://127.0.0.1:8092',
    channel: 'chrome',
    actionTimeout: 15_000,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
})
