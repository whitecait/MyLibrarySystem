import {
  inferP3Suite,
  isListInvocation,
  loadP3Environment,
  validateP3Environment,
} from './p3-runtime.mjs'

export default async function p3GlobalSetup() {
  if (isListInvocation(process.argv)) return
  loadP3Environment()
  validateP3Environment(inferP3Suite(process.argv))
}
