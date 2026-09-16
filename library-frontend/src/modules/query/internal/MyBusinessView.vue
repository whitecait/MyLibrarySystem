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
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getMyBusiness } from '../index'
import { cancelReservation as cancelReservationRequest, renewLoan } from '../../circulation'

const items = ref([])
const nextCursor = ref(null)
const kind = ref('')
const { state, run } = useApiRequest()
const { state: renewState, run: runRenew } = useApiRequest()
const renewResult = ref(null)
const { state: cancelState, run: runCancel } = useApiRequest()

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
