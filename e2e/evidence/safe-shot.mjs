// Screenshot guard for acceptance evidence (acceptance plan 2026-09-19,
// plan item 2.7). The first control is that raw cookies, Bearer tokens and
// model keys never enter evidence at all; this guard is the second layer and
// refuses to write a screenshot when the page content or URL contains any
// value from the temporary secret set built by
// scripts/collect-evidence-secret-values.sh.
import { readFileSync } from 'node:fs'

// Values only: keys, comments and blank lines are dropped so key NAMES never
// widen the match set (only secret VALUES are scanned for).
export function loadSecretValues(secretFiles) {
  const values = new Set()
  for (const file of secretFiles) {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#')) continue
      const eq = trimmed.indexOf('=')
      const value = eq === -1 ? trimmed : trimmed.slice(eq + 1)
      if (value.length >= 8) values.add(value)
    }
  }
  return [...values]
}

export async function safeShot(page, screenshotPath, secretValues) {
  const haystack = `${page.url()}\n${await page.content()}`
  for (const value of secretValues) {
    if (haystack.includes(value)) {
      throw new Error(`refusing screenshot ${screenshotPath}: page content or URL contains a secret value`)
    }
  }
  await page.screenshot({ path: screenshotPath, fullPage: false })
  return screenshotPath
}
