<template>
  <section class="review-list-view">
    <h1>高风险复核</h1>
    <form class="filters" data-testid="review-filter" @submit.prevent="load()">
      <select v-model="status" data-testid="review-status" aria-label="复核状态">
        <option value="">全部状态</option>
        <option value="PENDING">待复核</option>
        <option value="APPROVED">已批准</option>
        <option value="REJECTED">已拒绝</option>
      </select>
      <el-button native-type="submit" data-testid="review-submit">查询</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="review-loading">加载中…</p>
    <template v-else-if="state.status === 'success'">
      <table class="review-table" data-testid="review-table">
        <thead>
          <tr><th>受理号</th><th>目标</th><th>动作</th><th>状态</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="review in reviews" :key="review.reviewId">
            <td>{{ review.reviewId }}</td>
            <td>{{ review.targetType }} / {{ review.targetId }}</td>
            <td>{{ review.action }}</td>
            <td>{{ review.status }}</td>
            <td>
              <el-button
                v-if="review.status === 'PENDING'"
                size="small"
                type="primary"
                :data-testid="`review-decide-${review.reviewId}`"
                @click="select(review)"
              >
                作出决定
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <el-button v-if="nextCursor" data-testid="review-load-more" @click="load(nextCursor)">加载更多</el-button>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />

    <ActionPanel title="复核决定" entry-id="review-decision" v-if="selected" :active-key="selectionRequest">
      <h2 data-testid="review-target-line">
        复核 {{ selected.reviewId }}：{{ selected.targetType }} / {{ selected.targetId }}（{{ selected.action }}）
      </h2>
      <p class="note">决定批准前目标动作不会执行；批准时系统将重新校验目标当前状态。</p>
      <form data-testid="decision-form" @submit.prevent="submitDecision">
        <select v-model="decision" data-testid="decision" aria-label="决定">
          <option value="APPROVED">批准</option>
          <option value="REJECTED">拒绝</option>
        </select>
        <el-input v-model="reason" data-testid="decision-reason" placeholder="决定原因（必填）" class="inline-input" />
        <el-button type="primary" native-type="submit" data-testid="decision-submit" :loading="decisionState.status === 'loading'">
          提交决定
        </el-button>
      </form>
      <el-alert
        v-if="decisionResult"
        type="success"
        :closable="false"
        data-testid="decision-success"
        :title="`复核 ${decisionResult.reviewId}：${decisionResult.status}${decisionResult.targetOutcome ? `（目标执行：${decisionResult.targetOutcome}）` : ''}`"
      />
      <template v-if="targetChanged">
        <el-alert
          type="warning"
          :closable="false"
          data-testid="target-changed"
          title="目标状态已变化，本次决定未执行"
          description="可核对最新状态后重新发起复核。"
        />
      </template>
      <ErrorAlert v-if="decisionState.status === 'failure' && !targetChanged" :failure="decisionState.failure" />
    </ActionPanel>
  </section>
</template>

<script setup>
import ActionPanel from '@/components/ActionPanel.vue'
import { computed, ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind } from '@/platform/http'
import { useApiRequest } from '@/platform/http'
import { decideReview, listReviews } from '../index'

const selectionRequest = ref(null)

const reviews = ref([])
const nextCursor = ref(null)
const status = ref('')
const { state, run } = useApiRequest()

const selected = ref(null)
const decision = ref('APPROVED')
const reason = ref('')
const { state: decisionState, run: runDecision } = useApiRequest()
const decisionResult = ref(null)
const targetChanged = computed(
  () =>
    decisionState.status === 'failure' &&
    decisionState.failure?.kind === FailureKind.CONFLICT &&
    decisionState.failure?.code === 'MLS-REV-001',
)

async function load(cursor) {
  const envelope = await run(() => listReviews({ status: status.value || undefined, cursor, limit: 20 }))
  if (!envelope) return
  reviews.value = cursor ? [...reviews.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

function select(review) {
  selectionRequest.value = (selectionRequest.value ?? 0) + 1
  selected.value = review
  decisionResult.value = null
}

async function submitDecision() {
  const envelope = await runDecision(() =>
    decideReview({ reviewId: selected.value.reviewId, decision: decision.value, reason: reason.value }),
  )
  if (envelope) {
    decisionResult.value = envelope.data
    await load()
  }
}

load()
</script>

<style scoped>
.review-list-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.filters,
.action-block form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.inline-input {
  min-width: 16rem;
}

.review-table {
  width: 100%;
  border-collapse: collapse;
}

.review-table th,
.review-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}

.action-block {
  border: 1px solid #e2eaf0;
  border-radius: 0.5rem;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.note {
  color: #526274;
  font-size: 0.85rem;
}
</style>
