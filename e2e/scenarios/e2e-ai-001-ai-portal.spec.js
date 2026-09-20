// @ts-check
// E2E-AI-001: anonymous AI portal on the real acceptance stack with the real
// model endpoint (acceptance plan 2026-09-19, phase 3 probe). Visitor session
// start, catalog-grounded question, quota decrement, in-page session
// continuity and event stream. The backend wire format is fixed to
// thinking.type=disabled (matching the endpoint smoke's THINKING_MODE=disabled).
// Evidence policy: no HAR, no storageState; screenshots only via safe-shot.
import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSecretValues, safeShot } from '../evidence/safe-shot.mjs'

const evidenceDir = fileURLToPath(new URL('../../artifacts/m6-acceptance/phase3/e2e-ai-001/', import.meta.url))
const secrets = loadSecretValues([
  fileURLToPath(new URL('../../.env', import.meta.url)),
  fileURLToPath(new URL('../../.env.acceptance', import.meta.url)),
])

test('E2E-AI-001 访客 AI 会话：启动→提问→额度递减→会话连续→事件流', async ({ page }) => {
  test.setTimeout(300_000)
  mkdirSync(evidenceDir, { recursive: true })
  const shot = (name) => join(evidenceDir, name)

  await page.goto('/assistant')
  await expect(page.getByTestId('ai-session-id')).not.toContainText('—')
  await expect(page.getByTestId('ai-role')).toHaveText('VISITOR')
  const quotaBefore = Number((await page.getByTestId('ai-quota-remaining').innerText()).trim())
  expect(Number.isFinite(quotaBefore)).toBeTruthy()

  await page.getByTestId('ai-input').fill('请检索"数据库系统概念"相关书目并介绍')
  await page.getByTestId('ai-send').click()
  await expect(page.locator('[data-testid="ai-messages"] .turn.assistant').first()).toBeVisible({ timeout: 180_000 })
  await expect(page.getByTestId('ai-quota-remaining')).not.toHaveText(String(quotaBefore))
  const quotaAfterFirst = Number((await page.getByTestId('ai-quota-remaining').innerText()).trim())
  const sessionId = (await page.getByTestId('ai-session-id').innerText()).trim()
  await safeShot(page, shot('01-first-answer.png'), secrets)

  await page.getByTestId('ai-input').fill('刚才检索到的书目属于哪个分类？')
  await page.getByTestId('ai-send').click()
  await expect(page.locator('[data-testid="ai-messages"] .turn.assistant')).toHaveCount(2, { timeout: 180_000 })
  await expect(page.getByTestId('ai-session-id')).toHaveText(sessionId)
  await expect(page.getByTestId('ai-quota-remaining')).not.toHaveText(String(quotaAfterFirst))
  await safeShot(page, shot('02-second-answer.png'), secrets)

  await page.getByTestId('ai-events-toggle').click()
  await expect(page.getByTestId('ai-events-table')).toBeVisible()
  const eventRows = await page.locator('[data-testid="ai-events-table"] tbody tr').count()
  expect(eventRows).toBeGreaterThanOrEqual(2)
  await safeShot(page, shot('03-event-stream.png'), secrets)
})
