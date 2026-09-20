// @ts-check
// E2E-001: circulation core flow on the real acceptance stack (acceptance
// plan 2026-09-19, phase 3 probe). One sequential scenario over the seeded
// probe dataset: register → login → search → title detail → self-service
// walk-in loan → my-business + renew → librarian return.
//
// Required seed (artifacts/m6-acceptance/phase3/probe-seed.sql, Env-W project
// m6-acceptance): book_title 700001, book_copy 800001/800002 SHELF_READY and
// the throwaway LIBRARIAN probe account below.
// Evidence policy: no HAR, no storageState; screenshots only via safe-shot.
import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSecretValues, safeShot } from '../evidence/safe-shot.mjs'

const READER_PASSWORD = 'E2e-Reader-2026!'
const LIBRARIAN_USERNAME = 'e2e-librarian'
const LIBRARIAN_PASSWORD = 'E2e-Librarian-2026!'
const TITLE_ID = '700001'
const COPY_ID = '800001'

const evidenceDir = fileURLToPath(new URL('../../artifacts/m6-acceptance/phase3/e2e-001/', import.meta.url))
const secrets = loadSecretValues([
  fileURLToPath(new URL('../../.env', import.meta.url)),
  fileURLToPath(new URL('../../.env.acceptance', import.meta.url)),
])

test('E2E-001 流通主流程：注册→登录→检索→自助借阅→续借→馆员归还', async ({ page }) => {
  test.setTimeout(120_000)
  mkdirSync(evidenceDir, { recursive: true })
  const shot = (name) => join(evidenceDir, name)
  const username = `e2e-r-${Date.now()}`

  await page.goto('/register')
  await page.getByTestId('register-username').fill(username)
  await page.getByTestId('register-password').fill(READER_PASSWORD)
  await page.getByTestId('register-display-name').fill('E2E探路读者')
  await page.getByTestId('register-submit').click()
  await expect(page.getByTestId('register-success')).toBeVisible()
  await safeShot(page, shot('01-register-success.png'), secrets)

  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(READER_PASSWORD)
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('logout-button')).toBeVisible()

  await page.goto('/catalog')
  await page.getByTestId('portal-search-input').fill('E2E探路')
  await page.getByTestId('portal-search-submit').click()
  const titleLink = page.locator('[data-testid^="portal-title-link-"]').first()
  await expect(titleLink).toBeVisible()
  await titleLink.click()
  await expect(page.getByTestId('title-detail-name')).toContainText('E2E探路')
  await safeShot(page, shot('02-title-detail.png'), secrets)

  await page.goto('/walk-in')
  await page.getByTestId('walkin-channel').selectOption('SELF_SERVICE')
  await page.getByTestId('walkin-title-0').fill(TITLE_ID)
  await page.getByTestId('walkin-copy-0').fill(COPY_ID)
  await page.getByTestId('walkin-submit').click()
  await expect(page.getByTestId('walkin-results')).toBeVisible()
  // 成功行的结果标签为 LENT；REJECTED 行携带拒绝原因（如副本不可借）
  await expect(page.getByTestId('walkin-results')).toContainText('LENT')
  await safeShot(page, shot('03-walkin-results.png'), secrets)

  await page.goto('/me/business')
  await page.getByTestId('my-business-kind').selectOption('LOAN')
  await page.getByTestId('my-business-submit').click()
  const renewButton = page.locator('[data-testid^="renew-"]').first()
  await expect(renewButton).toBeVisible()
  await safeShot(page, shot('04-my-business-loan.png'), secrets)
  await renewButton.click()
  await expect(page.getByTestId('renew-result')).toBeVisible()
  await safeShot(page, shot('05-renew-success.png'), secrets)

  await page.getByTestId('logout-button').click()
  // 等待本地会话清理完成（登出后头部回到「登录」入口），再切换馆员登录
  await expect(page.getByTestId('login-link')).toBeVisible()
  await page.goto('/login')
  await page.getByTestId('login-username').fill(LIBRARIAN_USERNAME)
  await page.getByTestId('login-password').fill(LIBRARIAN_PASSWORD)
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('logout-button')).toBeVisible()

  await page.goto('/desk')
  await page.getByTestId('return-copy-id').fill(COPY_ID)
  await page.getByTestId('return-condition').selectOption('INTACT')
  await page.getByTestId('return-submit').click()
  await expect(page.getByTestId('return-returned')).toBeVisible()
  await safeShot(page, shot('06-return-success.png'), secrets)
})
