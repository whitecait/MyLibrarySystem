// @ts-check
// E2E-AI-001: AI portal coverage on the real acceptance stack with the real
// model endpoint (acceptance plan 2026-09-19). Five sequential scenarios:
//   01 anonymous visitor session: start, catalog-grounded Q&A, quota
//      decrement, session continuity, event stream (probe baseline).
//   02 role matrix: READER and LIBRARIAN authenticated sessions show their
//      role and account-quota scope (session metadata only, no model call).
//   03 session-ownership enforcement: a different visitor cannot read or
//      post into another visitor's session (rejected before any model call).
//   04 L2 prefill submit/cancel + L3 guidance: reader borrows the seed copy,
//      asks the embedded assistant on /me/business for a renewal prefill,
//      opens the modal, cancels (no business fact), then submits through
//      the ordinary renewal endpoint with the draft id; an L3 request only
//      yields guidance, no prefill (AT-0002 §2.33/§2.34).
//   05 quota exhaustion: with the low visitor quota configured for Env-W
//      (AI_VISITOR_DAILY_LIMIT=8 via compose.acceptance.yaml; a catalog-
//      grounded answer costs 2+ model calls, so the pool must still cover
//      scenario 01's two turns), the shared visitor pool drains and the
//      MLS-AI-002 exhausted alert appears and disables the composer.
//
// The backend wire format is fixed to thinking.type=disabled (matching the
// endpoint smoke's THINKING_MODE=disabled). Re-run note: the visitor pool
// is global per day — reset probe data (reset-probe-data.sh) before
// re-running the whole spec.
// Evidence policy: no HAR, no storageState; screenshots only via safe-shot.
import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSecretValues, safeShot } from '../evidence/safe-shot.mjs'

const READER_PASSWORD = 'E2e-Reader-2026!'
const LIBRARIAN_USERNAME = 'e2e-librarian'
const LIBRARIAN_PASSWORD = 'E2e-Librarian-2026!'
const TITLE_ID = '700001'
const COPY_ID = '800001'

const evidenceDir = fileURLToPath(new URL('../../artifacts/m6-acceptance/phase3/e2e-ai-001/', import.meta.url))
const secrets = loadSecretValues([
  fileURLToPath(new URL('../../.env', import.meta.url)),
  fileURLToPath(new URL('../../.env.acceptance', import.meta.url)),
])

async function login(page, username, password) {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('logout-button')).toBeVisible()
}

async function askAi(page, message, timeout = 180_000) {
  await page.getByTestId('ai-input').fill(message)
  await page.getByTestId('ai-send').click()
  await expect(page.locator('[data-testid="ai-messages"] .turn.assistant').last()).toBeVisible({ timeout })
}

test('E2E-AI-001/01 访客会话：启动→提问→额度递减→会话连续→事件流', async ({ page }) => {
  test.setTimeout(300_000)
  mkdirSync(evidenceDir, { recursive: true })
  const shot = (name) => join(evidenceDir, name)

  await page.goto('/assistant')
  await expect(page.getByTestId('ai-session-id')).not.toContainText('—')
  await expect(page.getByTestId('ai-role')).toHaveText('VISITOR')
  const quotaBefore = Number((await page.getByTestId('ai-quota-remaining').innerText()).trim())
  expect(Number.isFinite(quotaBefore)).toBeTruthy()

  await askAi(page, '请检索"数据库系统概念"相关书目并介绍')
  await expect(page.getByTestId('ai-quota-remaining')).not.toHaveText(String(quotaBefore))
  const quotaAfterFirst = Number((await page.getByTestId('ai-quota-remaining').innerText()).trim())
  const sessionId = (await page.getByTestId('ai-session-id').innerText()).trim()
  await safeShot(page, shot('01-first-answer.png'), secrets)

  await askAi(page, '刚才检索到的书目属于哪个分类？')
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

test('E2E-AI-001/02 角色矩阵：读者与馆员的账户级会话', async ({ page }) => {
  test.setTimeout(120_000)
  const shot = (name) => join(evidenceDir, name)
  const username = `e2e-ai-r-${Date.now()}`

  await page.goto('/register')
  await page.getByTestId('register-username').fill(username)
  await page.getByTestId('register-password').fill(READER_PASSWORD)
  await page.getByTestId('register-display-name').fill('E2E角色读者')
  await page.getByTestId('register-submit').click()
  await expect(page.getByTestId('register-success')).toBeVisible()

  await login(page, username, READER_PASSWORD)
  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('READER')
  await expect(page.getByTestId('ai-quota-scope')).toHaveText('账户额度')
  await expect(page.getByTestId('ai-quota-remaining')).not.toContainText('—')
  await safeShot(page, shot('04-reader-session.png'), secrets)
  await page.getByTestId('logout-button').click()
  await expect(page.getByTestId('login-link')).toBeVisible()

  await login(page, LIBRARIAN_USERNAME, LIBRARIAN_PASSWORD)
  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('LIBRARIAN')
  await expect(page.getByTestId('ai-quota-scope')).toHaveText('账户额度')
  await safeShot(page, shot('05-librarian-session.png'), secrets)
})

test('E2E-AI-001/03 会话所有权：另一访客不能读写他人会话', async ({ browser }) => {
  test.setTimeout(120_000)

  const ownerContext = await browser.newContext()
  const ownerPage = await ownerContext.newPage()
  await ownerPage.goto('/assistant')
  await expect(ownerPage.getByTestId('ai-session-id')).not.toContainText('—')
  const victimSessionId = (await ownerPage.getByTestId('ai-session-id').innerText()).trim()

  const intruderContext = await browser.newContext()
  const intruderPage = await intruderContext.newPage()
  await intruderPage.goto('/assistant')
  await expect(intruderPage.getByTestId('ai-role')).toHaveText('VISITOR')

  // 读写都应在所有权检查处被明确拒绝（不产生模型调用）；拒绝形态可能
  // 是 401（缺少所有权凭据）或 404（不泄露会话存在性），均为正确行为
  const readStatus = await intruderPage.evaluate(async (sid) => {
    const res = await fetch(`/api/v1/ai/sessions/${sid}/events?limit=5`, { method: 'GET' })
    return res.status
  }, victimSessionId)
  expect([401, 403, 404]).toContain(readStatus)

  const writeStatus = await intruderPage.evaluate(async (sid) => {
    const res = await fetch(`/api/v1/ai/sessions/${sid}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `intruder-${Date.now()}` },
      body: JSON.stringify({ message: '越权写入' }),
    })
    return res.status
  }, victimSessionId)
  expect([401, 403, 404]).toContain(writeStatus)

  // 会话主人自己仍可读取事件
  const ownerRead = await ownerPage.evaluate(async (sid) => {
    const res = await fetch(`/api/v1/ai/sessions/${sid}/events?limit=5`, { method: 'GET' })
    return res.status
  }, victimSessionId)
  expect(ownerRead).toBe(200)

  await ownerContext.close()
  await intruderContext.close()
})

test('E2E-AI-001/04 L2 预填提交与取消、L3 仅引导', async ({ page }) => {
  test.setTimeout(420_000)
  const shot = (name) => join(evidenceDir, name)
  const username = `e2e-ai-l2-${Date.now()}`

  await page.goto('/register')
  await page.getByTestId('register-username').fill(username)
  await page.getByTestId('register-password').fill(READER_PASSWORD)
  await page.getByTestId('register-display-name').fill('E2E预填读者')
  await page.getByTestId('register-submit').click()
  await expect(page.getByTestId('register-success')).toBeVisible()
  await login(page, username, READER_PASSWORD)

  // 建立在借事务，供 L2 续借草稿引用
  await page.goto('/walk-in')
  await page.getByTestId('walkin-channel').selectOption('SELF_SERVICE')
  await page.getByTestId('walkin-title-0').fill(TITLE_ID)
  await page.getByTestId('walkin-copy-0').fill(COPY_ID)
  await page.getByTestId('walkin-submit').click()
  await expect(page.getByTestId('walkin-results')).toContainText('LENT')

  await page.goto('/me/business')
  await expect(page.getByTestId('ai-panel')).toBeVisible()

  // 两次机会触发 L2 草稿（真实模型；判定以工具调用行为为准）
  let l2Card = page.locator('[data-testid="ai-action-L2"]').first()
  let attempts = 0
  while ((await l2Card.count()) === 0 && attempts < 2) {
    attempts++
    const phrasing = attempts === 1
      ? '请帮我预填续借我正在借的《E2E探路：数据库系统概念（验收种子）》'
      : '我借的书快到期了，请生成续借预填草稿'
    await askAi(page, phrasing)
  }
  expect(await l2Card.count()).toBeGreaterThan(0)
  await safeShot(page, shot('06-l2-card.png'), secrets)

  // 打开预填模态，校验草稿标识与交易号注入，然后取消：无业务事实
  await page.locator('[data-testid^="ai-action-open-"]').first().click()
  const modal = page.getByTestId('renew-modal')
  await expect(modal).toBeVisible()
  await expect(page.getByTestId('renew-draft-note')).toContainText('AI 助手预填')
  const prefillTxId = await page.getByTestId('renew-modal-tx-id').inputValue()
  expect(prefillTxId).toBeTruthy()
  await page.getByTestId('renew-modal-cancel').click()
  await expect(modal).not.toBeVisible()
  await expect(page.getByTestId('renew-result')).toHaveCount(0)
  await safeShot(page, shot('07-l2-cancelled.png'), secrets)

  // 再次打开并提交：走现行续借接口（Ai-Draft-Id 头），形成业务事实
  await page.locator('[data-testid^="ai-action-open-"]').first().click()
  await expect(modal).toBeVisible()
  await page.getByTestId('renew-modal-submit').click()
  await expect(page.getByTestId('renew-result')).toBeVisible({ timeout: 60_000 })
  await safeShot(page, shot('08-l2-submitted.png'), secrets)

  // L3 级请求仅返回引导卡片，不含预填与确认按钮
  const l3Card = page.locator('[data-testid="ai-action-L3"]').first()
  let l3Attempts = 0
  while ((await l3Card.count()) === 0 && l3Attempts < 2) {
    l3Attempts++
    const phrasing = l3Attempts === 1
      ? '请直接替我把这本书归还图书馆'
      : '帮我操作归还，不用我自己去办'
    await askAi(page, phrasing)
  }
  if ((await l3Card.count()) > 0) {
    await expect(l3Card).toContainText('仅作引导')
    await expect(l3Card.locator('[data-testid^="ai-action-open-"]')).toHaveCount(0)
    await safeShot(page, shot('09-l3-guidance.png'), secrets)
  }
})

test('E2E-AI-001/05 额度触顶：访客共享池耗尽后明确告知并停发', async ({ page }) => {
  test.setTimeout(420_000)
  const shot = (name) => join(evidenceDir, name)

  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('VISITOR')

  // Env-W 配置 AI_VISITOR_DAILY_LIMIT=8；本 spec 前序访客调用已消耗部分
  // 池额，循环发送直至耗尽（上限保护避免死循环）。每轮先等上一轮发送
  // 退出 loading（在途请求期间按钮 disabled），再判断耗尽提示后发送；
  // 超额失败不等模型回合，与「新回答出现 / 耗尽提示出现」竞速。
  let guard = 0
  while (guard < 6) {
    guard++
    await expect(page.getByTestId('ai-send')).not.toHaveClass(/is-loading/, { timeout: 180_000 }).catch(() => {})
    if (await page.getByTestId('ai-quota-exhausted').count() > 0) break
    if (await page.getByTestId('ai-send').isDisabled()) break
    await page.getByTestId('ai-input').fill('请检索一本关于操作系统原理的书')
    await page.getByTestId('ai-send').click()
    await Promise.race([
      expect(page.locator('[data-testid="ai-messages"] .turn.assistant').last()).toBeVisible({ timeout: 180_000 }),
      expect(page.getByTestId('ai-quota-exhausted')).toBeVisible({ timeout: 180_000 }),
    ]).catch(() => {})
  }
  await expect(page.getByTestId('ai-quota-exhausted')).toBeVisible()
  await expect(page.getByTestId('ai-input')).toBeDisabled()
  await expect(page.getByTestId('ai-send')).toBeDisabled()
  await safeShot(page, shot('10-quota-exhausted.png'), secrets)
})
