<template>
  <section class="account-admin-view">
    <h1>账户管理</h1>

    <form class="filters" data-testid="account-filters" @submit.prevent="load()">
      <select v-model="filterRole" data-testid="account-filter-role" aria-label="按角色筛选">
        <option value="">全部角色</option>
        <option value="READER">读者</option>
        <option value="LIBRARIAN">馆员</option>
        <option value="ADMIN">管理员</option>
      </select>
      <select v-model="filterStatus" data-testid="account-filter-status" aria-label="按状态筛选">
        <option value="">全部状态</option>
        <option value="ACTIVE">启用</option>
        <option value="SUSPENDED">停用</option>
        <option value="DISABLED">禁用</option>
      </select>
      <el-button native-type="submit" data-testid="account-filter-submit">查询</el-button>
    </form>

    <p v-if="listState.status === 'loading'" data-testid="account-list-loading">加载中…</p>
    <template v-else-if="listState.status === 'success'">
      <table class="account-table" data-testid="account-table">
        <thead>
          <tr><th>账户</th><th>显示名</th><th>角色</th><th>状态</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="account in accounts" :key="account.accountId">
            <td>{{ account.username }}</td>
            <td>{{ account.displayName }}</td>
            <td>{{ account.role }}</td>
            <td>{{ account.status }}</td>
            <td>
              <el-button size="small" :data-testid="`account-select-${account.accountId}`" @click="select(account)">
                选择
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <el-button v-if="nextCursor" data-testid="account-load-more" @click="load(nextCursor)">加载更多</el-button>
    </template>
    <ErrorAlert v-if="listState.status === 'failure'" :failure="listState.failure" />

    <template v-if="selected">
      <h2 data-testid="selected-account">已选择：{{ selected.username }}（{{ selected.role }} / {{ selected.status }}）</h2>

      <div class="action-block">
        <h3>高风险变更申请</h3>
        <form data-testid="change-form" @submit.prevent="submitChange">
          <select v-model="changeAction" data-testid="change-action" aria-label="变更动作">
            <option value="SUSPEND">停用账户</option>
            <option value="RESTORE">恢复账户</option>
            <option value="CHANGE_ROLE">变更角色</option>
          </select>
          <select v-if="changeAction === 'CHANGE_ROLE'" v-model="changeRole" data-testid="change-role" aria-label="目标角色">
            <option value="READER">读者</option>
            <option value="LIBRARIAN">馆员</option>
            <option value="ADMIN">管理员</option>
          </select>
          <el-input
            v-model="changeReason"
            data-testid="change-reason"
            placeholder="变更原因（必填）"
            class="reason-input"
          />
          <el-button type="primary" native-type="submit" data-testid="change-submit">提交复核</el-button>
        </form>
        <el-alert
          v-if="changeResult"
          type="warning"
          :closable="false"
          data-testid="change-review-result"
          :title="`已提交复核：受理号 ${changeResult.reviewId}（${changeResult.status}）`"
          description="变更在管理员批准前不生效。"
        />
        <ErrorAlert v-if="changeState.status === 'failure'" :failure="changeState.failure" />
      </div>

      <div class="action-block">
        <h3>重置密码</h3>
        <form data-testid="reset-form" @submit.prevent="submitReset">
          <el-input v-model="resetReason" data-testid="reset-reason" placeholder="重置原因（必填）" class="reason-input" />
          <el-button type="primary" native-type="submit" data-testid="reset-submit">重置</el-button>
        </form>
        <el-alert
          v-if="resetResult"
          type="success"
          :closable="false"
          data-testid="reset-result"
          :title="`临时密码：${resetResult.temporaryPassword}`"
          description="该账户下次登录必须修改密码。"
        />
        <ErrorAlert v-if="resetState.status === 'failure'" :failure="resetState.failure" />
      </div>

      <div class="action-block">
        <h3>读者状态办理</h3>
        <form data-testid="reader-status-form" @submit.prevent="submitReaderStatus">
          <select v-model="readerStatus" data-testid="reader-status" aria-label="读者状态">
            <option value="ACTIVE">启用</option>
            <option value="SUSPENDED">停用</option>
          </select>
          <el-input
            v-model="readerReason"
            data-testid="reader-status-reason"
            placeholder="办理原因（必填）"
            class="reason-input"
          />
          <el-button type="primary" native-type="submit" data-testid="reader-status-submit">提交</el-button>
        </form>
        <el-alert
          v-if="readerResult"
          type="success"
          :closable="false"
          data-testid="reader-status-result"
          :title="`读者 ${readerResult.readerId} 状态：${readerResult.status}`"
        />
        <ErrorAlert v-if="readerState.status === 'failure'" :failure="readerState.failure" />
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { changeReaderStatus, listAccounts, requestAccountChange, resetPassword } from '../index'

const accounts = ref([])
const nextCursor = ref(null)
const filterRole = ref('')
const filterStatus = ref('')
const selected = ref(null)

const { state: listState, run: runList } = useApiRequest()
const { state: changeState, run: runChange } = useApiRequest()
const { state: resetState, run: runReset } = useApiRequest()
const { state: readerState, run: runReader } = useApiRequest()

const changeAction = ref('SUSPEND')
const changeRole = ref('READER')
const changeReason = ref('')
const changeResult = ref(null)
const resetReason = ref('')
const resetResult = ref(null)
const readerStatus = ref('ACTIVE')
const readerReason = ref('')
const readerResult = ref(null)

async function load(cursor) {
  const envelope = await runList(() =>
    listAccounts({
      role: filterRole.value || undefined,
      status: filterStatus.value || undefined,
      cursor: cursor || undefined,
      limit: 20,
    }),
  )
  if (!envelope) return
  accounts.value = cursor ? [...accounts.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

function select(account) {
  selected.value = account
  changeResult.value = null
  resetResult.value = null
  readerResult.value = null
}

async function submitChange() {
  const payload = changeAction.value === 'CHANGE_ROLE' ? { role: changeRole.value } : {}
  const envelope = await runChange(() =>
    requestAccountChange({
      accountId: selected.value.accountId,
      action: changeAction.value,
      payload,
      reason: changeReason.value,
    }),
  )
  if (envelope) changeResult.value = envelope.data
}

async function submitReset() {
  const envelope = await runReset(() => resetPassword({ accountId: selected.value.accountId, reason: resetReason.value }))
  if (envelope) resetResult.value = envelope.data
}

async function submitReaderStatus() {
  const envelope = await runReader(() =>
    changeReaderStatus({ readerId: selected.value.accountId, status: readerStatus.value, reason: readerReason.value }),
  )
  if (envelope) readerResult.value = envelope.data
}

load()
</script>

<style scoped>
.account-admin-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.filters {
  display: flex;
  gap: 0.75rem;
}

.account-table {
  width: 100%;
  border-collapse: collapse;
}

.account-table th,
.account-table td {
  text-align: left;
  padding: 0.5rem 0.75rem;
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

.action-block form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.reason-input {
  min-width: 16rem;
}
</style>
