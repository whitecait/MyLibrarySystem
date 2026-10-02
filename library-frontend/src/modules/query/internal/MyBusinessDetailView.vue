<template>
  <section class="business-detail-view">
    <section v-if="detail" data-testid="business-detail">
      <h1>业务详情</h1>
      <dl class="business-detail-fields">
        <div>
          <dt>业务类型</dt>
          <dd data-testid="business-detail-kind">{{ detail.kind === 'RESERVATION' ? '预约事务' : '借阅事务' }}</dd>
        </div>
        <div>
          <dt>编号</dt>
          <dd data-testid="business-detail-id">{{ detail.id }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd data-testid="business-detail-status">{{ detail.status }}</dd>
        </div>
        <div v-if="detail.title">
          <dt>书名</dt>
          <dd data-testid="business-detail-title">{{ detail.title }}</dd>
        </div>
        <div v-if="detail.businessDate">
          <dt>业务日期</dt>
          <dd data-testid="business-detail-date">{{ detail.businessDate }}</dd>
        </div>
      </dl>
    </section>

    <template v-else>
      <h1>业务详情</h1>
      <p v-if="state.status === 'loading'" data-testid="business-detail-loading">加载中…</p>
      <el-empty
        v-else-if="state.status === 'not-found'"
        data-testid="business-detail-not-found"
        description="未找到可查看的业务记录"
      />
      <ErrorAlert v-else-if="state.status === 'failure'" :failure="state.failure" />
    </template>
  </section>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { FailureKind, RequestFailure } from '@/platform/http'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { getSessionState, isAuthenticated } from '@/platform/auth/session'
import { getMyBusiness } from '../index'

const props = defineProps({ txId: { type: String, required: true } })
const BORROW_KINDS = ['RESERVATION', 'LOAN']
const PAGE_SIZE = 20
const detail = ref(null)
const state = reactive({ status: 'loading', failure: null })
const session = getSessionState()
let generation = 0

function isCurrentRequest(requestGeneration, txId, identity) {
  return requestGeneration === generation && props.txId === txId
    && session.token === identity.token
    && session.actor?.accountId === identity.accountId
    && session.actor?.role === 'READER'
    && isAuthenticated()
}

function systemFailure(error) {
  if (error instanceof RequestFailure) return error
  return new RequestFailure({
    kind: FailureKind.SYSTEM,
    message: error?.message ?? '请求失败',
    cause: error,
  })
}

function malformedResponse() {
  return new RequestFailure({ kind: FailureKind.MALFORMED, message: '业务查询响应异常' })
}

async function findBorrowTransaction(txId, requestGeneration, identity) {
  for (const kind of BORROW_KINDS) {
    const seenCursors = new Set()
    let cursor

    while (true) {
      if (cursor !== undefined) {
        if (seenCursors.has(cursor)) throw malformedResponse()
        seenCursors.add(cursor)
      }

      const envelope = await getMyBusiness({ kind, cursor, limit: PAGE_SIZE })
      if (!isCurrentRequest(requestGeneration, txId, identity)) return null

      const page = envelope?.data
      if (!Array.isArray(page?.items) || !Object.hasOwn(page, 'nextCursor')) throw malformedResponse()

      const transaction = page.items.find((item) =>
        item?.kind === kind && typeof item.id === 'string' && item.id === txId,
      )
      if (transaction) return transaction

      const nextCursor = page.nextCursor
      if (nextCursor == null) break
      if (typeof nextCursor !== 'string' || nextCursor.length === 0 || seenCursors.has(nextCursor)) {
        throw malformedResponse()
      }
      cursor = nextCursor
    }
  }

  return null
}

watch(
  [
    () => props.txId,
    () => session.token,
    () => session.actor?.accountId,
    () => session.actor?.role,
    () => session.expiresAt,
  ],
  async ([rawTxId, token, accountId, role]) => {
    const requestGeneration = ++generation
    detail.value = null
    state.failure = null

    if (typeof rawTxId !== 'string' || !/^\d+$/.test(rawTxId)
      || !token || !accountId || role !== 'READER' || !isAuthenticated()) {
      state.status = 'not-found'
      return
    }

    state.status = 'loading'
    const identity = { token, accountId }
    try {
      const transaction = await findBorrowTransaction(rawTxId, requestGeneration, identity)
      if (!isCurrentRequest(requestGeneration, rawTxId, identity)) return
      detail.value = transaction
      state.status = transaction ? 'success' : 'not-found'
    } catch (error) {
      if (!isCurrentRequest(requestGeneration, rawTxId, identity)) return
      state.failure = systemFailure(error)
      state.status = 'failure'
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  generation += 1
})
</script>

<style scoped>
.business-detail-view {
  max-width: 60rem;
}

.business-detail-fields {
  display: grid;
  gap: 0.75rem;
  margin: 1rem 0 0;
}

.business-detail-fields > div {
  display: grid;
  grid-template-columns: 8rem minmax(0, 1fr);
  gap: 0.75rem;
}

.business-detail-fields dt {
  color: #586b78;
}

.business-detail-fields dd {
  margin: 0;
}
</style>
