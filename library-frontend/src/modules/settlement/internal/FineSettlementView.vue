<template>
  <section class="fine-settlement-view">
    <h1>罚款与支付</h1>
    <p class="simulation-warning" role="note" data-testid="payment-simulation-warning">
      <strong>模拟支付：</strong>本项目不接入真实资金；支付只用于流程验证，不会扣款或转账。
    </p>

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
                  v-if="fine.status === 'UNPAID' && !hasOccupiedOrder(fine.fineId)"
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

    <div v-if="occupiedOrders.length" class="action-block" data-testid="occupied-orders-panel">
      <h2>未释放的支付单</h2>
      <ul class="occupied-orders">
        <li v-for="occupied in occupiedOrders" :key="occupied.orderId">
          <el-button
            size="small"
            :data-testid="`select-order-${occupied.orderId}`"
            :type="order?.orderId === occupied.orderId ? 'primary' : 'default'"
            @click="selectOccupiedOrder(occupied)"
          >
            支付单 {{ occupied.orderId }}（{{ occupied.status }}）
          </el-button>
        </li>
      </ul>
      <el-button
        v-if="occupiedOrdersCursor"
        data-testid="occupied-orders-load-more"
        :loading="occupiedListState.status === 'loading'"
        @click="loadOccupiedOrders(occupiedOrdersCursor)"
      >
        加载更多支付单
      </el-button>
    </div>
    <ErrorAlert v-if="occupiedListState.status === 'failure'" :failure="occupiedListState.failure" />

    <ActionPanel title="支付单详情" entry-id="fine-order" v-if="order" :active-key="selectionRequest">
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
      </div>
      <ErrorAlert v-if="closeState.status === 'failure'" :failure="closeState.failure" />
      <ErrorAlert v-if="queryState.status === 'failure'" :failure="queryState.failure" />
    </ActionPanel>
    <ErrorAlert v-if="orderState.status === 'failure'" :failure="orderState.failure" />
  </section>
</template>

<script setup>
import ActionPanel from '@/components/ActionPanel.vue'
import { computed, ref } from 'vue'
import { getRole } from '@/platform/auth/session'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { closeOrder, createOrder, getOrder, listFines, listOccupiedOrders } from '../index'

const selectionRequest = ref(null)

const fines = ref([])
const nextCursor = ref(null)
const readerId = ref('')
const status = ref('')
const { state: listState, run: runList } = useApiRequest()

const occupiedOrders = ref([])
const occupiedOrdersCursor = ref(null)
const { state: occupiedListState, run: runOccupiedList } = useApiRequest()
const order = ref(null)
const { state: orderState, run: runOrder } = useApiRequest()
const { state: queryState, run: runQuery } = useApiRequest()
const { state: closeState, run: runClose } = useApiRequest()

const canClose = computed(() => order.value?.status === 'OCCUPIED')

function hasOccupiedOrder(fineId) {
  return occupiedOrders.value.some((candidate) => candidate.fineId === fineId)
}

function selectOccupiedOrder(value) {
  selectionRequest.value = (selectionRequest.value ?? 0) + 1
  order.value = value
}

function rememberOrder(value) {
  if (value.status === 'OCCUPIED') {
    const existingIndex = occupiedOrders.value.findIndex((candidate) => candidate.orderId === value.orderId)
    if (existingIndex === -1) occupiedOrders.value = [...occupiedOrders.value, value]
    else occupiedOrders.value = occupiedOrders.value.map((candidate) =>
      candidate.orderId === value.orderId ? value : candidate,
    )
  } else {
    occupiedOrders.value = occupiedOrders.value.filter((candidate) => candidate.orderId !== value.orderId)
  }
}

async function load(cursor) {
  const envelope = await runList(() =>
    listFines({ readerId: readerId.value || undefined, status: status.value || undefined, cursor, limit: 20 }),
  )
  if (!envelope) return
  fines.value = cursor ? [...fines.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

async function createOrderFor(fine) {
  const envelope = await runOrder(() => createOrder({ fineId: fine.fineId }))
  if (envelope) {
    order.value = envelope.data
    selectionRequest.value = (selectionRequest.value ?? 0) + 1
    rememberOrder(envelope.data)
  } else if (getRole() === 'READER') {
    await loadOccupiedOrders()
  }
}

async function loadOccupiedOrders(cursor) {
  if (getRole() !== 'READER') return
  const envelope = await runOccupiedList(() => listOccupiedOrders({ cursor, limit: 20 }))
  if (!envelope) return
  occupiedOrders.value = cursor ? [...occupiedOrders.value, ...envelope.data.items] : envelope.data.items
  occupiedOrdersCursor.value = envelope.data.nextCursor
}

async function refreshOrder() {
  const envelope = await runQuery(() => getOrder({ orderId: order.value.orderId }))
  if (envelope) {
    order.value = envelope.data
    rememberOrder(envelope.data)
    if (envelope.data.status === 'PAID') {
      fines.value = fines.value.map((fine) =>
        fine.fineId === envelope.data.fineId ? { ...fine, status: 'PAID' } : fine,
      )
    }
  }
}

async function close() {
  const envelope = await runClose(() => closeOrder({ orderId: order.value.orderId }))
  if (envelope) {
    order.value = envelope.data
    rememberOrder(envelope.data)
  }
}

load()
if (getRole() === 'READER') loadOccupiedOrders()
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

.simulation-warning {
  margin: 0;
  padding: 0.75rem 1rem;
  border: 1px solid #e6a23c;
  border-radius: 0.5rem;
  background: #fdf6ec;
  color: #7a4b00;
}

.action-block form,
.row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.occupied-orders {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin: 0;
  padding: 0;
  list-style: none;
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
