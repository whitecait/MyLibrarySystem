<template>
  <section class="title-borrow-view">
    <p v-if="state.status === 'loading'" role="status">加载中…</p>
    <el-empty v-else-if="notFound" data-testid="borrow-title-not-found" description="书目不存在或不可见" />
    <template v-else-if="state.status === 'success' && detail">
      <header class="borrow-intro">
        <p>线上借阅</p>
        <h1 data-testid="borrow-title-name">{{ detail.title }}</h1>
        <span>选择预约日期与取书时段；日历直接显示馆藏状态。</span>
      </header>
      <div class="borrowing-tools">
        <ReservePanel :title-id="titleId" @reserved="calendarRefreshKey++" />
        <ReservationCalendarView :title-id="titleId" :refresh-key="calendarRefreshKey" />
      </div>
    </template>
    <ErrorAlert v-if="state.status === 'failure' && !notFound" :failure="state.failure" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind, useApiRequest } from '@/platform/http'
import { getTitle } from '@/modules/catalog'
import ReservePanel from './ReservePanel.vue'
import ReservationCalendarView from './ReservationCalendarView.vue'

const props = defineProps({ titleId: { type: String, required: true } })
const detail = ref(null)
const calendarRefreshKey = ref(0)
const { state, run } = useApiRequest()
const notFound = computed(() => state.status === 'failure' && state.failure?.kind === FailureKind.NOT_FOUND)

onMounted(async () => {
  const envelope = await run(() => getTitle({ titleId: props.titleId }))
  if (envelope) detail.value = envelope.data
})
</script>

<style scoped>
.title-borrow-view { min-width: 0; color: #273128; container: title-borrow / inline-size; }
.borrow-intro { margin-bottom: 24px; }
.borrow-intro p { margin: 0 0 10px; color: #68715f; font-size: 12px; }
.borrow-intro h1 { margin: 0 0 12px; font-size: 24px; line-height: 1.5; font-weight: 500; overflow-wrap: anywhere; }
.borrow-intro span { color: #68715f; font-size: 12px; line-height: 1.8; }
.borrowing-tools { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: 18px; align-items: start; }
@container title-borrow (max-width: 580px) { .borrowing-tools { grid-template-columns: minmax(0, 1fr); } }
</style>
