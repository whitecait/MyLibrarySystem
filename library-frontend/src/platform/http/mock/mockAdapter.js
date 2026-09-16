// Mock Adapter：只回放共同样例（contract-scenarios.json）或显式登记的夹具，
// 不实现任何权威业务规则；未登记的操作立即失败，避免用 mock 伪造业务结果。
// 场景回放按契约路径模板匹配，且无论请求负载如何都原样返回样例（mock 不判定规则）。
import { getOperation, matchOperation } from '../operations.js'
import { failureFromHttpStatus } from '../errors.js'
import { isSuccessCode, parseEnvelope } from '../envelope.js'
import { getScenario } from '../../contracts/scenarios/index.js'

function replay({ httpStatus, body }) {
  if (httpStatus >= 400) throw failureFromHttpStatus(httpStatus, body)
  const envelope = parseEnvelope(body)
  if (!isSuccessCode(envelope.code)) {
    throw new Error(`mock sample for success status carries non-success code "${envelope.code}"`)
  }
  return envelope
}

export function createMockTransport({ scenarioId = null, fixtures = {} } = {}) {
  async function request(operationId) {
    const operation = getOperation(operationId)
    if (!operation) throw new Error(`unknown operationId "${operationId}"`)
    if (scenarioId) {
      const scenario = getScenario(scenarioId)
      const matched = matchOperation(scenario.request.method, scenario.request.path)
      if (matched?.operationId === operationId) return replay(scenario.response)
    }
    const fixture = fixtures[operationId]
    if (!fixture) {
      throw new Error(
        `MOCK_MISSING_FIXTURE: no contract scenario or fixture registered for operation "${operationId}"`,
      )
    }
    return replay({
      httpStatus: fixture.status ?? 200,
      body: { code: fixture.code, message: fixture.message ?? '', data: fixture.data ?? {} },
    })
  }
  return {
    mode: 'mock',
    request,
  }
}
