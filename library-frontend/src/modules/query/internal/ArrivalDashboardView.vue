<template>
  <section class="arrival-dashboard-view">
    <h1>到馆看板</h1>
    <form class="filters" data-testid="dashboard-filter" @submit.prevent="load">
      <el-input v-model="businessDate" data-testid="dashboard-date" placeholder="营业日（YYYY-MM-DD）" class="date-input" />
      <el-button type="primary" native-type="submit" data-testid="dashboard-submit">查询</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="dashboard-loading">加载中…</p>
    <template v-else-if="state.status === 'success' && dashboard">
      <dl class="dashboard-grid" data-testid="dashboard-grid">
        <div><dt>应到</dt><dd data-testid="dashboard-expected">{{ dashboard.expected }}</dd></div>
        <div><dt>已到</dt><dd data-testid="dashboard-arrived">{{ dashboard.arrived }}</dd></div>
        <div><dt>待取</dt><dd data-testid="dashboard-pending">{{ dashboard.pending }}</dd></div>
        <div><dt>异常</dt><dd data-testid="dashboard-exceptions">{{ dashboard.exceptions }}</dd></div>
      </dl>
      <p class="date-line">营业日：{{ dashboard.businessDate }}</p>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getArrivalDashboard } from '../index'

const businessDate = ref('')
const dashboard = ref(null)
const { state, run } = useApiRequest()

async function load() {
  const envelope = await run(() => getArrivalDashboard({ businessDate: businessDate.value || undefined }))
  if (envelope) dashboard.value = envelope.data
}
</script>

<style scoped>
.arrival-dashboard-view {
  max-width: 40rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.filters {
  display: flex;
  gap: 0.75rem;
}

.date-input {
  min-width: 14rem;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin: 0;
}

.dashboard-grid div {
  border: 1px solid #e2eaf0;
  border-radius: 0.5rem;
  padding: 0.75rem;
  text-align: center;
}

.dashboard-grid dt {
  color: #526274;
  font-size: 0.85rem;
}

.dashboard-grid dd {
  margin: 0.25rem 0 0;
  font-size: 1.5rem;
  font-weight: 700;
}

.date-line {
  color: #526274;
}
</style>
