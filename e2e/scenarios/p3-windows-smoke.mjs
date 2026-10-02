// Run from a Windows native shell with e2e dependencies installed:
//   node e2e/scenarios/p3-windows-smoke.mjs
// P3_BASE_URL selects the isolated stack; P3_OUTPUT_DIR selects an evidence directory.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve, join } from 'node:path'
import { chromium } from '@playwright/test'

const outputDir = resolve(process.env.P3_OUTPUT_DIR ?? 'artifacts/p3-acceptance')
const baseURL = process.env.P3_BASE_URL ?? 'http://127.0.0.1:8089'
const sizes = [{ width: 1280, height: 800 }, { width: 800, height: 600 }]
const results = []
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: sizes[0] })
const page = await context.newPage()
const username = `p3-smoke-${Date.now()}`
const password = `P3!${crypto.randomUUID().replaceAll('-', '')}Aa1`

await mkdir(outputDir, { recursive: true })

async function check(name, action) {
  try {
    const detail = await action()
    results.push({ name, status: 'PASS', detail: detail ?? null })
  } catch (error) {
    results.push({ name, status: 'FAIL', error: String(error?.message ?? error).slice(0, 500) })
  }
}

async function open(path, heading) {
  await page.goto(new URL(path, baseURL).href)
  await page.getByRole('heading', { name: heading, exact: true }).waitFor({ timeout: 10000 })
}

async function layout(label, path, heading, size, screenshot = false) {
  await page.setViewportSize(size)
  await open(path, heading)
  const m = await page.evaluate(() => ({
    innerWidth, innerHeight,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
    visibleButtons: [...document.querySelectorAll('button')].filter(x => x.getClientRects().length).length,
  }))
  assert.equal(m.innerWidth, size.width)
  assert.equal(m.innerHeight, size.height)
  assert.ok(m.documentWidth <= m.innerWidth, `${label}: horizontal document overflow ${m.documentWidth} > ${m.innerWidth}`)
  if (screenshot) await page.screenshot({ path: join(outputDir, `p3-${label}-${size.width}x${size.height}.png`) })
  return m
}

try {
  for (const size of sizes) {
    await check(`public-catalog-${size.width}x${size.height}`, () => layout('catalog', '/catalog', '书目检索', size, true))
    await check(`public-ai-${size.width}x${size.height}`, () => layout('ai', '/assistant', 'AI 助手', size))
    await check(`login-${size.width}x${size.height}`, () => layout('login', '/login', '登录', size))
  }

  await page.setViewportSize(sizes[0])
  await check('catalog-search-and-detail', async () => {
    await open('/catalog', '书目检索')
    await page.getByTestId('portal-search-input').fill('E2E探路')
    await page.getByTestId('portal-search-submit').click()
    const link = page.locator('[data-testid^="portal-title-link-"]').first()
    await link.waitFor({ timeout: 10000 })
    await link.click()
    await page.getByTestId('title-detail-name').waitFor({ timeout: 10000 })
    assert.match(await page.getByTestId('title-detail-name').innerText(), /E2E探路/)
    return { path: new URL(page.url()).pathname }
  })

  await check('register-reader', async () => {
    await open('/register', '注册读者账户')
    await page.getByTestId('register-username').fill(username)
    await page.getByTestId('register-password').fill(password)
    await page.getByTestId('register-display-name').fill('P3 自动验收读者')
    await page.getByTestId('register-submit').click()
    await page.getByTestId('register-success').waitFor({ timeout: 10000 })
    return { testAccount: username }
  })

  await check('wrong-password-rejected', async () => {
    await open('/login', '登录')
    await page.getByTestId('login-username').fill(username)
    await page.getByTestId('login-password').fill('invalid-for-p3')
    await page.getByTestId('login-submit').click()
    await page.getByTestId('error-alert').waitFor({ timeout: 10000 })
    assert.equal(await page.getByTestId('logout-button').count(), 0)
  })

  await check('reader-login', async () => {
    await open('/login', '登录')
    await page.getByTestId('login-username').fill(username)
    await page.getByTestId('login-password').fill(password)
    await page.getByTestId('login-submit').click()
    await page.getByTestId('logout-button').waitFor({ timeout: 10000 })
    return { path: new URL(page.url()).pathname }
  })

  for (const size of sizes) {
    await check(`reader-business-${size.width}x${size.height}`, () => layout('reader-business', '/me/business', '我的业务', size, true))
    await check(`reader-fines-${size.width}x${size.height}`, () => layout('reader-fines', '/fines', '罚款与支付', size, true))
    await check(`reader-notifications-${size.width}x${size.height}`, () => layout('reader-notifications', '/notifications', '通知中心', size))
  }

  await check('reader-admin-route-denied', async () => {
    await page.goto(new URL('/admin/reviews', baseURL).href)
    await page.getByRole('heading', { name: '403' }).waitFor({ timeout: 10000 })
    return { path: new URL(page.url()).pathname }
  })

  await check('logout-protected-route', async () => {
    await page.goto(new URL('/me/business', baseURL).href)
    await page.getByTestId('logout-button').click()
    await page.goto(new URL('/me/business', baseURL).href)
    await page.getByRole('heading', { name: '登录' }).waitFor({ timeout: 10000 })
    return { path: new URL(page.url()).pathname }
  })
} finally {
  const report = {
    when: new Date().toISOString(), baseURL, browser: 'Google Chrome', browserVersion: browser.version(),
    viewports: sizes, results, counts: {
      passed: results.filter(x => x.status === 'PASS').length,
      failed: results.filter(x => x.status === 'FAIL').length,
    },
  }
  await writeFile(join(outputDir, 'p3-windows-smoke-results.json'), JSON.stringify(report, null, 2))
  await context.close()
  await browser.close()
  console.log(JSON.stringify(report, null, 2))
  if (report.counts.failed) process.exitCode = 1
}
