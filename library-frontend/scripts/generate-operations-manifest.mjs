// 从 OpenAPI 契约生成运行时操作清单（generated，禁止手改；重建入口 npm run contracts:generate）。
// 输出 JSON 供 platform/http 解析路径模板、判断幂等键与公开操作，前端不得手写任何 URL。
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import process from 'node:process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(scriptDir, '..')
const contractPath = resolve(frontendRoot, '../docs/04-module-design/contracts/http/openapi.yaml')
const defaultOut = join(frontendRoot, 'src/platform/contracts/generated/operations.json')
const outPath = process.argv[2] ? resolve(process.argv[2]) : defaultOut

const HTTP_METHODS = ['get', 'post', 'patch', 'delete', 'put']
const IDEMPOTENCY_REF = '#/components/parameters/IdempotencyKey'
const IDEMPOTENCY_HEADER = 'Idempotency-Key'

const doc = parse(readFileSync(contractPath, 'utf8'))

if (doc.openapi !== '3.1.0') {
  console.error(`generate-operations-manifest: unsupported OpenAPI version "${doc.openapi}", expected "3.1.0"`)
  process.exit(1)
}

const servers = doc.servers?.map((server) => server.url) ?? []
if (servers.length !== 1 || !servers[0].startsWith('/')) {
  console.error(`generate-operations-manifest: expected exactly one relative server url, got ${JSON.stringify(servers)}`)
  process.exit(1)
}

function hasIdempotencyKey(operation) {
  return (operation.parameters ?? []).some(
    (parameter) => parameter.$ref === IDEMPOTENCY_REF || parameter.name === IDEMPOTENCY_HEADER,
  )
}

const operations = []
const seen = new Set()
for (const [route, pathItem] of Object.entries(doc.paths ?? {})) {
  for (const method of HTTP_METHODS) {
    const operation = pathItem[method]
    if (!operation?.operationId) continue
    if (seen.has(operation.operationId)) {
      console.error(`generate-operations-manifest: duplicate operationId "${operation.operationId}"`)
      process.exit(1)
    }
    seen.add(operation.operationId)
    const security = operation.security ?? pathItem.security ?? doc.security
    operations.push({
      operationId: operation.operationId,
      method: method.toUpperCase(),
      path: route,
      tag: operation.tags?.[0] ?? 'Uncategorized',
      permission: operation['x-permission'] ?? pathItem['x-permission'] ?? null,
      public: Array.isArray(security) && security.length === 0,
      requiresIdempotencyKey: hasIdempotencyKey(operation),
    })
  }
}

operations.sort((a, b) => a.tag.localeCompare(b.tag) || a.operationId.localeCompare(b.operationId))

const manifest = {
  basePath: servers[0],
  openapiVersion: doc.openapi,
  sourceSha256: createHash('sha256').update(readFileSync(contractPath)).digest('hex'),
  operations,
}

mkdirSync(dirname(outPath), { recursive: true })
writeFileSync(outPath, `${JSON.stringify(manifest, null, 2)}\n`)
console.log(`generate-operations-manifest: wrote ${operations.length} operations to ${outPath}`)
