<template>
  <section class="fine-settlement-view">
    <h1>罚款与支付</h1>

    <div class="action-block">
      <h2>罚款列表</h2>
      <form data-testid="fines-filter" @submit.prevent="load()">
        <el-input v-model="readerId" data-testid="fines-reader-id" placeholder="读者 ID（可选）" class="inline-input" />
        <select v-model="status" data-testid="fines-status" aria-label="罚款状态">
          <option value="">全部状态</option>
          <option value="UNPAID">未支付</option>
          <option value="OCCUPIED">支付中</option>
          <option value="PAID">已支付</option>
          <option value="REFUNDED">已退款</option>
        </select>
        <el-button native-type="submit" data-testid="fines-submit">查询</el-button>
      </form>
      <p v-if="listState.status === 'loading'" data-testid="fines-loading">加载中…</p>
      <template v-else-if="listState.status === 'success'">
        <table class="fines-table" data-testid="fines-table">
          <thead>
            <tr><th>罚款</th><th>读者</th><th>金额</th><th>状态</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="fine in fines" :key="fine.fineId">
              <td>{{ fine.fineId }}</td>
              <td>{{ fine.readerId }}</td>
              <td>{{ (fine.amountCents / 100).toFixed(2) }} 元</td>
              <td>{{ fine.status }}</td>
              <td>
                <el-button
                  v-if="fine.status === 'UNPAID'"
                  size="small"
                  type="primary"
                  :data-testid="`create-order-${fine.fineId}`"
                  :loading="orderState.status === 'loading'"
                  @click="createOrderFor(fine)"
                >
                  建单支付
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
        <el-button v-if="nextCursor" data-testid="fines-load-more" @click="load(nextCursor)">加载更多</el-button>
      </template>
      <ErrorAlert v-if="listState.status === 'failure'" :failure="listState.failure" />
    </div>

    <div v-if="order" class="action-block">
      <h2 data-testid="order-panel-title">支付单 {{ order.orderId }}（{{ order.status }}）</h2>
      <p data-testid="order-amount">金额：{{ (order.amountCents / 100).toFixed(2) }} 元</p>
      <p class="note" data-testid="order-wait-note">
        支付结果以回调为准；本页仅手动刷新查询，等待超时不代表支付失败。
      </p>
      <div class="row">
        <el-button data-testid="order-refresh" :loading="queryState.status === 'loading'" @click="refreshOrder">刷新状态</el-button>
        <el-button
          v-if="canClose"
          data-testid="order-close"
          :loading="closeState.status === 'loading'"
          @click="close"
        >
          关闭支付单
        </el-button>
        <template v-if="isLibrarian">
          <el-input v-model="refundAmount" data-testid="refund-amount" placeholder="退款金额（元）" class="amount-input" />
          <el-input v-model="refundReason" data-testid="refund-reason" placeholder="退款原因（必填）" class="inline-input" />
          <el-button data-testid="refund-submit" :loading="refundState.status === 'loading'" @click="submitRefund">
            申请退款
          </el-button>
        </template>
      </div>
      <el-alert
        v-if="refundResult"
        type="warning"
        :closable="false"
        data-testid="refund-review-pending"
        :title="`退款申请已提交复核：受理号 ${refundResult.reviewId}（${refundResult.status}）`"
        description="退款在管理员批准前不生效。"
      />
      <ErrorAlert v-if="refundState.status === 'failure'" :failure="refundState.failure" />
    </div>
    <ErrorAlert v-if="orderState.status === 'failure'" :failure="orderState.failure" />
    <ErrorAlert v-if="closeState.status === 'failure'" :failure="closeState.failure" />
    <ErrorAlert v-if="queryState.status === 'failure'" :failure="queryState.failure" />
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getRole } from '@/platform/auth/session'
import { closeOrder, createOrder, getOrder, listFines, requestRefund } from '../index'

const fines = ref([])
const nextCursor = ref(null)
const readerId = ref('')
const status = ref('')
const { state: listState, run: runList } = useApiRequest()

const order = ref(null)
const { state: orderState, run: runOrder } = useApiRequest()
const { state: queryState, run: runQuery } = useApiRequest()
const { state: closeState, run: runClose } = useApiRequest()
const refundAmount = ref('')
const refundReason = ref('')
const { state: refundState, run: runRefund } = useApiRequest()
const refundResult = ref(null)

const isLibrarian = computed(() => getRole() === 'LIBRARIAN')
const canClose = computed(() => order.value?.status === 'FAILED')

async function load(cursor) {
  const envelope = await runList(() =>
    listFines({ readerId: readerId.value || undefined, status: status.value || undefined, cursor, limit: 20 }),
  )
  if (!envelope) return
  fines.value = cursor ? [...fines.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

async function createOrderFor(fine) {
  refundResult.value = null
  const envelope = await runOrder(() => createOrder({ fineId: fine.fineId }))
  if (envelope) order.value = envelope.data
}

async function refreshOrder() {
  const envelope = await runQuery(() => getOrder({ orderId: order.value.orderId }))
  if (envelope) order.value = envelope.data
}

async function close() {
  const envelope = await runClose(() => closeOrder({ orderId: order.value.orderId }))
  if (envelope) order.value = envelope.data
}

async function submitRefund() {
  const envelope = await runRefund(() =>
    requestRefund({ orderId: order.value.orderId, amountCents: Math.round(Number(refundAmount.value) * 100), reason: refundReason.value }),
  )
  if (envelope) refundResult.value = envelope.data
}

load()
</script>

<style scoped>
.fine-settlement-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.action-block {
  border: 1px solid #e2eaf0;
  border-radius: 0.5rem;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.action-block form,
.row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.inline-input {
  min-width: 14rem;
}

.amount-input {
  width: 10rem;
}

.fines-table {
  width: 100%;
  border-collapse: collapse;
}

.fines-table th,
.fines-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}

.note {
  color: #526274;
  font-size: 0.85rem;
}
</style>
