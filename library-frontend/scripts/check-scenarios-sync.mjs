// 共同样例同步校验：src/platform/contracts/scenarios/contract-scenarios.json 必须与
// docs/04-module-design/contracts/examples/contract-scenarios.json 逐字节一致（防第二份手写样例源）。
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import process from 'node:process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(scriptDir, '..')
const sourcePath = resolve(frontendRoot, '../docs/04-module-design/contracts/examples/contract-scenarios.json')
const copyPath = join(frontendRoot, 'src/platform/contracts/scenarios/contract-scenarios.json')

function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex')
}

const sourceHash = sha256(readFileSync(sourcePath))
const copyHash = sha256(readFileSync(copyPath))

if (sourceHash !== copyHash) {
  console.error('scenario sync check failed:')
  console.error(`- canonical ${sourcePath}: ${sourceHash}`)
  console.error(`- synced copy ${copyPath}: ${copyHash}`)
  console.error('- the copy must be a byte-identical sync of the canonical sample; re-copy it, never hand-edit')
  process.exit(1)
}
console.log('scenario sync: OK (contract-scenarios.json copy is byte-identical to the canonical sample)')
