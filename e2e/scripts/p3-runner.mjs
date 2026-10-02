import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'
import {
  createP3RunId,
  e2eRoot,
  loadP3Environment,
  validateP3Environment,
} from './p3-runtime.mjs'

function main() {
  const suiteArg = process.argv.find((arg) => arg.startsWith('--suite='))
  const suite = suiteArg?.slice('--suite='.length) ?? 'all'
  try {
    loadP3Environment()
    validateP3Environment(suite)
  } catch (error) {
    console.error(error.message)
    process.exitCode = 2
    return
  }
  process.env.P3_RUN_ID = createP3RunId()

  const cli = join(e2eRoot, 'node_modules', '@playwright', 'test', 'cli.js')
  if (!existsSync(cli)) {
    console.error('P3 regression BLOCKED: install the locked e2e dependencies in the Windows environment first.')
    process.exitCode = 2
    return
  }

  const args = ['test', '--config=playwright.p3.config.js']
  if (suite === 'fines') args.push('scenarios/p3-fines.spec.js')
  if (suite === 'ai') args.push('scenarios/p3-ai-draft.spec.js')
  const result = spawnSync(process.execPath, [cli, ...args], {
    cwd: e2eRoot,
    env: process.env,
    stdio: 'inherit',
  })
  if (result.error) {
    console.error('P3 regression could not start the Playwright runner.')
    process.exitCode = 1
    return
  }
  process.exitCode = result.status ?? 1
}

if (process.argv[1]?.endsWith('p3-runner.mjs')) main()
