// @ts-check
// E2E-001: circulation core flow on the real acceptance stack (acceptance
// plan 2026-09-19). Three self-contained sequential scenarios over the
// seeded probe dataset (artifacts/m6-acceptance/phase3/probe-seed.sql, Env-W
// project m6-acceptance):
//   01 register → login → search → detail → walk-in loan → renew →
//      librarian return, plus server-side session teardown verification
//      (the sign-out request must carry the Bearer token, and the old token
//      must be rejected afterwards — DEF-M6-004 reopen).
//   02 simulated-payment loop: borrow copy 800002 → DAMAGED return (fine) →
//      reader order → admin schedules SUCCESS via the simulator (with the
//      prominent simulated-payment notice) → fine reaches PAID.
//   03 manual review: admin submits an account SUSPEND change for review →
//      rejects it in /admin/reviews → reader account stays usable.
//
// Required seed: book_title 700001, book_copy 800001/800002 SHELF_READY,
// throwaway accounts e2e-librarian (600001) and e2e-admin (600002).
// Re-run prerequisite: bash artifacts/m6-acceptance/phase3/reset-probe-data.sh
// (the DAMAGED return leaves copy 800002 in MAINTENANCE until reset).
// Each scenario registers its own reader: no cross-test shared state.
// Evidence policy: no HAR, no storageState; screenshots only via safe-shot.
import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSecretValues, safeShot } from '../evidence/safe-shot.mjs'

const READER_PASSWORD = 'E2e-Reader-2026!'
const LIBRARIAN_USERNAME = 'e2e-librarian'
const LIBRARIAN_PASSWORD = 'E2e-Librarian-2026!'
const ADMIN_USERNAME = 'e2e-admin'
const ADMIN_PASSWORD = 'E2e-Admin-2026!'
const TITLE_ID = '700001'
const COPY_ID = '800001'
const DAMAGED_COPY_ID = '800002'

const evidenceDir = fileURLToPath(new URL('../../artifacts/m6-acceptance/phase3/e2e-001/', import.meta.url))
const secrets = loadSecretValues([
  fileURLToPath(new URL('../../.env', import.meta.url)),
  fileURLToPath(new URL('../../.env.acceptance', import.meta.url)),
])

async function registerReader(page, username, displayName) {
  await page.goto('/register')
  await page.getByTestId('register-username').fill(username)
  await page.getByTestId('register-password').fill(READER_PASSWORD)
  await page.getByTestId('register-display-name').fill(displayName)
  await page.getByTestId('register-submit').click()
  await expect(page.getByTestId('register-success')).toBeVisible()
}

async function login(page, username, password) {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('logout-button')).toBeVisible()
}

async function logoutViaUi(page) {
  await page.getByTestId('logout-button').click()
  await expect(page.getByTestId('login-link')).toBeVisible()
}

test('E2E-001/01 流通主流程与登出会话终结：注册→登录→检索→借阅→续借→归还→旧令牌失效', async ({ page }) => {
  test.setTimeout(180_000)
  mkdirSync(evidenceDir, { recursive: true })
  const shot = (name) => join(evidenceDir, name)
  const username = `e2e-r-${Date.now()}`

  await registerReader(page, username, 'E2E探路读者')
  await safeShot(page, shot('01-register-success.png'), secrets)

  await login(page, username, READER_PASSWORD)
  // 抓取当前令牌：登出后必须验证该令牌在服务端已失效（DEF-M6-004 重开）
  const oldToken = await page.evaluate(() => JSON.parse(sessionStorage.getItem('mls.session.v1')).token)
  expect(oldToken).toBeTruthy()

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

  await logoutViaUi(page)
  // 注销请求带令牌发出后，服务端会话必须终结：旧令牌访问受保护接口被拒
  const oldTokenStatus = await page.evaluate(async (token) => {
    const res = await fetch('/api/v1/me/business?kind=LOAN&limit=5', {
      headers: { Authorization: `Bearer ${token}` },
    })
    return res.status
  }, oldToken)
  expect(oldTokenStatus).toBe(401)
  await safeShot(page, shot('06-old-token-rejected.png'), secrets)

  await login(page, LIBRARIAN_USERNAME, LIBRARIAN_PASSWORD)
  await page.goto('/desk')
  await page.getByTestId('return-copy-id').fill(COPY_ID)
  await page.getByTestId('return-condition').selectOption('INTACT')
  await page.getByTestId('return-submit').click()
  await expect(page.getByTestId('return-returned')).toBeVisible()
  await safeShot(page, shot('07-return-success.png'), secrets)
})

test('E2E-001/02 模拟支付闭环：损毁归还→罚款→建单→模拟成功→已支付', async ({ page }) => {
  test.setTimeout(180_000)
  const shot = (name) => join(evidenceDir, name)
  const username = `e2e-pay-r-${Date.now()}`
  await registerReader(page, username, 'E2E支付读者')

  // 读者借出第二副本（800002），供损毁归还产生罚款
  await login(page, username, READER_PASSWORD)
  await page.goto('/walk-in')
  await page.getByTestId('walkin-channel').selectOption('SELF_SERVICE')
  await page.getByTestId('walkin-title-0').fill(TITLE_ID)
  await page.getByTestId('walkin-copy-0').fill(DAMAGED_COPY_ID)
  await page.getByTestId('walkin-submit').click()
  await expect(page.getByTestId('walkin-results')).toContainText('LENT')
  await logoutViaUi(page)

  // 馆员按损坏归还：事务转 RETURN_ABNORMAL，罚款按书价全额同事务入账
  // （API 枚举 DUE→UNPAID）；无需人工处置（那是预约 MANUAL_HANDLING 流）
  await login(page, LIBRARIAN_USERNAME, LIBRARIAN_PASSWORD)
  await page.goto('/desk')
  await page.getByTestId('return-copy-id').fill(DAMAGED_COPY_ID)
  await page.getByTestId('return-condition').selectOption('DAMAGED')
  await page.getByTestId('return-submit').click()
  await expect(page.getByTestId('return-abnormal')).toBeVisible()
  await safeShot(page, shot('08-damaged-return-fine.png'), secrets)
  await logoutViaUi(page)

  // 读者对未支付罚款建单
  await login(page, username, READER_PASSWORD)
  await page.goto('/fines')
  await page.getByTestId('fines-status').selectOption('UNPAID')
  await page.getByTestId('fines-submit').click()
  const createOrderButton = page.locator('[data-testid^="create-order-"]').first()
  await expect(createOrderButton).toBeVisible()
  await safeShot(page, shot('09-unpaid-fine.png'), secrets)
  await createOrderButton.click()
  const orderPanel = page.getByTestId('order-panel-title')
  await expect(orderPanel).toBeVisible()
  await expect(page.getByTestId('order-amount')).toBeVisible()
  const orderText = await orderPanel.innerText()
  const orderId = (orderText.match(/支付单\s*(\d+)/) ?? [])[1]
  expect(orderId).toBeTruthy()
  await safeShot(page, shot('10-order-created.png'), secrets)
  await logoutViaUi(page)

  // 管理员经模拟支付调度页安排成功结果；页面显著提示模拟性质（RA-0013 §5）
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto('/admin/simulator')
  await expect(page.getByRole('heading', { name: /模拟支付调度/ })).toBeVisible()
  await expect(page.locator('.payment-simulator-view .note')).toContainText('模拟')
  await page.getByTestId('simulator-order-id').fill(orderId)
  await page.getByTestId('simulator-result').selectOption('SUCCESS')
  await page.getByTestId('simulator-submit').click()
  await expect(page.getByTestId('simulator-scheduled')).toBeVisible()
  await safeShot(page, shot('11-simulator-scheduled.png'), secrets)
  await logoutViaUi(page)

  // 回调落地后罚款转为已支付（近实时回调，留出落地余量再查）
  await page.waitForTimeout(2000)
  await login(page, username, READER_PASSWORD)
  await page.goto('/fines')
  await page.getByTestId('fines-status').selectOption('PAID')
  await page.getByTestId('fines-submit').click()
  await expect(page.getByTestId('fines-table')).toContainText('PAID', { timeout: 20_000 })
  await safeShot(page, shot('12-fine-paid.png'), secrets)
  await logoutViaUi(page)
})

test('E2E-001/03 人工复核：账户变更提交复核→复核拒绝→账户保持可用', async ({ page }) => {
  test.setTimeout(180_000)
  const shot = (name) => join(evidenceDir, name)
  const username = `e2e-rev-r-${Date.now()}`
  await registerReader(page, username, 'E2E复核读者')

  // 管理员在账户管理页定位该读者并取得账户号（列表查询为 ADMIN 能力；
  // 新注册账户按序排在尾页，翻页加载直到出现）
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto('/admin/accounts')
  await page.getByTestId('account-filter-submit').click()
  const readerRow = page.locator('[data-testid="account-table"] tbody tr', { hasText: username }).first()
  for (let i = 0; i < 12 && (await readerRow.count()) === 0; i++) {
    const loadMore = page.getByTestId('account-load-more')
    if ((await loadMore.count()) === 0) break
    await loadMore.click()
  }
  await expect(readerRow).toBeVisible()
  const accountId = (await readerRow.locator('[data-testid^="account-select-"]').first().getAttribute('data-testid')).replace('account-select-', '')
  expect(accountId).toBeTruthy()
  await safeShot(page, shot('13-reader-located.png'), secrets)
  await logoutViaUi(page)

  // 提交账户变更为 LIBRARIAN 能力（角色矩阵 LIBRARIAN_THEN_ADMIN_REVIEW），
  // 而账户管理页面挂在 ADMIN-only 路由上——页面上的「提交复核」对能进入
  // 页面的唯一角色必然被 MLS-ID-005 拒绝（DEF-M6-005），因此经馆员
  // 会话以 API 发起变更申请。
  await login(page, LIBRARIAN_USERNAME, LIBRARIAN_PASSWORD)
  const changeStatus = await page.evaluate(async (id) => {
    const token = JSON.parse(sessionStorage.getItem('mls.session.v1')).token
    const res = await fetch(`/api/v1/accounts/${id}/change-requests`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': `e2e-change-${Date.now()}`,
      },
      body: JSON.stringify({ action: 'SUSPEND', payload: {}, reason: 'E2E 复核链路探路：提交停用复核' }),
    })
    return res.status
  }, accountId)
  expect(changeStatus).toBeLessThan(300)
  await logoutViaUi(page)

  // 管理员在复核决定页拒绝该申请，读者账户保持可用
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto('/admin/reviews')
  await page.getByTestId('review-status').selectOption('PENDING')
  await page.getByTestId('review-submit').click()
  const decideButton = page.locator('[data-testid^="review-decide-"]').first()
  await expect(decideButton).toBeVisible()
  await decideButton.click()
  await page.getByTestId('decision').selectOption('REJECTED')
  await page.getByTestId('decision-reason').fill('E2E 复核链路探路：拒绝停用，保持账户可用')
  await page.getByTestId('decision-submit').click()
  await expect(page.getByTestId('decision-success')).toBeVisible()
  await safeShot(page, shot('14-review-rejected.png'), secrets)
  await logoutViaUi(page)

  // 复核被拒后读者仍可登录（停用未生效）
  await login(page, username, READER_PASSWORD)
  await safeShot(page, shot('15-reader-still-active.png'), secrets)
  await logoutViaUi(page)
})
