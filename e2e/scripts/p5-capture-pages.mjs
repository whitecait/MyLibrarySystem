// Windows-native, visible-browser screenshots for P5 visual review.
// The UI must come from the repair checkout's Vite preview. API calls can be
// proxied to a separately selected loopback stack; this script never starts,
// upgrades, or stops either server.
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { safeShot } from '../evidence/safe-shot.mjs'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(scriptDir, '../..')
const outputDir = resolve(repoRoot, 'visual-review-20261002')
const viewports = [
  { width: 1280, height: 800 },
  { width: 800, height: 600 },
]

function requireWindowsNative() {
  if (process.platform !== 'win32') {
    throw new Error('P5 visual capture is Windows-native only; run it from PowerShell, not WSL.')
  }
}

function required(name) {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`Missing ${name}; set it in the Windows process environment.`)
  return value
}

function loopbackUrl(name, raw, { allowOldApi = false } = {}) {
  let value
  try {
    value = new URL(raw)
  } catch {
    throw new Error(`${name} must be an HTTP URL on loopback.`)
  }
  if (value.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(value.hostname)) {
    throw new Error(`${name} must use http://localhost, 127.0.0.1, or [::1].`)
  }
  if (value.username || value.password || value.search || value.hash || (value.pathname !== '/' && value.pathname !== '')) {
    throw new Error(`${name} must be a bare loopback origin without credentials, path, query, or fragment.`)
  }
  if (!allowOldApi && value.port === '18137') {
    throw new Error('VISUAL_REVIEW_UI_URL cannot use port 18137; that is the existing old frontend stack.')
  }
  return value.origin
}

function gitText(...args) {
  return execFileSync('git', args, { cwd: repoRoot, stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
}

function assertRepairCheckout(expectedCommit) {
  if (!/^[a-f0-9]{40}$/i.test(expectedCommit)) {
    throw new Error('VISUAL_REVIEW_EXPECTED_COMMIT must be the full 40-character repair commit SHA.')
  }
  if (expectedCommit.toLowerCase().startsWith('6e93a50')) {
    throw new Error('6e93a50 is the known unrepaired baseline and cannot be used as VISUAL_REVIEW_EXPECTED_COMMIT.')
  }

  let checkoutHead
  try {
    checkoutHead = gitText('rev-parse', 'HEAD')
    execFileSync('git', ['merge-base', '--is-ancestor', expectedCommit, 'HEAD'], {
      cwd: repoRoot,
      stdio: 'ignore',
    })
  } catch {
    throw new Error('The repair commit is not present in this Windows checkout; no screenshots were captured.')
  }

  const frontendDiff = spawnSync('git', ['diff', '--quiet', expectedCommit, '--', 'library-frontend'], {
    cwd: repoRoot,
    stdio: 'ignore',
  })
  if (frontendDiff.status !== 0) {
    throw new Error('library-frontend differs from VISUAL_REVIEW_EXPECTED_COMMIT; refresh the repair checkout before capture.')
  }

  return { expectedCommit: expectedCommit.toLowerCase(), checkoutHead }
}

function readCaptureSettings() {
  const expectedCommit = required('VISUAL_REVIEW_EXPECTED_COMMIT')
  const uiUrl = loopbackUrl(
    'VISUAL_REVIEW_UI_URL',
    process.env.VISUAL_REVIEW_UI_URL || 'http://127.0.0.1:5173',
  )
  const apiUrl = loopbackUrl(
    'VISUAL_REVIEW_API_URL',
    process.env.VISUAL_REVIEW_API_URL || 'http://127.0.0.1:18137',
    { allowOldApi: true },
  )
  const titleId = required('VISUAL_REVIEW_TITLE_ID')
  const transactionId = required('VISUAL_REVIEW_TRANSACTION_ID')
  if (!/^[1-9][0-9]*$/.test(titleId) || !/^[1-9][0-9]*$/.test(transactionId)) {
    throw new Error('VISUAL_REVIEW_TITLE_ID and VISUAL_REVIEW_TRANSACTION_ID must be positive decimal fixture IDs.')
  }

  const roles = {
    reader: {
      username: required('VISUAL_REVIEW_READER_USERNAME'),
      password: required('VISUAL_REVIEW_READER_PASSWORD'),
    },
    librarian: {
      username: required('VISUAL_REVIEW_LIBRARIAN_USERNAME'),
      password: required('VISUAL_REVIEW_LIBRARIAN_PASSWORD'),
    },
    admin: {
      username: required('VISUAL_REVIEW_ADMIN_USERNAME'),
      password: required('VISUAL_REVIEW_ADMIN_PASSWORD'),
    },
  }

  return { expectedCommit, uiUrl, apiUrl, titleId, transactionId, roles }
}

const routes = [
  { id: 'home', role: 'anonymous', path: '/', heading: 'MyLibrarySystem' },
  { id: 'login', role: 'anonymous', path: '/login', heading: '登录' },
  { id: 'register', role: 'anonymous', path: '/register', heading: '注册读者账户' },
  { id: 'catalog-search', role: 'anonymous', path: '/catalog', heading: '书目检索' },
  { id: 'title-detail', role: 'anonymous', path: (s) => `/titles/${s.titleId}`, testId: 'title-detail-name' },
  { id: 'assistant', role: 'anonymous', path: '/assistant', heading: 'AI 助手' },
  { id: 'my-business', role: 'reader', path: '/me/business', heading: '我的业务' },
  {
    id: 'business-detail',
    role: 'reader',
    path: (s) => `/me/business/${s.transactionId}`,
    testId: 'business-detail',
    manifestPath: '/me/business/:txId',
  },
  { id: 'notifications', role: 'reader', path: '/notifications', heading: '通知中心' },
  { id: 'fines', role: 'reader', path: '/fines', heading: '罚款与支付' },
  { id: 'librarian-desk', role: 'librarian', path: '/desk', heading: '馆员工作台' },
  { id: 'walk-in', role: 'librarian', path: '/walk-in', heading: '现场借阅' },
  { id: 'arrival-dashboard', role: 'librarian', path: '/admin/dashboard', heading: '到馆看板' },
  { id: 'admin-accounts', role: 'admin', path: '/admin/accounts', heading: '账户管理' },
  { id: 'admin-reviews', role: 'admin', path: '/admin/reviews', heading: '高风险复核' },
  { id: 'admin-closing', role: 'admin', path: '/admin/closing', heading: '闭馆与恢复' },
  { id: 'admin-audit', role: 'admin', path: '/admin/audit', heading: '审计查询' },
]

function displayPath(route, settings) {
  return route.manifestPath || (typeof route.path === 'function' ? route.path(settings) : route.path)
}

async function checkLocalServices(settings) {
  const [uiResponse, apiResponse] = await Promise.all([
    fetch(settings.uiUrl),
    fetch(new URL('/api/health', settings.apiUrl)),
  ])
  if (!uiResponse.ok) throw new Error(`UI preview returned HTTP ${uiResponse.status}.`)
  const html = await uiResponse.text()
  if (!html.includes('/@vite/client')) {
    throw new Error('UI URL is not the repair checkout Vite development preview; screenshots were not captured.')
  }
  if (!apiResponse.ok) throw new Error(`API upstream health returned HTTP ${apiResponse.status}.`)
  const health = await apiResponse.json().catch(() => null)
  if (health?.status !== 'UP') throw new Error('API upstream did not report status UP.')
}

async function installReadOnlyApiRoute(context, apiUrl) {
  const blockedWrites = []
  const allowedBootstrapPosts = new Set(['/auth/sessions', '/ai/sessions'])
  await context.route('**/api/**', async (route) => {
    const request = route.request()
    const requestUrl = new URL(request.url())
    const relativeApiPath = requestUrl.pathname.replace(/^\/api\/v\d+/, '')
    const method = request.method().toUpperCase()
    const allowed = ['GET', 'HEAD', 'OPTIONS'].includes(method)
      || (method === 'POST' && allowedBootstrapPosts.has(relativeApiPath))

    if (!allowed) {
      blockedWrites.push({ method, path: requestUrl.pathname })
      await route.abort('blockedbyclient')
      return
    }

    const upstreamUrl = new URL(requestUrl.pathname + requestUrl.search, apiUrl)
    const response = await route.fetch({ url: upstreamUrl.href })
    await route.fulfill({ response })
  })
  return blockedWrites
}

function safeErrorMessage(error, secrets) {
  let message = error instanceof Error ? error.message : 'unknown capture error'
  for (const secret of secrets) {
    if (secret) message = message.replaceAll(secret, '[redacted]')
  }
  return message.slice(0, 500)
}

async function openRoute(page, route, settings) {
  const path = typeof route.path === 'function' ? route.path(settings) : route.path
  const url = new URL(path, settings.uiUrl).href
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  const expectedPath = new URL(url).pathname
  await page.waitForFunction((pathname) => location.pathname === pathname, expectedPath)

  const ready = route.testId
    ? page.getByTestId(route.testId)
    : page.getByRole('heading', { name: route.heading, exact: true })
  await ready.waitFor({ state: 'visible', timeout: 20_000 })
  await page.waitForLoadState('networkidle', { timeout: 3_000 }).catch(() => {})
  return { path, url }
}

async function login(page, credentials, uiUrl) {
  await page.goto(new URL('/login', uiUrl).href, { waitUntil: 'domcontentloaded' })
  await page.getByTestId('login-username').fill(credentials.username)
  await page.getByTestId('login-password').fill(credentials.password)
  await page.getByTestId('login-submit').click()
  await page.getByTestId('logout-button').waitFor({ state: 'visible', timeout: 20_000 })
}

async function captureRoute(page, route, settings, browser, captures, blockedWrites) {
  for (const viewport of viewports) {
    await page.setViewportSize(viewport)
    const opened = await openRoute(page, route, settings)
    if (blockedWrites.length) {
      throw new Error(`A non-read API request was blocked (${blockedWrites.at(-1).method} ${blockedWrites.at(-1).path}); no more screenshots were written.`)
    }

    const measured = await page.evaluate(() => ({
      width: window.innerWidth,
      height: window.innerHeight,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }))
    const filename = `${route.id}-${route.role}-${viewport.width}x${viewport.height}.png`
    const file = join(outputDir, filename)
    await safeShot(page, file, settings.secrets)
    captures.push({
      page: route.id,
      role: route.role,
      path: displayPath(route, settings),
      url: opened.url,
      viewport: { ...viewport },
      actualSize: measured,
      browser: { name: 'Chromium', version: browser.version() },
      file: filename,
    })
  }
}

async function run() {
  requireWindowsNative()
  const settings = readCaptureSettings()
  const source = assertRepairCheckout(settings.expectedCommit)
  settings.secrets = Object.values(settings.roles).flatMap(({ username, password }) => [username, password])
  await checkLocalServices(settings)
  await mkdir(outputDir, { recursive: true })

  const { chromium } = await import('@playwright/test')
  const browser = await chromium.launch({ headless: false })
  const captures = []
  const startedAt = new Date().toISOString()
  const manifest = {
    purpose: 'Local visual review only',
    startedAt,
    source: {
      expectedRepairCommit: source.expectedCommit,
      windowsCheckoutHead: source.checkoutHead,
      frontendTreeMatchesRepairCommit: true,
      uiPreviewUrl: settings.uiUrl,
      apiUpstreamUrl: settings.apiUrl,
      apiIdentity: 'Existing loopback API; not upgraded by this capture script.',
    },
    viewports,
    captures,
    blockedWriteRequests: [],
  }

  try {
    const roleGroups = ['anonymous', 'reader', 'librarian', 'admin']
    for (const role of roleGroups) {
      const context = await browser.newContext({ viewport: viewports[0] })
      const blockedWrites = await installReadOnlyApiRoute(context, settings.apiUrl)
      const page = await context.newPage()
      if (role !== 'anonymous') await login(page, settings.roles[role], settings.uiUrl)

      for (const route of routes.filter((entry) => entry.role === role)) {
        await captureRoute(page, route, settings, browser, captures, blockedWrites)
      }
      manifest.blockedWriteRequests.push(...blockedWrites)
      await context.close()
    }
    manifest.finishedAt = new Date().toISOString()
    manifest.status = 'complete'
  } catch (error) {
    manifest.finishedAt = new Date().toISOString()
    manifest.status = 'failed'
    manifest.error = safeErrorMessage(error, settings.secrets)
    throw error
  } finally {
    await browser.close()
    await writeFile(join(outputDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8')
  }

  console.log(`Captured ${captures.length} viewport images to ${outputDir}.`)
}

run().catch((error) => {
  const secrets = [
    process.env.VISUAL_REVIEW_READER_PASSWORD,
    process.env.VISUAL_REVIEW_LIBRARIAN_PASSWORD,
    process.env.VISUAL_REVIEW_ADMIN_PASSWORD,
  ].filter(Boolean)
  console.error(`P5 visual capture failed: ${safeErrorMessage(error, secrets)}`)
  process.exitCode = 1
})
