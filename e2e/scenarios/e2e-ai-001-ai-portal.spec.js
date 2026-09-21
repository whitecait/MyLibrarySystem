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
//   05 quota exhaustion: with the Env-W acceptance quota set explicitly in
//      .env.acceptance (AI_VISITOR_DAILY_LIMIT=8, registered in the
//      acceptance plan 2026-09-19 run log item ④; the protocol default stays
//      10/day via application.yml; a catalog-grounded answer costs 2+ model
//      calls, so the pool must still cover scenario 01's two turns), the
//      shared visitor pool drains, the MLS-AI-002 exhausted alert appears
//      and disables the composer, and a direct API attempt is rejected with
//      MLS-AI-002 while the session's MODEL_REQUEST event count stops
//      growing (backend stop, independent of the disabled UI).
//
// Registration policy: each checked item is registered as its own
// test.info().annotations entry (role-matrix items, ownership rejections,
// L2/L3 attempt counts, business-fact snapshots, quota-stop proof), so a
// combined pass never masks a single uncovered item. L3 that never triggers
// fails the test explicitly instead of passing silently.
//
// The backend wire format is fixed to thinking.type=disabled (matching the
// endpoint smoke's THINKING_MODE=disabled). Re-run note: the visitor pool
// is global per day — reset probe data (reset-probe-data.sh) before
// re-running the whole spec.
// Evidence policy: no HAR, no storageState; screenshots only via safe-shot.
import { test, expect } from '@playwright/test'
import { mkdirSync, readFileSync } from 'node:fs'
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

function readEnvFileValue(file, key) {
  try {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#')) continue
      const eq = trimmed.indexOf('=')
      if (eq === -1) continue
      if (trimmed.slice(0, eq).trim() === key) return trimmed.slice(eq + 1).trim()
    }
  } catch {
    // 文件不存在（.env.acceptance 由 scripts/prepare-acceptance-env.sh 生成）时
    // 返回 undefined，由使用方显式失败，不回退猜测值。
  }
  return undefined
}

// 额度口径唯一来源（2026-09-21 统一）：Env-W 验收值由 .env.acceptance 显式设定
// （AI_VISITOR_DAILY_LIMIT=8，prepare-acceptance-env.sh 写入）；E2E 运行环境变量
// E2E_AI_VISITOR_DAILY_LIMIT 可覆盖；两者都缺失时额度场景显式失败。
const VISITOR_DAILY_LIMIT = Number(
  process.env.E2E_AI_VISITOR_DAILY_LIMIT
    ?? readEnvFileValue(fileURLToPath(new URL('../../.env.acceptance', import.meta.url)), 'AI_VISITOR_DAILY_LIMIT')
    ?? readEnvFileValue(fileURLToPath(new URL('../../.env', import.meta.url)), 'AI_VISITOR_DAILY_LIMIT'),
)

// 经页面上下文（自带会话 Cookie）分页拉取信封接口的 items 列表。
// 信封形态：{ code, message, data: { items, nextCursor } }。
async function fetchEnvelopeItems(page, path, label) {
  const items = []
  let cursor
  for (let pageIndex = 0; pageIndex < 50; pageIndex++) {
    const envelope = await page.evaluate(async ({ requestPath, cursorValue }) => {
      const params = new URLSearchParams({ limit: '100' })
      if (cursorValue) params.set('cursor', cursorValue)
      const res = await fetch(`${requestPath}${requestPath.includes('?') ? '&' : '?'}${params.toString()}`)
      return { status: res.status, body: res.ok ? await res.json() : null }
    }, { requestPath: path, cursorValue: cursor })
    expect(envelope.status, `${label} 应可读取（实际 HTTP ${envelope.status}）`).toBe(200)
    const data = envelope.body?.data ?? {}
    items.push(...(data.items ?? []))
    if (!data.nextCursor) return items
    cursor = data.nextCursor
  }
  return items
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
  // 分项登记（不合并）：读者角色的会话元数据逐项入 annotations
  test.info().annotations.push(
    { type: 'ai-matrix-reader-role', description: (await page.getByTestId('ai-role').innerText()).trim() },
    { type: 'ai-matrix-reader-quota-scope', description: (await page.getByTestId('ai-quota-scope').innerText()).trim() },
    { type: 'ai-matrix-reader-quota-remaining', description: (await page.getByTestId('ai-quota-remaining').innerText()).trim() },
  )
  await safeShot(page, shot('04-reader-session.png'), secrets)
  await page.getByTestId('logout-button').click()
  await expect(page.getByTestId('login-link')).toBeVisible()

  await login(page, LIBRARIAN_USERNAME, LIBRARIAN_PASSWORD)
  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('LIBRARIAN')
  await expect(page.getByTestId('ai-quota-scope')).toHaveText('账户额度')
  // 分项登记：馆员角色的会话元数据逐项入 annotations
  test.info().annotations.push(
    { type: 'ai-matrix-librarian-role', description: (await page.getByTestId('ai-role').innerText()).trim() },
    { type: 'ai-matrix-librarian-quota-scope', description: (await page.getByTestId('ai-quota-scope').innerText()).trim() },
    { type: 'ai-matrix-librarian-quota-remaining', description: (await page.getByTestId('ai-quota-remaining').innerText()).trim() },
  )
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
  test.info().annotations.push({ type: 'ownership-read-rejected', description: `跨访客读事件流被拒：HTTP ${readStatus}` })

  const writeStatus = await intruderPage.evaluate(async (sid) => {
    const res = await fetch(`/api/v1/ai/sessions/${sid}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `intruder-${Date.now()}` },
      body: JSON.stringify({ message: '越权写入' }),
    })
    return res.status
  }, victimSessionId)
  expect([401, 403, 404]).toContain(writeStatus)
  test.info().annotations.push({ type: 'ownership-write-rejected', description: `跨访客写消息被拒：HTTP ${writeStatus}` })

  // 会话主人自己仍可读取事件
  const ownerRead = await ownerPage.evaluate(async (sid) => {
    const res = await fetch(`/api/v1/ai/sessions/${sid}/events?limit=5`, { method: 'GET' })
    return res.status
  }, victimSessionId)
  expect(ownerRead).toBe(200)
  test.info().annotations.push({ type: 'ownership-owner-read', description: `会话主人自读事件流：HTTP ${ownerRead}` })

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
  // 业务事实基线：取消前该读者的业务事务列表（RESERVATION/LOAN/FINE，分页取全）
  const itemsBeforeCancel = await fetchEnvelopeItems(page, '/api/v1/me/business', '取消前业务事务')
  await page.getByTestId('renew-modal-cancel').click()
  await expect(modal).not.toBeVisible()
  await expect(page.getByTestId('renew-result')).toHaveCount(0)
  // 零业务提交证明：取消前后该读者业务事务列表逐项不变（无新事务、无状态迁移）
  const itemsAfterCancel = await fetchEnvelopeItems(page, '/api/v1/me/business', '取消后业务事务')
  expect(itemsAfterCancel).toEqual(itemsBeforeCancel)
  await safeShot(page, shot('07-l2-cancelled.png'), secrets)
  test.info().annotations.push(
    { type: 'l2-attempts', description: String(attempts) },
    { type: 'l2-cancel-business-unchanged', description: `取消前后业务事务列表逐项不变（${itemsBeforeCancel.length} 项）` },
  )

  // 再次打开并提交：走现行续借接口（Ai-Draft-Id 头），形成业务事实
  await page.locator('[data-testid^="ai-action-open-"]').first().click()
  await expect(modal).toBeVisible()
  await page.getByTestId('renew-modal-submit').click()
  await expect(page.getByTestId('renew-result')).toBeVisible({ timeout: 60_000 })
  const itemsAfterSubmit = await fetchEnvelopeItems(page, '/api/v1/me/business', '提交后业务事务')
  await safeShot(page, shot('08-l2-submitted.png'), secrets)
  test.info().annotations.push(
    { type: 'l2-submit-business-items', description: `提交后业务事务列表 ${itemsAfterSubmit.length} 项（登记用途：续借业务事实以流通域续借结果为准）` },
  )

  // L3 级请求仅返回引导卡片，不含预填与确认按钮。两次尝试都未出现时本测试
  // 显式失败并注明未覆盖：静默通过等于该断言从未执行（2026-09-21 补强）。
  const l3Card = page.locator('[data-testid="ai-action-L3"]').first()
  let l3Attempts = 0
  while ((await l3Card.count()) === 0 && l3Attempts < 2) {
    l3Attempts++
    const phrasing = l3Attempts === 1
      ? '请直接替我把这本书归还图书馆'
      : '帮我操作归还，不用我自己去办'
    await askAi(page, phrasing)
  }
  if ((await l3Card.count()) === 0) {
    throw new Error('L3 未触发（未覆盖）：两次真实模型尝试均未产生 L3 引导卡片，L3 断言未执行')
  }
  await expect(l3Card).toContainText('仅作引导')
  await expect(l3Card.locator('[data-testid^="ai-action-open-"]')).toHaveCount(0)
  await safeShot(page, shot('09-l3-guidance.png'), secrets)
  test.info().annotations.push(
    { type: 'l3-attempts', description: String(l3Attempts) },
    { type: 'l3-guidance-only', description: 'L3 卡片仅含引导文案，无打开表单入口' },
  )
})

test('E2E-AI-001/05 额度触顶：访客共享池耗尽后明确告知并停发', async ({ page }) => {
  test.setTimeout(420_000)
  const shot = (name) => join(evidenceDir, name)

  // 额度口径必须显式设定（.env.acceptance 或 E2E_AI_VISITOR_DAILY_LIMIT），
  // 缺失时本场景显式失败，不回退猜测值（2026-09-21 三处口径统一）。
  expect(
    Number.isFinite(VISITOR_DAILY_LIMIT) && VISITOR_DAILY_LIMIT > 0,
    'AI_VISITOR_DAILY_LIMIT 未显式设定：请运行 scripts/prepare-acceptance-env.sh 生成 .env.acceptance（含 AI_VISITOR_DAILY_LIMIT=8），或以 E2E_AI_VISITOR_DAILY_LIMIT 注入',
  ).toBe(true)
  test.info().annotations.push(
    { type: 'ai-visitor-daily-limit', description: String(VISITOR_DAILY_LIMIT) },
    { type: 'ai-visitor-daily-limit-source', description: 'E2E_AI_VISITOR_DAILY_LIMIT 或 .env.acceptance 的 AI_VISITOR_DAILY_LIMIT（Env-W 验收值 8，验收计划 2026-09-19 ④）' },
  )

  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('VISITOR')
  const sessionId = (await page.getByTestId('ai-session-id').innerText()).trim()

  // Env-W 验收值 AI_VISITOR_DAILY_LIMIT 由 .env.acceptance 显式设定；本 spec
  // 场景 01 已从同一访客共享池消耗部分额度，循环发送直至耗尽（上限保护避免
  // 死循环）。每轮显式等待上一轮发送的结局——「新回答出现」或「耗尽提示
  // 出现」二者之一——不用 Promise.race，也不吞掉任何断言失败；两者都未出现
  // 时 expect.poll 超时使本测试失败。
  const sendGuard = VISITOR_DAILY_LIMIT + 4
  for (let sent = 0; sent < sendGuard; sent++) {
    if (await page.getByTestId('ai-quota-exhausted').count() > 0) break
    if (await page.getByTestId('ai-send').isDisabled()) break
    const assistantTurnsBefore = await page.locator('[data-testid="ai-messages"] .turn.assistant').count()
    await page.getByTestId('ai-input').fill('请检索一本关于操作系统原理的书')
    await page.getByTestId('ai-send').click()
    await expect.poll(async () => {
      if (await page.getByTestId('ai-quota-exhausted').count() > 0) return 'exhausted'
      const assistantTurns = await page.locator('[data-testid="ai-messages"] .turn.assistant').count()
      return assistantTurns > assistantTurnsBefore ? 'answered' : 'pending'
    }, { timeout: 180_000 }).not.toBe('pending')
  }
  await expect(page.getByTestId('ai-quota-exhausted')).toBeVisible()
  await expect(page.getByTestId('ai-input')).toBeDisabled()
  await expect(page.getByTestId('ai-send')).toBeDisabled()
  await safeShot(page, shot('10-quota-exhausted.png'), secrets)

  // 后端停发证明（不依赖 UI 禁用态）：经 API 以新幂等键直接发起一条消息，
  // 应被 MLS-AI-002（HTTP 429）拒绝；随后该会话 MODEL_REQUEST 事件数不再
  // 增长——额度检查先于模型调用，被拒回合只追加 QUOTA_BLOCKED 事件。
  const eventsBefore = await fetchEnvelopeItems(page, `/api/v1/ai/sessions/${sessionId}/events`, '停发前会话事件流')
  const modelRequestsBefore = eventsBefore.filter((event) => event.type === 'MODEL_REQUEST').length
  const attempt = await page.evaluate(async ({ sid, key }) => {
    const res = await fetch(`/api/v1/ai/sessions/${sid}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      body: JSON.stringify({ message: '额度耗尽后的直接 API 尝试' }),
    })
    let bodyCode = null
    try { bodyCode = (await res.json()).code } catch { /* 非 JSON 响应体时保持 null */ }
    return { status: res.status, bodyCode }
  }, { sid: sessionId, key: `quota-stop-${Date.now()}` })
  expect(attempt.status, '额度耗尽后直接 API 发消息应被拒（HTTP 429）').toBe(429)
  expect(attempt.bodyCode, '拒绝码应为 MLS-AI-002').toBe('MLS-AI-002')
  const eventsAfter = await fetchEnvelopeItems(page, `/api/v1/ai/sessions/${sessionId}/events`, '停发后会话事件流')
  const modelRequestsAfter = eventsAfter.filter((event) => event.type === 'MODEL_REQUEST').length
  expect(modelRequestsAfter, '后端停发证明：MODEL_REQUEST 事件数不得再增长').toBe(modelRequestsBefore)
  const quotaBlockedEvents = eventsAfter.filter((event) => event.type === 'QUOTA_BLOCKED').length
  test.info().annotations.push(
    { type: 'ai-model-requests-before-stop', description: String(modelRequestsBefore) },
    { type: 'ai-model-requests-after-stop', description: `${modelRequestsAfter}（未增长）` },
    { type: 'ai-quota-blocked-events', description: String(quotaBlockedEvents) },
    { type: 'ai-stop-rejection', description: `HTTP ${attempt.status} ${attempt.bodyCode}` },
  )
})
