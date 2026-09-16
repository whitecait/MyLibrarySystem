// 运行时操作清单助手：数据全部来自 generated/operations.json（由 OpenAPI 单命令生成），
// 这里只提供查找、路径模板替换与场景请求匹配，不得出现任何手写 API 路径。
import manifest from '../contracts/generated/operations.json'

export const BASE_PATH = manifest.basePath

const byOperationId = new Map(manifest.operations.map((operation) => [operation.operationId, operation]))

export function getOperation(operationId) {
  return byOperationId.get(operationId) ?? null
}

export function listOperations() {
  return [...manifest.operations]
}

function templateToRegExp(pathTemplate) {
  const pattern = pathTemplate.replaceAll(/[{}][^{}]*[{}]/g, () => '[^/]+')
  return new RegExp(`^${pattern}$`)
}

const templateMatchers = manifest.operations.map((operation) => ({
  operation,
  matcher: templateToRegExp(operation.path),
}))

// 把场景样例中的完整请求路径（含 basePath 与查询串）归一为契约路径模板匹配。
export function matchOperation(method, fullPath) {
  let pathname = fullPath
  const queryIndex = pathname.indexOf('?')
  if (queryIndex >= 0) pathname = pathname.slice(0, queryIndex)
  if (pathname.startsWith(BASE_PATH)) pathname = pathname.slice(BASE_PATH.length) || '/'
  const target = method.toUpperCase()
  const hit = templateMatchers.find(({ operation, matcher }) => operation.method === target && matcher.test(pathname))
  return hit?.operation ?? null
}

export function buildPath(operationId, params = {}) {
  const operation = getOperation(operationId)
  if (!operation) throw new Error(`unknown operationId "${operationId}"`)
  const missing = []
  const path = operation.path.replaceAll(/{([^{}]+)}/g, (_, name) => {
    const value = params[name]
    if (value === undefined || value === null || value === '') missing.push(name)
    return encodeURIComponent(String(value))
  })
  if (missing.length > 0) throw new Error(`operation "${operationId}" missing path params: ${missing.join(', ')}`)
  return path
}

export function buildUrl(operationId, { params, query } = {}) {
  const path = buildPath(operationId, params)
  if (!query) return `${BASE_PATH}${path}`
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const queryString = search.toString()
  return queryString ? `${BASE_PATH}${path}?${queryString}` : `${BASE_PATH}${path}`
}
