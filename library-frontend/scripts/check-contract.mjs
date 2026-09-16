// 前端静态契约门禁（CONTRACT-001 / CONTRACT-002）。
// CONTRACT-001：OpenAPI 3.1 可解析、内部 $ref 完整、45 个唯一 operationId、幂等键只出现在非 GET 操作。
// CONTRACT-002：生成物可单命令重建且与已提交版本哈希一致；/api/ URL 字面量只允许出现在 platform/http 与 generated。
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync } from 'node:fs'
import process from 'node:process'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(scriptDir, '..')
const contractPath = resolve(frontendRoot, '../docs/04-module-design/contracts/http/openapi.yaml')
const generatedDir = join(frontendRoot, 'src/platform/contracts/generated')
const EXPECTED_OPERATION_COUNT = 45

const failures = []
function fail(message) {
  failures.push(message)
}

function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex')
}

// ---------- CONTRACT-001 ----------
const doc = parse(readFileSync(contractPath, 'utf8'))

if (doc.openapi !== '3.1.0') {
  fail(`CONTRACT-001: openapi version is "${doc.openapi}", expected "3.1.0"`)
}

function resolvePointer(pointer) {
  let node = doc
  for (const rawSegment of pointer.replace(/^#\//, '').split('/')) {
    const segment = decodeURIComponent(rawSegment.replaceAll('~1', '/').replaceAll('~0', '~'))
    if (node == null || typeof node !== 'object' || !(segment in node)) return undefined
    node = node[segment]
  }
  return node
}

function walk(node, visit) {
  if (Array.isArray(node)) {
    for (const item of node) walk(item, visit)
    return
  }
  if (node != null && typeof node === 'object') {
    for (const value of Object.values(node)) {
      visit(value)
      walk(value, visit)
    }
  }
}

const brokenRefs = new Set()
walk(doc, (node) => {
  if (typeof node?.$ref !== 'string' || !node.$ref.startsWith('#/')) return
  if (resolvePointer(node.$ref) === undefined) brokenRefs.add(node.$ref)
})
for (const ref of brokenRefs) fail(`CONTRACT-001: unresolved internal $ref "${ref}"`)

const operationIds = []
walk(doc, (node) => {
  if (typeof node?.operationId === 'string') operationIds.push(node.operationId)
})
const uniqueOperationIds = [...new Set(operationIds)]
if (uniqueOperationIds.length !== EXPECTED_OPERATION_COUNT) {
  fail(`CONTRACT-001: expected ${EXPECTED_OPERATION_COUNT} unique operationIds, found ${uniqueOperationIds.length}`)
}
if (operationIds.length !== uniqueOperationIds.length) {
  const duplicates = operationIds.filter((id, index) => operationIds.indexOf(id) !== index)
  fail(`CONTRACT-001: duplicate operationIds: ${[...new Set(duplicates)].join(', ')}`)
}

// Idempotency-Key 参数必须为必填 header（组件定义处校验，$ref 处随组件生效）。
walk(doc, (node) => {
  if (node?.name !== 'Idempotency-Key' || node?.in !== 'header') return
  if (node.required !== true) fail('CONTRACT-001: Idempotency-Key parameter must be required')
})

// 生成清单与契约的幂等不变量：只有非 GET 操作携带幂等键。
const manifestPath = join(generatedDir, 'operations.json')
if (existsSync(manifestPath)) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
  if (manifest.operations.length !== EXPECTED_OPERATION_COUNT) {
    fail(`CONTRACT-001: generated manifest holds ${manifest.operations.length} operations, expected ${EXPECTED_OPERATION_COUNT}`)
  }
  for (const operation of manifest.operations) {
    if (operation.method === 'GET' && operation.requiresIdempotencyKey) {
      fail(`CONTRACT-001: GET operation "${operation.operationId}" must not require Idempotency-Key`)
    }
    if (operation.method !== 'GET' && !operation.requiresIdempotencyKey) {
      fail(`CONTRACT-001: write operation "${operation.operationId}" must require Idempotency-Key`)
    }
  }
} else {
  fail('CONTRACT-001: missing generated manifest operations.json (run npm run contracts:generate)')
}

// ---------- CONTRACT-002：重建一致性 ----------
if (failures.length === 0) {
  const rebuildDir = join(tmpdir(), `mls-contract-check-${process.pid}-${Date.now()}`)
  mkdirSync(rebuildDir, { recursive: true })
  try {
    const manifestTmp = join(rebuildDir, 'operations.json')
    const typesTmp = join(rebuildDir, 'openapi.d.ts')
    execFileSync(process.execPath, [join(scriptDir, 'generate-operations-manifest.mjs'), manifestTmp], { stdio: 'pipe' })
    const generatorBin = join(frontendRoot, 'node_modules/.bin/openapi-typescript')
    execFileSync(generatorBin, [contractPath, '-o', typesTmp], { cwd: frontendRoot, stdio: 'pipe' })

    for (const [name, tmpPath] of [
      ['operations.json', manifestTmp],
      ['openapi.d.ts', typesTmp],
    ]) {
      const committedPath = join(generatedDir, name)
      if (!existsSync(committedPath)) {
        fail(`CONTRACT-002: missing generated artifact ${name} (run npm run contracts:generate)`)
        continue
      }
      const committedHash = sha256(readFileSync(committedPath))
      const rebuiltHash = sha256(readFileSync(tmpPath))
      if (committedHash !== rebuiltHash) {
        fail(
          `CONTRACT-002: generated artifact ${name} drifted from OpenAPI source ` +
            `(committed ${committedHash.slice(0, 12)}…, rebuilt ${rebuiltHash.slice(0, 12)}…); run npm run contracts:generate`,
        )
      }
    }
  } finally {
    rmSync(rebuildDir, { recursive: true, force: true })
  }
}

// ---------- CONTRACT-002：URL 字面量扫描 ----------
const allowedDirs = [join(frontendRoot, 'src/platform/http'), generatedDir]
const urlLiteralPattern = /['"`]\/api\//
function scanForUrls(dir) {
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry)
    if (statSync(fullPath).isDirectory()) {
      if (basename(fullPath) === 'generated' || basename(fullPath) === 'node_modules') continue
      scanForUrls(fullPath)
      continue
    }
    if (!/\.(js|mjs|vue)$/.test(entry) || entry.endsWith('.test.js')) continue
    if (allowedDirs.some((allowed) => fullPath.startsWith(allowed))) continue
    const lines = readFileSync(fullPath, 'utf8').split('\n')
    lines.forEach((line, index) => {
      if (urlLiteralPattern.test(line)) {
        fail(
          `CONTRACT-002: handwritten API URL literal at ${fullPath.replace(`${frontendRoot}/`, '')}:${index + 1} ` +
            '(page code must call generated operations via platform/http)',
        )
      }
    })
  }
}
scanForUrls(join(frontendRoot, 'src'))

if (failures.length > 0) {
  console.error('contract check failed:')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}
console.log(
  'CONTRACT-001/002: OK (openapi 3.1 parseable, refs resolved, 45 unique operations, generated artifacts fresh, no stray API URLs)',
)
