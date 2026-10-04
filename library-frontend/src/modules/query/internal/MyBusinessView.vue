<template>
  <section class="my-business-view">
    <h1>我的业务</h1>
    <div class="business-tabs" role="tablist" aria-label="借阅事务分类">
      <button
        v-for="tab in BUSINESS_TABS"
        :key="tab.group"
        type="button"
        role="tab"
        :aria-selected="activeGroup === tab.group"
        :data-testid="`my-business-tab-${tab.group}`"
        @click="selectGroup(tab.group)"
      >
        {{ tab.label }}
      </button>
    </div>

    <p v-if="businessState.status === 'loading' && businessState.items.length === 0" data-testid="my-business-loading">
      加载中…
    </p>
    <p v-else-if="businessState.status === 'loading'" data-testid="my-business-refreshing">正在更新…</p>
    <ErrorAlert v-if="businessState.status === 'failure'" :failure="businessState.failure" />
    <el-button
      v-if="businessState.status === 'failure'"
      data-testid="my-business-retry"
      @click="loadGroup(activeGroup)"
    >
      重试
    </el-button>
    <el-empty
      v-if="businessState.status === 'success' && businessState.items.length === 0"
      data-testid="my-business-empty"
      description="当前分类暂无业务记录"
    />
    <template v-if="businessState.items.length > 0">
      <table class="business-table" data-testid="my-business-table">
        <thead>
          <tr><th>类型</th><th>编号</th><th>状态</th><th>书名</th><th>营业日</th><th>金额</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in businessState.items" :key="`${item.kind}-${item.id}`">
            <td>{{ item.kind }}</td>
            <td>{{ item.id }}</td>
            <td>{{ item.status }}</td>
            <td>{{ item.title ?? '—' }}</td>
            <td>{{ item.businessDate ?? '—' }}</td>
            <td>{{ formatAmount(item) }}</td>
            <td class="row-actions">
              <router-link
                v-if="isBorrowTransaction(item)"
                :to="`/me/business/${item.id}`"
                :data-testid="`business-detail-link-${item.id}`"
                :aria-label="`查看业务 ${item.id} 详情`"
              >
                查看详情
              </router-link>
              <el-button
                v-if="item.kind === 'LOAN' && item.status === 'LENT'"
                size="small"
                :data-testid="`renew-${item.id}`"
                :loading="renewState.status === 'loading'"
                @click="renew(item)"
              >
                续借
              </el-button>
              <el-button
                v-if="item.kind === 'RESERVATION' && CANCELLABLE_RESERVATION_STATUSES.has(item.status)"
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
      <el-button
        v-if="businessState.nextCursor"
        data-testid="my-business-load-more"
        :disabled="businessState.status === 'loading'"
        @click="loadGroup(activeGroup, businessState.nextCursor)"
      >
        加载更多
      </el-button>
    </template>

    <section class="other-business" aria-label="其他业务">
      <label for="my-business-other-kind">其他业务</label>
      <select
        id="my-business-other-kind"
        v-model="otherKind"
        data-testid="my-business-other-kind"
        aria-label="其他业务类型"
        @change="loadSelectedOtherKind"
      >
        <option value="">选择罚款或支付单</option>
        <option value="FINE">罚款</option>
        <option value="ORDER">支付单</option>
      </select>
      <template v-if="otherKind">
        <h2>{{ otherKind === 'FINE' ? '罚款' : '支付单' }}</h2>
        <p
          v-if="otherBusinessState.status === 'loading' && otherBusinessState.items.length === 0"
          data-testid="my-business-other-loading"
        >
          加载中…
        </p>
        <p v-else-if="otherBusinessState.status === 'loading'" data-testid="my-business-other-refreshing">
          正在更新…
        </p>
        <ErrorAlert v-if="otherBusinessState.status === 'failure'" :failure="otherBusinessState.failure" />
        <el-button
          v-if="otherBusinessState.status === 'failure'"
          data-testid="my-business-other-retry"
          @click="loadOther(otherKind)"
        >
          重试
        </el-button>
        <el-empty
          v-if="otherBusinessState.status === 'success' && otherBusinessState.items.length === 0"
          data-testid="my-business-other-empty"
          :description="otherKind === 'FINE' ? '暂无罚款记录' : '暂无支付单记录'"
        />
        <template v-if="otherBusinessState.items.length > 0">
          <table class="business-table" data-testid="my-business-other-table">
            <thead>
              <tr><th>类型</th><th>编号</th><th>状态</th><th>说明</th><th>营业日</th><th>金额</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in otherBusinessState.items" :key="`${item.kind}-${item.id}`">
                <td>{{ item.kind }}</td>
                <td>{{ item.id }}</td>
                <td>{{ item.status }}</td>
                <td>{{ item.title ?? '—' }}</td>
                <td>{{ item.businessDate ?? '—' }}</td>
                <td>{{ formatAmount(item) }}</td>
              </tr>
            </tbody>
          </table>
          <el-button
            v-if="otherBusinessState.nextCursor"
            data-testid="my-business-other-load-more"
            :disabled="otherBusinessState.status === 'loading'"
            @click="loadOther(otherKind, otherBusinessState.nextCursor)"
          >
            加载更多其他业务
          </el-button>
        </template>
      </template>
    </section>

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
      <ErrorAlert v-if="modalRenewState.status === 'failure'" :failure="modalRenewState.failure" />
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
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind, RequestFailure, useApiRequest } from '@/platform/http'
import { getSessionState, isAuthenticated } from '@/platform/auth/session'
import { getMyBusiness } from '../index'
import { cancelReservation as cancelReservationRequest, renewLoan } from '../../circulation'
import { AiAssistantView } from '../../ai'

const BUSINESS_TABS = [
  { group: 'CURRENT_LOANS', label: '当前借阅' },
  { group: 'RESERVATIONS', label: '预约与候补' },
  { group: 'HISTORY', label: '历史记录' },
]
const LIST_KEYS = [...BUSINESS_TABS.map(({ group }) => group), 'FINE', 'ORDER']
const PAGE_SIZE = 20
const CANCELLABLE_RESERVATION_STATUSES = new Set(['AWAIT', 'CONFIRMED', 'WAITLIST', 'MANUAL_HANDLING'])

function newListState() {
  return { items: [], nextCursor: null, status: 'idle', failure: null, hasLoaded: false, requestId: 0 }
}

function newActionState() {
  return { status: 'idle', failure: null, requestId: 0 }
}

const lists = reactive(Object.fromEntries(LIST_KEYS.map((key) => [key, newListState()])))
const activeGroup = ref('CURRENT_LOANS')
const otherKind = ref('')
const businessState = computed(() => lists[activeGroup.value])
const otherBusinessState = computed(() => lists[otherKind.value] ?? newListState())
const session = getSessionState()
let sessionGeneration = 0

const renewState = reactive(newActionState())
const cancelState = reactive(newActionState())
const modalRenewState = reactive(newActionState())
const renewResult = ref(null)
const renewModalVisible = ref(false)
const renewModalTxId = ref('')
const aiDraftId = ref(null)

function snapshotSession() {
  return {
    token: session.token,
    accountId: session.actor?.accountId ?? null,
    role: session.actor?.role ?? null,
    expiresAt: session.expiresAt,
  }
}

function sessionIsCurrent(identity) {
  if (session.token !== identity.token
    || (session.actor?.accountId ?? null) !== identity.accountId
    || (session.actor?.role ?? null) !== identity.role
    || session.expiresAt !== identity.expiresAt) return false
  return !identity.token || (identity.accountId && identity.role === 'READER' && isAuthenticated())
}

function isReaderSession() {
  return Boolean(session.token && session.actor?.accountId && session.actor?.role === 'READER' && isAuthenticated())
}

function resetListState(list) {
  list.requestId += 1
  list.items = []
  list.nextCursor = null
  list.status = 'idle'
  list.failure = null
  list.hasLoaded = false
}

function resetActionState(actionState) {
  actionState.requestId += 1
  actionState.status = 'idle'
  actionState.failure = null
}

function malformedResponse() {
  return new RequestFailure({ kind: FailureKind.MALFORMED, message: '本人业务查询响应异常' })
}

async function loadList(key, query, cursor) {
  const list = lists[key]
  const identity = snapshotSession()
  const requestGeneration = sessionGeneration
  const requestId = ++list.requestId
  const request = useApiRequest()
  list.status = 'loading'
  list.failure = null

  const envelope = await request.run(() => getMyBusiness({ ...query, cursor, limit: PAGE_SIZE }))
  if (requestId !== list.requestId
    || requestGeneration !== sessionGeneration
    || !sessionIsCurrent(identity)) return null

  list.status = request.state.status
  list.failure = request.state.failure
  list.hasLoaded = true
  if (!envelope) return null

  const page = envelope.data
  if (!Array.isArray(page?.items)
    || !Object.hasOwn(page, 'nextCursor')
    || (page.nextCursor != null && (typeof page.nextCursor !== 'string' || page.nextCursor.length === 0))) {
    list.status = 'failure'
    list.failure = malformedResponse()
    return null
  }

  list.items = cursor ? [...list.items, ...page.items] : page.items
  list.nextCursor = page.nextCursor
  return envelope
}

function loadGroup(group, cursor) {
  return loadList(group, { group }, cursor)
}

function loadOther(kind, cursor) {
  return loadList(kind, { kind }, cursor)
}

function selectGroup(group) {
  activeGroup.value = group
  const list = lists[group]
  if (!list.hasLoaded && list.status !== 'loading') void loadGroup(group)
  if (list.status === 'failure') void loadGroup(group)
}

function loadSelectedOtherKind() {
  if (!otherKind.value) return
  const list = lists[otherKind.value]
  if (!list.hasLoaded && list.status !== 'loading') void loadOther(otherKind.value)
  if (list.status === 'failure') void loadOther(otherKind.value)
}

function isBorrowTransaction(item) {
  return (item.kind === 'LOAN' || item.kind === 'RESERVATION') && item.id != null
}

function formatAmount(item) {
  return item.amountCents != null ? `${(item.amountCents / 100).toFixed(2)} 元` : '—'
}

async function runAction(actionState, executor) {
  const identity = snapshotSession()
  const requestGeneration = sessionGeneration
  const requestId = ++actionState.requestId
  const request = useApiRequest()
  actionState.status = 'loading'
  actionState.failure = null
  const envelope = await request.run(executor)
  if (requestId !== actionState.requestId
    || requestGeneration !== sessionGeneration
    || !sessionIsCurrent(identity)) return null
  actionState.status = request.state.status
  actionState.failure = request.state.failure
  return envelope
}

async function renew(item) {
  const envelope = await runAction(renewState, () => renewLoan({ txId: item.id }))
  if (!envelope) return
  renewResult.value = envelope.data
  await loadGroup('CURRENT_LOANS')
}

async function cancelReservation(item) {
  const envelope = await runAction(
    cancelState,
    () => cancelReservationRequest({ txId: item.id, reason: '读者自助取消预约' }),
  )
  if (!envelope) return

  resetListState(lists.RESERVATIONS)
  resetListState(lists.HISTORY)

  const currentGroup = activeGroup.value
  if (currentGroup === 'RESERVATIONS' || currentGroup === 'HISTORY') {
    await loadGroup(currentGroup)
  }
}

function applyAiPrefill(action) {
  if (action.actionType !== 'RENEW_LOAN') return
  resetActionState(modalRenewState)
  renewResult.value = null
  renewModalTxId.value = action.prefill?.txId ?? ''
  aiDraftId.value = action.draftId
  renewModalVisible.value = true
}

function closeRenewModal() {
  resetActionState(modalRenewState)
  renewModalVisible.value = false
  aiDraftId.value = null
  renewModalTxId.value = ''
}

async function submitModalRenew() {
  const headers = aiDraftId.value ? { 'Ai-Draft-Id': aiDraftId.value } : undefined
  const envelope = await runAction(
    modalRenewState,
    () => renewLoan({ txId: renewModalTxId.value }, { headers }),
  )
  if (!envelope) return
  renewResult.value = envelope.data
  closeRenewModal()
  await loadGroup('CURRENT_LOANS')
}

watch(
  [
    () => session.token,
    () => session.actor?.accountId,
    () => session.actor?.role,
    () => session.expiresAt,
  ],
  () => {
    sessionGeneration += 1
    Object.values(lists).forEach(resetListState)
    resetActionState(renewState)
    resetActionState(cancelState)
    resetActionState(modalRenewState)
    renewResult.value = null
    renewModalVisible.value = false
    renewModalTxId.value = ''
    aiDraftId.value = null
    if (!isReaderSession()) return
    void loadGroup(activeGroup.value)
    if (otherKind.value) void loadOther(otherKind.value)
  },
)

onBeforeUnmount(() => {
  sessionGeneration += 1
  Object.values(lists).forEach((list) => { list.requestId += 1 })
  resetActionState(renewState)
  resetActionState(cancelState)
  resetActionState(modalRenewState)
})

void loadGroup(activeGroup.value)
</script>

<style scoped>
.my-business-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.business-tabs,
.other-business,
.row-actions {
  display: flex;
  gap: 0.75rem;
}

.business-tabs button[aria-selected='true'] {
  font-weight: 700;
  border-bottom: 2px solid currentColor;
}

.other-business {
  flex-direction: column;
  align-items: flex-start;
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
</style>
