<template>
  <section class="catalog-admin-view">
    <h1>书目维护</h1>

    <div class="action-block">
      <h2>检索书目</h2>
      <form data-testid="admin-search-form" @submit.prevent="search">
        <el-input v-model="q" data-testid="admin-search-input" placeholder="按书名关键字检索" class="search-input" />
        <el-button native-type="submit" data-testid="admin-search-submit">检索</el-button>
      </form>
      <p v-if="searchState.status === 'loading'" data-testid="admin-search-loading">检索中…</p>
      <table v-else-if="results.length" class="result-table" data-testid="admin-search-results">
        <thead>
          <tr><th>书名</th><th>分类</th><th>可借</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="item in results" :key="item.titleId">
            <td>{{ item.title }}</td>
            <td>{{ item.category }}</td>
            <td>{{ item.availability }}</td>
            <td>
              <el-button size="small" :data-testid="`admin-select-${item.titleId}`" @click="selectTitle(item.titleId)">
                选择
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <ErrorAlert v-if="searchState.status === 'failure'" :failure="searchState.failure" />
    </div>

    <div class="action-block">
      <h2>修改书目</h2>
      <form v-if="edit" data-testid="update-form" @submit.prevent="submitUpdate">
        <el-form-item label="书名"><el-input v-model="edit.title" data-testid="update-title" /></el-form-item>
        <el-form-item label="作者（逗号分隔）"><el-input v-model="edit.authorsText" data-testid="update-authors" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="edit.category" data-testid="update-category" /></el-form-item>
        <el-form-item label="定价（元）">
          <el-input v-model.number="edit.priceYuan" type="number" step="0.01" min="0" data-testid="update-price" />
        </el-form-item>
        <p class="version-line">当前版本号：{{ edit.version }}（乐观锁，保存时提交）</p>
        <el-button type="primary" native-type="submit" data-testid="update-submit" :loading="updateState.status === 'loading'">
          保存修改
        </el-button>
        <el-button data-testid="remove-toggle" @click="removePanelVisible = !removePanelVisible">下架…</el-button>
      </form>
      <p v-else data-testid="update-empty">请先在上方检索并选择书目。</p>
      <ErrorAlert v-if="detailState.status === 'failure'" :failure="detailState.failure" />
      <el-alert
        v-if="updateResult"
        type="success"
        :closable="false"
        data-testid="update-success"
        :title="`已保存，新版本号 ${updateResult.version}`"
      />
      <el-alert
        v-if="versionConflict"
        type="warning"
        :closable="false"
        data-testid="version-conflict-hint"
        title="书目已被他人修改，请重新检索并选择后重试"
      />
      <ErrorAlert v-if="updateState.status === 'failure'" :failure="updateState.failure" />
      <form v-if="edit && removePanelVisible" class="remove-form" data-testid="remove-form" @submit.prevent="submitRemove">
        <el-input v-model="removeReason" data-testid="remove-reason" placeholder="下架原因（必填）" class="search-input" />
        <el-button type="danger" native-type="submit" data-testid="remove-submit">确认下架</el-button>
      </form>
      <el-alert v-if="removeResult" type="success" :closable="false" data-testid="remove-success" title="书目已下架" />
      <ErrorAlert v-if="removeState.status === 'failure'" :failure="removeState.failure" />
    </div>

    <div class="action-block">
      <h2>新增书目</h2>
      <form data-testid="create-form" @submit.prevent="submitCreate">
        <el-form-item label="ISBN"><el-input v-model="create.isbn" data-testid="create-isbn" /></el-form-item>
        <el-form-item label="书名"><el-input v-model="create.title" data-testid="create-title" /></el-form-item>
        <el-form-item label="作者（逗号分隔）"><el-input v-model="create.authorsText" data-testid="create-authors" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="create.category" data-testid="create-category" /></el-form-item>
        <el-form-item label="定价（元）">
          <el-input v-model.number="create.priceYuan" type="number" step="0.01" min="0" data-testid="create-price" />
        </el-form-item>
        <el-button type="primary" native-type="submit" data-testid="create-submit" :loading="createState.status === 'loading'">
          新增
        </el-button>
      </form>
      <el-alert
        v-if="createResult"
        type="success"
        :closable="false"
        data-testid="create-success"
        :title="`已新增书目 ${createResult.titleId}`"
      />
      <ErrorAlert v-if="createState.status === 'failure'" :failure="createState.failure" />
    </div>
  </section>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { createTitle, getTitle, removeTitle, searchTitles, updateTitle } from '../index'

const q = ref('')
const results = ref([])
const { state: searchState, run: runSearch } = useApiRequest()
const { state: detailState, run: runDetail } = useApiRequest()
const { state: updateState, run: runUpdate } = useApiRequest()
const { state: createState, run: runCreate } = useApiRequest()
const { state: removeState, run: runRemove } = useApiRequest()

const edit = ref(null)
const updateResult = ref(null)
const removePanelVisible = ref(false)
const removeReason = ref('')
const removeResult = ref(null)
const create = reactive({ isbn: '', title: '', authorsText: '', category: '', priceYuan: null })
const createResult = ref(null)

const versionConflict = computed(
  () => updateState.status === 'failure' && updateState.failure?.code === 'MLS-CAT-003',
)

function toCents(priceYuan) {
  return Math.round(Number(priceYuan) * 100)
}

function splitAuthors(text) {
  return String(text)
    .split(/[,，]/)
    .map((author) => author.trim())
    .filter(Boolean)
}

async function search() {
  const envelope = await runSearch(() => searchTitles({ q: q.value || undefined, limit: 20 }))
  if (envelope) results.value = envelope.data.items
}

async function selectTitle(titleId) {
  const detail = await runDetail(() => getTitle({ titleId }))
  if (detail) {
    const data = detail.data
    edit.value = {
      titleId: data.titleId,
      title: data.title,
      authorsText: data.authors.join(','),
      category: data.category,
      publisher: data.publisher,
      publishYear: data.publishYear,
      priceYuan: data.priceCents / 100,
      version: data.version,
    }
    updateResult.value = null
    removeResult.value = null
    removePanelVisible.value = false
  }
}

async function submitUpdate() {
  const form = edit.value
  const envelope = await runUpdate(() =>
    updateTitle({
      titleId: form.titleId,
      version: form.version,
      title: form.title,
      authors: splitAuthors(form.authorsText),
      category: form.category,
      priceCents: toCents(form.priceYuan),
    }),
  )
  if (!envelope) return
  updateResult.value = envelope.data
  edit.value.version = envelope.data.version
  edit.value.title = envelope.data.title
}

async function submitRemove() {
  const envelope = await runRemove(() =>
    removeTitle({ titleId: edit.value.titleId, version: edit.value.version, reason: removeReason.value }),
  )
  if (!envelope) return
  removeResult.value = envelope.data
  edit.value = null
}

async function submitCreate() {
  const envelope = await runCreate(() =>
    createTitle({
      isbn: create.isbn,
      title: create.title,
      authors: splitAuthors(create.authorsText),
      category: create.category,
      priceCents: toCents(create.priceYuan),
    }),
  )
  if (envelope) createResult.value = envelope.data
}
</script>

<style scoped>
.catalog-admin-view {
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

.action-block .el-form-item {
  margin: 0;
  min-width: 18rem;
}

.search-input {
  min-width: 16rem;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
}

.result-table th,
.result-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}

.version-line {
  color: #526274;
  font-size: 0.85rem;
}
</style>
