<template>
  <section class="portal-view">
    <h1>书目检索</h1>
    <form class="search-bar" data-testid="portal-search-form" @submit.prevent="onSearch">
      <el-input
        v-model="q"
        data-testid="portal-search-input"
        placeholder="按书名关键字检索"
        clearable
        @clear="onSearch"
      />
      <el-button type="primary" native-type="submit" data-testid="portal-search-submit">检索</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="portal-loading">检索中…</p>
    <el-empty
      v-else-if="state.status === 'success' && titles.length === 0"
      data-testid="portal-empty"
      description="没有找到相关书目"
    />
    <template v-else-if="state.status === 'success'">
      <table class="title-table" data-testid="portal-results">
        <thead>
          <tr>
            <th>书名</th>
            <th>作者</th>
            <th>分类</th>
            <th>可借状态</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in titles" :key="item.titleId">
            <td>{{ item.title }}</td>
            <td>{{ (item.authors ?? []).join('、') }}</td>
            <td>{{ item.category }}</td>
            <td><el-tag :type="availabilityTagType(item.availability)">{{ availabilityLabel(item.availability) }}</el-tag></td>
            <td><RouterLink :to="`/titles/${item.titleId}`" :data-testid="`portal-title-link-${item.titleId}`">详情</RouterLink></td>
          </tr>
        </tbody>
      </table>
      <el-button v-if="nextCursor" data-testid="portal-load-more" @click="loadMore">加载更多</el-button>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { searchTitles } from '../index'

const q = ref('')
const titles = ref([])
const nextCursor = ref(null)
const { state, run } = useApiRequest()

const AVAILABILITY_LABELS = { AVAILABLE: '可借', UNAVAILABLE: '不可借', UNKNOWN: '未知' }
const AVAILABILITY_TAG_TYPES = { AVAILABLE: 'success', UNAVAILABLE: 'danger', UNKNOWN: 'info' }
function availabilityLabel(availability) {
  return AVAILABILITY_LABELS[availability] ?? availability
}
function availabilityTagType(availability) {
  return AVAILABILITY_TAG_TYPES[availability] ?? 'info'
}

async function onSearch() {
  const envelope = await run(() => searchTitles({ q: q.value || undefined, limit: 20 }))
  if (!envelope) return
  titles.value = envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

async function loadMore() {
  const envelope = await run(() => searchTitles({ q: q.value || undefined, limit: 20, cursor: nextCursor.value }))
  if (!envelope) return
  titles.value = [...titles.value, ...envelope.data.items]
  nextCursor.value = envelope.data.nextCursor
}
</script>

<style scoped>
.portal-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.search-bar {
  display: flex;
  gap: 0.75rem;
}

.title-table {
  width: 100%;
  border-collapse: collapse;
}

.title-table th,
.title-table td {
  text-align: left;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e2eaf0;
}
</style>
