<template>
  <section class="copy-admin-view">
    <h1>副本维护</h1>

    <ActionPanel title="登记新副本" entry-id="copy-create" description="使用下方查询中的书目 ID 登记副本。">
      <h2>登记新副本</h2>
      <form data-testid="create-copy-form" @submit.prevent="submitCreate">
        <el-input v-model="newCopyNo" data-testid="create-copy-no" placeholder="副本编号（如 C-003）" class="inline-input" />
        <el-button type="primary" native-type="submit" data-testid="create-copy-submit" :loading="createState.status === 'loading'">
          登记副本
        </el-button>
      </form>
      <el-alert
        v-if="createResult"
        type="success"
        :closable="false"
        data-testid="create-copy-success"
        :title="`已登记副本 ${createResult.copyNo}（${createResult.status}），待上架`"
      />
      <ErrorAlert v-if="createState.status === 'failure'" :failure="createState.failure" />
    </ActionPanel>

    <div class="action-block">
      <h2>副本列表</h2>
      <form data-testid="copy-filter-form" @submit.prevent="load()">
        <el-input v-model="filterTitleId" data-testid="copy-filter-title" placeholder="书目 ID（如 2001）" class="inline-input" />
        <select v-model="filterStatus" data-testid="copy-filter-status" aria-label="副本状态">
          <option value="">全部状态</option>
          <option value="PENDING_SHELVING">待上架</option>
          <option value="AVAILABLE">可借</option>
          <option value="RESERVED">已预约</option>
          <option value="BORROWED">借出</option>
          <option value="MAINTENANCE">维护中</option>
          <option value="REMOVED">已移除</option>
        </select>
        <el-button native-type="submit" data-testid="copy-filter-submit">查询</el-button>
      </form>
      <p v-if="listState.status === 'loading'" data-testid="copy-list-loading">加载中…</p>
      <template v-else-if="listState.status === 'success'">
        <table class="copy-table" data-testid="copy-table">
          <thead>
            <tr><th>副本</th><th>书目</th><th>状态</th><th>版本</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="copy in copies" :key="copy.copyId">
              <td>{{ copy.copyNo }}</td>
              <td>{{ copy.titleId }}</td>
              <td>{{ copy.status }}</td>
              <td>{{ copy.version }}</td>
              <td>
                <el-button size="small" :data-testid="`copy-select-${copy.copyId}`" @click="selectCopy(copy)">选择</el-button>
              </td>
            </tr>
          </tbody>
        </table>
        <el-button v-if="nextCursor" data-testid="copy-load-more" @click="load(nextCursor)">加载更多</el-button>
      </template>
      <ErrorAlert v-if="listState.status === 'failure'" :failure="listState.failure" />
    </div>

    <ActionPanel title="副本办理" entry-id="copy-action" v-if="selected" :active-key="selectionRequest">
      <h2 data-testid="selected-copy">已选择：{{ selected.copyNo }}（{{ selected.status }} / 版本 {{ selected.version }}）</h2>
      <form data-testid="copy-action-form" @submit.prevent="submitAction">
        <select v-model="action" data-testid="copy-action" aria-label="副本动作">
          <option value="SHELVE">上架（恢复可借）</option>
          <option value="MAINTAIN">转入维护</option>
          <option value="RESTORE">维护完成</option>
          <option value="REMOVE">移除</option>
        </select>
        <el-button type="primary" native-type="submit" data-testid="copy-action-submit">提交动作</el-button>
      </form>
      <el-alert
        v-if="actionResult"
        type="success"
        :closable="false"
        data-testid="copy-action-success"
        :title="`副本现为 ${actionResult.status}（版本 ${actionResult.version}）`"
      />
      <ErrorAlert v-if="actionState.status === 'failure'" :failure="actionState.failure" />
    </ActionPanel>
  </section>
</template>

<script setup>
import ActionPanel from '@/components/ActionPanel.vue'
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { changeCopyState, createCopy, listCopies } from '../index'

const selectionRequest = ref(null)

const newCopyNo = ref('')
const createState = useApiRequest()
const { run: runCreate } = createState
const createResult = ref(null)

const copies = ref([])
const nextCursor = ref(null)
const filterTitleId = ref('')
const filterStatus = ref('')
const { state: listState, run: runList } = useApiRequest()

const selected = ref(null)
const action = ref('SHELVE')
const { state: actionState, run: runAction } = useApiRequest()
const actionResult = ref(null)

async function submitCreate() {
  const envelope = await runCreate(() => createCopy({ titleId: filterTitleId.value, copyNo: newCopyNo.value }))
  if (envelope) {
    createResult.value = envelope.data
    await load()
  }
}

async function load(cursor) {
  const envelope = await runList(() =>
    listCopies({ titleId: filterTitleId.value || undefined, status: filterStatus.value || undefined, cursor, limit: 20 }),
  )
  if (!envelope) return
  copies.value = cursor ? [...copies.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

function selectCopy(copy) {
  selectionRequest.value = (selectionRequest.value ?? 0) + 1
  selected.value = copy
  actionResult.value = null
}

async function submitAction() {
  const envelope = await runAction(() =>
    changeCopyState({ copyId: selected.value.copyId, action: action.value, version: selected.value.version }),
  )
  if (!envelope) return
  actionResult.value = envelope.data
  selected.value = envelope.data
  await load()
}
</script>

<style scoped>
.copy-admin-view {
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

.action-block form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.inline-input {
  min-width: 14rem;
}

.copy-table {
  width: 100%;
  border-collapse: collapse;
}

.copy-table th,
.copy-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}
</style>
