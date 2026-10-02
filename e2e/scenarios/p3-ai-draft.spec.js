import { test, expect } from '@playwright/test'
import {
  P3_VIEWPORTS,
  guardP3SuiteImport,
  login,
  logout,
  p3SafeShot,
  p3Value,
  readAiDraftEvent,
  readAiSessionEvidence,
  readCopyState,
  recordViewport,
  setAndCheckViewport,
  sendRealAiMessage,
  waitForApiResponse,
  walkInDraftFrom,
} from './p3-support.mjs'

guardP3SuiteImport('ai')

test('P3-14: reader is denied the walk-in draft; librarian applies all fields and explicitly lends once', async ({ page }) => {
  test.setTimeout(420_000)
  const fixture = {
    readerId: p3Value('P3_AI_READER_ID'),
    titleId: p3Value('P3_AI_TITLE_ID'),
    copyId: p3Value('P3_AI_COPY_ID'),
  }
  const readerUsername = p3Value('P3_READER_USERNAME')
  const readerPassword = p3Value('P3_READER_PASSWORD')
  const librarianUsername = p3Value('P3_LIBRARIAN_USERNAME')
  const librarianPassword = p3Value('P3_LIBRARIAN_PASSWORD')
  const walkInRequests = []

  page.on('request', (request) => {
    const url = new URL(request.url())
    if (request.method() === 'POST' && url.pathname === '/api/v1/walk-in-loans') {
      walkInRequests.push(request)
    }
  })

  await login(page, readerUsername, readerPassword)
  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('READER')
  const readerResult = await sendRealAiMessage(page, promptFor(fixture), { allowRoleDenied: true })
  if (readerResult.status === 200) {
    const readerWalkInActions = readerResult.data.actions.filter((action) => action.actionType === 'WALK_IN_LOAN')
    expect(readerWalkInActions, 'READER must not receive a WALK_IN_LOAN action').toHaveLength(0)
  } else {
    expect(readerResult.envelope.code, 'a model attempt to use the forbidden role tool has a stable denial').toBe('MLS-AI-004')
  }
  const readerSessionId = (await page.getByTestId('ai-session-id').innerText()).trim()
  const readerEvidence = await readAiSessionEvidence(page, readerSessionId)
  expect(readerEvidence.httpStatus, 'READER AI event readback').toBe(200)
  expect(readerEvidence.modelRequestSeen, 'reader denial must follow a real model request').toBe(true)
  expect(readerEvidence.walkInDraftSeen, 'READER must not receive a persisted walk-in draft').toBe(false)
  expect(walkInRequests, 'reader AI attempt must not submit a loan').toHaveLength(0)
  await expect(page.getByTestId('ai-events-toggle')).toBeVisible()
  await page.getByTestId('ai-events-toggle').click()
  await expect(page.getByTestId('ai-events-table')).toContainText('MODEL_REQUEST')
  await page.goto('/walk-in')
  await expect(page.getByRole('heading', { name: '403', exact: true })).toBeVisible()
  await logout(page)

  await login(page, librarianUsername, librarianPassword)
  await page.goto('/assistant')
  await expect(page.getByTestId('ai-role')).toHaveText('LIBRARIAN')

  const initialCopy = await readCopyState(page, fixture.titleId, fixture.copyId)
  expect(initialCopy.httpStatus, 'librarian fixture readback').toBe(200)
  expect(initialCopy.copy?.titleId, 'fixture copy must belong to the selected title').toBe(fixture.titleId)
  expect(initialCopy.copy?.status, 'fixture copy must be borrowable before AI application').toBe('AVAILABLE')

  let draft
  let modelAttempts = 0
  const prompts = [
    promptFor(fixture),
    '我是馆员。请只生成现场借阅草稿：读者 ID ' + fixture.readerId
      + '，书目 ID ' + fixture.titleId + '，副本 ID ' + fixture.copyId + '。请勿提交。',
  ]
  for (const prompt of prompts) {
    modelAttempts += 1
    const result = await sendRealAiMessage(page, prompt)
    draft = walkInDraftFrom(result)
    if (draft) break
  }
  test.info().annotations.push({ type: 'P3-14-attempts', description: String(modelAttempts) })
  if (!draft) {
    throw new Error('real model produced no WALK_IN_LOAN L2 draft after two attempts.')
  }

  expect(String(draft.prefill?.readerId ?? ''), 'AI draft must preserve readerId').toBe(fixture.readerId)
  expect(String(draft.prefill?.titleId ?? ''), 'AI draft must preserve titleId').toBe(fixture.titleId)
  expect(String(draft.prefill?.copyId ?? ''), 'AI draft must preserve copyId').toBe(fixture.copyId)
  expect(String(draft.draftId ?? ''), 'AI draft must carry its draft ID').not.toBe('')
  const librarianSessionId = (await page.getByTestId('ai-session-id').innerText()).trim()
  const savedDraft = await readAiDraftEvent(page, librarianSessionId, draft.draftId)
  expect(savedDraft.httpStatus, 'owned AI event readback').toBe(200)
  expect(savedDraft.modelRequestSeen, 'event stream must record a real model request').toBe(true)
  expect(savedDraft.event?.eventId).toBe(String(draft.draftId))
  expect(savedDraft.event?.actionType).toBe('prefill_walk_in_loan')
  expect(savedDraft.event?.level).toBe('L2')
  expect(String(savedDraft.event?.prefill?.readerId ?? '')).toBe(fixture.readerId)
  expect(String(savedDraft.event?.prefill?.titleId ?? '')).toBe(fixture.titleId)
  expect(String(savedDraft.event?.prefill?.copyId ?? '')).toBe(fixture.copyId)
  await page.getByTestId('ai-events-toggle').click()
  await expect(page.getByTestId('ai-events-table')).toContainText('MODEL_REQUEST')

  const openDraft = page.getByTestId('ai-action-open-' + String(draft.draftId))
  await expect(openDraft).toBeVisible()
  await openDraft.click()
  await expect(page).toHaveURL((url) => url.pathname === '/walk-in')
  await expect(page.getByRole('heading', { name: '现场借阅', exact: true })).toBeVisible()
  await expect(page.getByTestId('walkin-reader-id')).toHaveValue(fixture.readerId)
  await expect(page.getByTestId('walkin-title-0')).toHaveValue(fixture.titleId)
  await expect(page.getByTestId('walkin-copy-0')).toHaveValue(fixture.copyId)
  expect(new URL(page.url()).searchParams.get('aiDraftId')).toBe(String(draft.draftId))

  for (const size of P3_VIEWPORTS) {
    recordViewport(test.info(), await setAndCheckViewport(page, size))
    await expect(page.getByTestId('walkin-reader-id')).toHaveValue(fixture.readerId)
    await expect(page.getByTestId('walkin-title-0')).toHaveValue(fixture.titleId)
    await expect(page.getByTestId('walkin-copy-0')).toHaveValue(fixture.copyId)
    await p3SafeShot(page, 'p3-ai-walk-in-applied-' + size.width + 'x' + size.height + '.png')
  }

  expect(walkInRequests, 'applying an AI draft must not submit a loan').toHaveLength(0)
  const afterApplyCopy = await readCopyState(page, fixture.titleId, fixture.copyId)
  expect(afterApplyCopy.httpStatus, 'post-application fixture readback').toBe(200)
  expect(afterApplyCopy.copy?.status, 'applying a draft must leave the copy on shelf').toBe('AVAILABLE')

  if (process.env.P3_AI_EXPLICIT_SUBMIT !== '1') {
    throw new Error('P3_AI_EXPLICIT_SUBMIT=1 is required for the one allowed loan submission.')
  }
  const submitResponsePromise = waitForApiResponse(page, 'POST', '/api/v1/walk-in-loans')
  await page.getByTestId('walkin-submit').click()
  const submitResponse = await submitResponsePromise
  expect(submitResponse.status(), 'explicit walk-in submission').toBe(200)
  expect(walkInRequests, 'one explicit confirmation must make exactly one loan request').toHaveLength(1)

  const submittedRequest = walkInRequests[0]
  const requestBody = submittedRequest.postDataJSON()
  expect(String(requestBody.readerId ?? '')).toBe(fixture.readerId)
  expect(requestBody.channel).toBe('DESK')
  expect(requestBody.items).toHaveLength(1)
  expect(String(requestBody.items[0].titleId ?? '')).toBe(fixture.titleId)
  expect(String(requestBody.items[0].copyId ?? '')).toBe(fixture.copyId)
  expect(submittedRequest.headers()['ai-draft-id']).toBe(String(draft.draftId))

  const result = await submitResponse.json()
  expect(result.data?.items).toHaveLength(1)
  expect(result.data.items[0]?.outcome).toBe('LENT')
  await expect(page.getByTestId('walkin-results')).toBeVisible()
  const rows = page.getByTestId('walkin-results').locator('tbody tr')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('LENT')

  const finalCopy = await readCopyState(page, fixture.titleId, fixture.copyId)
  expect(finalCopy.httpStatus, 'post-submission fixture readback').toBe(200)
  expect(finalCopy.copy?.status, 'explicit submission must update the selected copy').toBe('BORROWED')
  expect(walkInRequests, 'post-submit readback must still show one submission').toHaveLength(1)
  test.info().annotations.push(
    { type: 'P3-14-real-model', description: 'READER denial and LIBRARIAN L2 draft were returned by the real AI endpoint' },
    { type: 'P3-14-prefill', description: 'readerId, titleId, and copyId retained at both required viewports' },
    { type: 'P3-14-confirmation', description: 'zero loan requests before confirmation; one POST and one LENT row after confirmation' },
    { type: 'fixture-state', description: 'copies API returned AVAILABLE before and after apply, then BORROWED after submission' },
  )
})


function promptFor(fixture) {
  return '请仅生成现场借阅草稿，不要提交：读者 ID ' + fixture.readerId
    + '，书目 ID ' + fixture.titleId + '，副本 ID ' + fixture.copyId + '。'
}
