// Drivability check for the registered browser matrix (AT-0017 precondition).
// Every entry that carries a version is launched headless and must render a
// canary page. Entries without a version are reported as pending so the
// matrix cannot silently pass while browsers are unregistered.
//
// Usage:
//   node e2e/browsers/verify-matrix.mjs [--registry e2e/browsers/registry.json]
// Exit codes: 0 all six drivable, 1 any registered entry failed,
//             2 some entries not yet registered (report names them).
// Result file: artifacts/m6-acceptance/browser-matrix/verify-<timestamp>.json
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium, firefox } from '@playwright/test'

const here = dirname(fileURLToPath(import.meta.url))
const argIndex = process.argv.indexOf('--registry')
const registryPath = resolve(argIndex > -1 ? process.argv[argIndex + 1] : resolve(here, 'registry.json'))
const registry = JSON.parse(readFileSync(registryPath, 'utf8'))
const results = []

for (const entry of registry.browsers) {
  if (!entry.version) {
    results.push({ id: entry.id, drivable: null, note: 'pending registration (version not recorded)' })
    continue
  }
  const launcher = entry.family === 'firefox' ? firefox : chromium
  const launchOpts = { headless: true }
  if (entry.executablePath) launchOpts.executablePath = entry.executablePath
  else if (entry.playwrightChannel) launchOpts.channel = entry.playwrightChannel
  else {
    results.push({ id: entry.id, drivable: false, error: 'neither executablePath nor playwrightChannel registered' })
    continue
  }
  try {
    const browser = await launcher.launch(launchOpts)
    const page = await browser.newPage()
    await page.setContent('<!doctype html><title>canary</title><h1>drivability canary</h1>')
    const title = await page.title()
    const heading = await page.textContent('h1')
    await browser.close()
    results.push({
      id: entry.id,
      version: entry.version,
      drivable: title === 'canary' && heading === 'drivability canary',
      verifiedAt: new Date().toISOString(),
    })
  } catch (err) {
    results.push({ id: entry.id, version: entry.version, drivable: false, error: String(err).slice(0, 500) })
  }
}

const stamp = new Date().toISOString().replace(/[:.]/g, '-')
const outDir = resolve(here, '../../artifacts/m6-acceptance/browser-matrix')
mkdirSync(outDir, { recursive: true })
const outFile = resolve(outDir, `verify-${stamp}.json`)
writeFileSync(outFile, JSON.stringify({ registryPath, results }, null, 2))

for (const r of results) {
  const state = r.drivable === null ? 'PENDING' : r.drivable ? 'PASS' : 'FAIL'
  console.log(`${state.padEnd(7)} ${r.id}${r.error ? ' — ' + r.error : ''}${r.note ? ' — ' + r.note : ''}`)
}
console.log(`result file: ${outFile}`)

const failed = results.some((r) => r.drivable === false)
const pending = results.some((r) => r.drivable === null)
process.exit(failed ? 1 : pending ? 2 : 0)
