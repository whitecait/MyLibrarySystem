// @ts-check
// M6 acceptance browser harness (runs in Env-W against the acceptance stack's
// frontend Nginx entry, never against component-level mock servers).
//
// Evidence policy (acceptance plan 2026-09-19 §7, plan item 2.7):
//   - No HAR capture and no storageState persistence. Raw cookies, Bearer
//     tokens and model keys must not enter long-term evidence; this config is
//     the first control, sensitive-value scans are only a safety net.
//   - Evidence screenshots are taken deliberately through
//     e2e/evidence/safe-shot.mjs, which refuses to write when the page
//     content or URL contains a value from the temporary secret set.
//   - Traces stay 'retain-on-failure' (zip archives) and are scanned by
//     scripts/check-sensitive-evidence-deep.sh before evidence hand-off.
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './scenarios',
  outputDir: process.env.E2E_OUTPUT_DIR ?? 'test-results',
  timeout: 30_000,
  retries: 0,
  // Acceptance scenarios share controlled business state; parallel workers
  // would race shared fixtures, so execution stays sequential.
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['json', { outputFile: process.env.E2E_REPORT ?? 'test-results/report.json' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8088',
    actionTimeout: 10_000,
    trace: 'retain-on-failure',
    screenshot: 'off',
    video: 'off',
    // No storageState: every scenario authenticates itself and signs out.
  },
})
