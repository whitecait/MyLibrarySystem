import { test, expect } from '@playwright/test'
import {
  P3_VIEWPORTS,
  guardP3SuiteImport,
  login,
  p3SafeShot,
  p3Value,
  recordViewport,
  setAndCheckViewport,
  waitForApiResponse,
} from './p3-support.mjs'

guardP3SuiteImport('fines')

test('P3 fines: librarian can query with the optional reader filter empty', async ({ page }) => {
  const username = p3Value('P3_LIBRARIAN_USERNAME')
  const password = p3Value('P3_LIBRARIAN_PASSWORD')
  test.setTimeout(90_000)

  await login(page, username, password)
  await setAndCheckViewport(page, P3_VIEWPORTS[0])

  const initialResponsePromise = waitForApiResponse(page, 'GET', '/api/v1/fines')
  await page.goto('/fines')
  await expect(page.getByRole('heading', { name: '罚款与支付', exact: true })).toBeVisible()
  const initialResponse = await initialResponsePromise
  await expectEmptyFilterSuccess(initialResponse)
  await expect(page.getByTestId('fines-table')).toBeVisible()

  for (const size of P3_VIEWPORTS) {
    recordViewport(test.info(), await setAndCheckViewport(page, size))
    const readerFilter = page.getByTestId('fines-reader-id')
    await readerFilter.fill('')
    await expect(readerFilter).toHaveValue('')

    const responsePromise = waitForApiResponse(page, 'GET', '/api/v1/fines')
    await page.getByTestId('fines-submit').click()
    const response = await responsePromise
    await expectEmptyFilterSuccess(response)
    await expect(page.getByTestId('fines-table')).toBeVisible()
    await expect(page.getByTestId('error-alert')).toHaveCount(0)
    await p3SafeShot(page, 'p3-fines-empty-filter-' + size.width + 'x' + size.height + '.png')
  }

  test.info().annotations.push(
    { type: 'P3-12-regression', description: 'empty optional reader filter returned HTTP 200 at both viewports' },
    { type: 'business-side-effects', description: 'read-only GET requests only' },
  )
})

async function expectEmptyFilterSuccess(response) {
  expect(response.status(), 'GET /api/v1/fines with no reader filter').toBe(200)
  const envelope = await response.json()
  expect(Array.isArray(envelope?.data?.items), 'successful fines response must contain an item list').toBe(true)
  const url = new URL(response.url())
  const readerId = url.searchParams.get('readerId')
  expect(readerId === null || readerId === '', 'readerId query must remain empty').toBe(true)
}
