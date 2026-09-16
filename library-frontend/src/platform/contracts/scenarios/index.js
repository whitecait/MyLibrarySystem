// 共同样例装载器：数据是 docs 契约样例的逐字节同步副本（哈希校验见 scripts/check-scenarios-sync.mjs），
// 这里只提供只读访问，不解释业务含义。
import scenariosFile from './contract-scenarios.json'

export const CONTRACT_SAMPLE_SCHEMA_VERSION = scenariosFile.schemaVersion

export function listScenarios() {
  return [...scenariosFile.scenarios]
}

export function listScenarioIds() {
  return scenariosFile.scenarios.map((scenario) => scenario.id)
}

export function getScenario(id) {
  const scenario = scenariosFile.scenarios.find((item) => item.id === id)
  if (!scenario) throw new Error(`unknown contract scenario "${id}"`)
  return scenario
}
