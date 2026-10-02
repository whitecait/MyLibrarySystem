<template>
  <section class="audit-query-view">
    <h1>审计查询</h1>
    <form class="filters" data-testid="audit-filter" @submit.prevent="load()">
      <el-input v-model="relatedId" data-testid="audit-related-id" placeholder="关联对象 ID" class="inline-input" />
      <el-input v-model="actorId" data-testid="audit-actor-id" placeholder="操作者 ID" class="inline-input" />
      <el-input v-model="eventType" data-testid="audit-event-type" placeholder="事件类型（如 ORDER_PAID）" class="inline-input" />
      <el-button native-type="submit" data-testid="audit-submit">查询</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="audit-loading">加载中…</p>
    <el-empty
      v-else-if="state.status === 'success' && events.length === 0"
      data-testid="audit-empty"
      description="无匹配审计事件"
    />
    <template v-else-if="state.status === 'success'">
      <div class="audit-table-scroll" data-testid="audit-table-scroll" role="region" tabindex="0" aria-label="审计记录（可横向滚动）">
        <table class="audit-table" data-testid="audit-table">
          <thead>
            <tr><th>事件</th><th>类型</th><th>操作者</th><th>关联对象</th><th>结果</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="event in events" :key="event.eventId">
              <td>{{ event.eventId }}</td>
              <td>{{ event.eventType }}</td>
              <td>{{ event.actorId }}</td>
              <td>{{ event.relatedId ?? '—' }}</td>
              <td>{{ event.outcome ?? '—' }}</td>
              <td>{{ event.occurredAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <el-button v-if="nextCursor" data-testid="audit-load-more" @click="load(nextCursor)">加载更多</el-button>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { listAuditEvents } from '../index'

const events = ref([])
const nextCursor = ref(null)
const relatedId = ref('')
const actorId = ref('')
const eventType = ref('')
const { state, run } = useApiRequest()

async function load(cursor) {
  const envelope = await run(() =>
    listAuditEvents({
      relatedId: relatedId.value || undefined,
      actorId: actorId.value || undefined,
      eventType: eventType.value || undefined,
      cursor,
      limit: 20,
    }),
  )
  if (!envelope) return
  events.value = cursor ? [...events.value, ...envelope.data.items] : envelope.data.items
  nextCursor.value = envelope.data.nextCursor
}

load()
</script>

<style scoped>
.audit-query-view {
  max-width: 60rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.filters {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.inline-input {
  min-width: 12rem;
}

.audit-table-scroll {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overscroll-behavior-x: contain;
}

.audit-table {
  width: max-content;
  min-width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
}

.audit-table th,
.audit-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}
</style>
