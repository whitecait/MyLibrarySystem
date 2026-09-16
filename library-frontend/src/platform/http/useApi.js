// 统一请求结果模型（组合式）：页面经此获得 idle/loading/success/failure 四态，
// 失败时持有 RequestFailure（kind + 稳定错误码），页面据此呈现错误状态。
import { reactive } from 'vue'
import { RequestFailure, FailureKind } from './errors.js'

export function useApiRequest() {
  const state = reactive({ status: 'idle', envelope: null, failure: null })

  async function run(executor) {
    state.status = 'loading'
    state.envelope = null
    state.failure = null
    try {
      state.envelope = await executor()
      state.status = 'success'
      return state.envelope
    } catch (error) {
      state.failure =
        error instanceof RequestFailure
          ? error
          : new RequestFailure({ kind: FailureKind.SYSTEM, message: error?.message ?? 'unexpected error', cause: error })
      state.status = 'failure'
      return null
    }
  }

  function reset() {
    state.status = 'idle'
    state.envelope = null
    state.failure = null
  }

  return { state, run, reset }
}
