<template>
  <section class="my-business-view">
    <h1>我的业务</h1>
    <form class="filters" data-testid="my-business-filter" @submit.prevent="load()">
      <select v-model="kind" data-testid="my-business-kind" aria-label="业务类型">
        <option value="">全部类型</option>
        <option value="RESERVATION">预约</option>
        <option value="LOAN">借阅</option>
        <option value="FINE">罚款</option>
        <option value="ORDER">支付单</option>
      </select>
      <el-button native-type="submit" data-testid="my-business-submit">查询</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="my-business-loading">加载中…</p>
    <el-empty
      v-else-if="state.status === 'success' && items.length === 0"
      data-testid="my-business-empty"
      description="暂无业务记录"
    />
    <template v-else-if="state.status === 'success'">
      <table class="business-table" data-testid="my-business-table">
        <thead>
          <tr><th>类型</th><th>编号</th><th>状态</th><th>书名</th><th>营业日</th><th>金额</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="`${item.kind}-${item.id}`">
            <td>{{ item.kind }}</td>
            <td>{{ item.id }}</td>
            <td>{{ item.status }}</td>
            <td>{{ item.title ?? '—' }}</td>
            <td>{{ item.businessDate ?? '—' }}</td>
            <td>{{ item.amountCents != null ? `${(item.amountCents / 100).toFixed(2)} 元` : '—' }}</td>
            <td class="row-actions">
              <el-button
                v-if="item.kind === 'LOAN'"
                size="small"
                :data-testid="`renew-${item.id}`"
                :loading="renewState.status === 'loading'"
                @click="renew(item)"
              >
                续借
              </el-button>
              <el-button
                v-if="item.kind === 'RESERVATION'"
                size="small"
                :data-testid="`cancel-${item.id}`"
                :loading="cancelState.status === 'loading'"
                @click="cancelReservation(item)"
              >
                取消预约
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <el-button v-if="nextCursor" data-testid="my-business-load-more" @click="load(nextCursor)">加载更多</el-button>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
    <el-alert
      v-if="renewResult"
      type="success"
      :closable="false"
      data-testid="renew-result"
      :title="`续借成功：交易 ${renewResult.txId}，新到期日 ${renewResult.dueDate}`"
    />
    <ErrorAlert v-if="renewState.status === 'failure'" :failure="renewState.failure" />
    <ErrorAlert v-if="cancelState.status === 'failure'" :failure="cancelState.failure" />

    <el-dialog v-model="renewModalVisible" title="续借" data-testid="renew-modal">
      <p v-if="aiDraftId" class="note" data-testid="renew-draft-note">
        来自 AI 助手预填（草稿 {{ aiDraftId }}）；提交时走现行续借接口并重新鉴权，取消不会发送任何请求。
      </p>
      <el-input v-model="renewModalTxId" data-testid="renew-modal-tx-id" placeholder="交易号" />
      <template #footer>
        <el-button data-testid="renew-modal-cancel" @click="closeRenewModal">取消</el-button>
        <el-button type="primary" data-testid="renew-modal-submit" :loading="modalRenewState.status === 'loading'" @click="submitModalRenew">
          提交续借
        </el-button>
      </template>
    </el-dialog>

    <AiAssistantView can-apply-prefill data-testid="ai-panel" @prefill="applyAiPrefill" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getMyBusiness } from '../index'
import { cancelReservation as cancelReservationRequest, renewLoan } from '../../circulation'
import { AiAssistantView } from '../../ai'

const items = ref([])
const nextCursor = ref(null)
const kind = ref('')
const { state, run } = useApiRequest()
const { state: renewState, run: runRenew } = useApiRequest()
const renewResult = ref(null)
const { state: cancelState, run: runCancel } = useApiRequest()

const renewModalVisible = ref(false)
const renewModalTxId = ref('')
const aiDraftId = ref(null)
const { state: modalRenewState, run: runModalRenew } = useApiRequest()

async function load(cursor) {
  const envelope = await run(() => getMyBusiness({ kind: kind.value || undefined, cursor, limit: 20 }))
  if (!envelope) return
  items.value = cursor ? [...items.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

async function renew(item) {
  const envelope = await runRenew(() => renewLoan({ txId: item.id }))
  if (envelope) renewResult.value = envelope.data
}

async function cancelReservation(item) {
  await runCancel(() => cancelReservationRequest({ txId: item.id }))
  if (cancelState.status === 'success') await load()
}

function applyAiPrefill(action) {
  if (action.actionType !== 'RENEW_LOAN') return
  renewModalTxId.value = action.prefill?.txId ?? ''
  aiDraftId.value = action.draftId
  renewModalVisible.value = true
}

function closeRenewModal() {
  renewModalVisible.value = false
  aiDraftId.value = null
  renewModalTxId.value = ''
}

async function submitModalRenew() {
  const headers = aiDraftId.value ? { 'Ai-Draft-Id': aiDraftId.value } : undefined
  const envelope = await runModalRenew(() => renewLoan({ txId: renewModalTxId.value }, { headers }))
  if (!envelope) return
  renewResult.value = envelope.data
  closeRenewModal()
}

load()
</script>

<style scoped>
.my-business-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.filters {
  display: flex;
  gap: 0.75rem;
}

.business-table {
  width: 100%;
  border-collapse: collapse;
}

.business-table th,
.business-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}

.row-actions {
  display: flex;
  gap: 0.5rem;
}
</style>
