import { existsSync, mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect } from '@playwright/test'
import { loadSecretValues, safeShot } from '../evidence/safe-shot.mjs'
import {
  isListInvocation,
  loadP3Environment,
  validateP3Environment,
} from '../scripts/p3-runtime.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const e2eRoot = resolve(here, '..')
const repoRoot = resolve(e2eRoot, '..')

export const P3_VIEWPORTS = [
  { width: 1280, height: 800 },
  { width: 800, height: 600 },
]

export function guardP3SuiteImport(suite) {
  if (isListInvocation(process.argv)) return
  if (process.platform !== 'win32') {
    throw new Error('P3 browser regression is Windows-native only; do not run it from WSL.')
  }
  if (process.env.P3_RUNNER_CONFIG !== '1') {
    throw new Error('P3 regression requires playwright.p3.config.js or an npm p3:* runner command.')
  }
  loadP3Environment()
  validateP3Environment(suite)
}

export function p3Value(name) {
  const value = process.env[name]
  if (!value?.trim()) {
    throw new Error('P3 runtime setting is missing: ' + name + '. See e2e/.env.p3.example.')
  }
  return value
}

export async function login(page, username, password) {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
  await page.getByTestId('login-username').fill(username)
  await page.getByTestId('login-password').fill(password)
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('logout-button')).toBeVisible()
}

export async function logout(page) {
  await page.getByTestId('logout-button').click()
  await expect(page.getByTestId('login-link')).toBeVisible()
}

export async function setAndCheckViewport(page, size) {
  await page.setViewportSize(size)
  await expect.poll(() => page.evaluate(() => ({ width: innerWidth, height: innerHeight }))).toEqual(size)
  const widths = await page.evaluate(() => ({
    width: innerWidth,
    height: innerHeight,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }))
  expect(widths.document, 'document should not overflow horizontally').toBeLessThanOrEqual(size.width)
  expect(widths.body, 'body should not overflow horizontally').toBeLessThanOrEqual(size.width)
  return {
    ...widths,
    browserVersion: page.context().browser()?.version() ?? 'unavailable',
  }
}

export function recordViewport(testInfo, measured) {
  testInfo.annotations.push(
    { type: 'browser-version', description: measured.browserVersion },
    {
      type: 'actual-viewport',
      description: measured.width + 'x' + measured.height,
    },
  )
}

export function waitForApiResponse(page, method, pathname, timeout = 30_000) {
  return page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === method && url.pathname === pathname
  }, { timeout })
}

export function waitForAiMessage(page) {
  return page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === 'POST'
      && url.pathname.startsWith('/api/v1/ai/sessions/')
      && url.pathname.endsWith('/messages')
  }, { timeout: 180_000 })
}

export async function readCopyState(page, titleId, copyId) {
  return page.evaluate(async ({ targetTitleId, targetCopyId }) => {
    const session = JSON.parse(sessionStorage.getItem('mls.session.v1') || 'null')
    const token = session?.token
    if (!token) return { httpStatus: 401, copy: null }

    let cursor = null
    const seenCursors = new Set()
    let status = 0
    let copy = null
    for (let pageNumber = 0; pageNumber < 50; pageNumber += 1) {
      const query = new URLSearchParams({ titleId: targetTitleId, limit: '100' })
      if (cursor) query.set('cursor', cursor)
      const response = await fetch('/api/v1/copies?' + query.toString(), {
        headers: { Authorization: 'Bearer ' + token },
      })
      status = response.status
      let envelope = null
      try {
        envelope = await response.json()
      } catch {
        envelope = null
      }
      if (!response.ok || !Array.isArray(envelope?.data?.items)) break
      copy = envelope.data.items.find((item) => String(item.copyId) === String(targetCopyId)) ?? null
      if (copy) break
      const nextCursor = envelope.data.nextCursor
      if (!nextCursor || seenCursors.has(nextCursor)) break
      seenCursors.add(nextCursor)
      cursor = nextCursor
    }
    return {
      httpStatus: status,
      copy: copy
        ? { copyId: String(copy.copyId), titleId: String(copy.titleId), status: copy.status }
        : null,
    }
  }, { targetTitleId: titleId, targetCopyId: copyId })
}

export async function readAiDraftEvent(page, sessionId, draftId) {
  return page.evaluate(async ({ targetSessionId, targetDraftId }) => {
    const session = JSON.parse(sessionStorage.getItem('mls.session.v1') || 'null')
    const token = session?.token
    if (!token) return { httpStatus: 401, event: null, modelRequestSeen: false }

    let cursor = null
    const seenCursors = new Set()
    let status = 0
    let event = null
    let modelRequestSeen = false
    for (let pageNumber = 0; pageNumber < 50; pageNumber += 1) {
      const query = new URLSearchParams({ limit: '100' })
      if (cursor) query.set('cursor', cursor)
      const response = await fetch(
        '/api/v1/ai/sessions/' + encodeURIComponent(targetSessionId) + '/events?' + query.toString(),
        { headers: { Authorization: 'Bearer ' + token } },
      )
      status = response.status
      let envelope = null
      try {
        envelope = await response.json()
      } catch {
        envelope = null
      }
      if (!response.ok || !Array.isArray(envelope?.data?.items)) break
      for (const item of envelope.data.items) {
        if (item.type === 'MODEL_REQUEST') modelRequestSeen = true
        if (item.type !== 'ACTION_DRAFT' || String(item.eventId) !== String(targetDraftId)) continue
        event = {
          eventId: String(item.eventId),
          actionType: item.payload?.actionType,
          level: item.payload?.level,
          prefill: {
            readerId: item.payload?.prefill?.readerId,
            titleId: item.payload?.prefill?.titleId,
            copyId: item.payload?.prefill?.copyId,
          },
        }
      }
      if (event) break
      const nextCursor = envelope.data.nextCursor
      if (!nextCursor || seenCursors.has(nextCursor)) break
      seenCursors.add(nextCursor)
      cursor = nextCursor
    }
    return { httpStatus: status, event, modelRequestSeen }
  }, { targetSessionId: sessionId, targetDraftId: draftId })
}

export async function readAiSessionEvidence(page, sessionId) {
  return page.evaluate(async ({ targetSessionId }) => {
    const session = JSON.parse(sessionStorage.getItem('mls.session.v1') || 'null')
    const token = session?.token
    if (!token) return { httpStatus: 401, modelRequestSeen: false, walkInDraftSeen: false }

    let cursor = null
    const seenCursors = new Set()
    let status = 0
    let modelRequestSeen = false
    let walkInDraftSeen = false
    for (let pageNumber = 0; pageNumber < 50; pageNumber += 1) {
      const query = new URLSearchParams({ limit: '100' })
      if (cursor) query.set('cursor', cursor)
      const response = await fetch(
        '/api/v1/ai/sessions/' + encodeURIComponent(targetSessionId) + '/events?' + query.toString(),
        { headers: { Authorization: 'Bearer ' + token } },
      )
      status = response.status
      let envelope = null
      try {
        envelope = await response.json()
      } catch {
        envelope = null
      }
      if (!response.ok || !Array.isArray(envelope?.data?.items)) break
      for (const item of envelope.data.items) {
        if (item.type === 'MODEL_REQUEST') modelRequestSeen = true
        if (item.type !== 'ACTION_DRAFT') continue
        const actionType = item.payload?.actionType
        if (actionType === 'prefill_walk_in_loan' || actionType === 'WALK_IN_LOAN') {
          walkInDraftSeen = true
        }
      }
      const nextCursor = envelope.data.nextCursor
      if (!nextCursor || seenCursors.has(nextCursor)) break
      seenCursors.add(nextCursor)
      cursor = nextCursor
    }
    return { httpStatus: status, modelRequestSeen, walkInDraftSeen }
  }, { targetSessionId: sessionId })
}

let screenshotSecrets
function getScreenshotSecrets() {
  if (screenshotSecrets) return screenshotSecrets
  const files = [join(repoRoot, '.env'), join(repoRoot, '.env.acceptance')].filter(existsSync)
  screenshotSecrets = [
    ...loadSecretValues(files),
    process.env.P3_LIBRARIAN_PASSWORD,
    process.env.P3_READER_PASSWORD,
  ].filter((value) => typeof value === 'string' && value.length > 0)
  return screenshotSecrets
}

export async function p3SafeShot(page, filename) {
  const outputDir = resolve(
    process.env.P3_EVIDENCE_DIR || resolve(e2eRoot, '../artifacts/m6-acceptance/p3-blockers'),
  )
  mkdirSync(outputDir, { recursive: true })
  return safeShot(page, join(outputDir, filename), getScreenshotSecrets())
}

export function walkInDraftFrom(result) {
  return (result.data.actions ?? []).find((action) =>
    action.actionType === 'WALK_IN_LOAN' && action.level === 'L2',
  )
}

export async function sendRealAiMessage(page, message, { allowRoleDenied = false } = {}) {
  const responsePromise = waitForAiMessage(page)
  await page.getByTestId('ai-input').fill(message)
  await page.getByTestId('ai-send').click()
  const response = await responsePromise
  const status = response.status()
  const envelope = await response.json()
  if (allowRoleDenied && status === 422) {
    expect(envelope.code, 'forbidden model tool call must be rejected as MLS-AI-004').toBe('MLS-AI-004')
  } else {
    expect(status, 'real AI message request').toBe(200)
  }
  if (status === 200 && !Array.isArray(envelope?.data?.actions)) {
    throw new Error('AI response did not contain an actions list.')
  }
  return { status, envelope, data: envelope.data }
}
