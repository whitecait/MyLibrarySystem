<template>
  <section class="reservation-calendar-view">
    <h1>预约日历</h1>
    <form class="range-form" data-testid="calendar-form" @submit.prevent="load">
      <el-input v-model="from" data-testid="calendar-from" placeholder="开始日（YYYY-MM-DD）" class="date-input" />
      <el-input v-model="to" data-testid="calendar-to" placeholder="结束日（YYYY-MM-DD）" class="date-input" />
      <el-button type="primary" native-type="submit" data-testid="calendar-submit">查询</el-button>
    </form>

    <p v-if="state.status === 'loading'" data-testid="calendar-loading">加载中…</p>
    <template v-else-if="state.status === 'success'">
      <table class="calendar-table" data-testid="calendar-table">
        <thead>
          <tr><th>营业日</th><th>预约保障</th></tr>
        </thead>
        <tbody>
          <tr v-for="day in days" :key="day.businessDate">
            <td>{{ day.businessDate }}</td>
            <td>
              <el-tag :type="tagType(day.availability)" :data-testid="`calendar-day-${day.businessDate}`">
                {{ label(day.availability) }}
              </el-tag>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
    <p><RouterLink :to="`/titles/${titleId}`">返回书目详情</RouterLink></p>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getReservationCalendar } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })

const from = ref('')
const to = ref('')
const days = ref([])
const { state, run } = useApiRequest()

const LABELS = { GUARANTEED: '有保障', NOT_GUARANTEED: '不保障', UNAVAILABLE: '不可预约' }
const TAG_TYPES = { GUARANTEED: 'success', NOT_GUARANTEED: 'warning', UNAVAILABLE: 'danger' }
function label(availability) {
  return LABELS[availability] ?? availability
}
function tagType(availability) {
  return TAG_TYPES[availability] ?? 'info'
}

async function load() {
  const envelope = await run(() => getReservationCalendar({ titleId: props.titleId, from: from.value, to: to.value }))
  if (envelope) days.value = envelope.data.items
}
</script>

<style scoped>
.reservation-calendar-view {
  max-width: 40rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.range-form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.date-input {
  min-width: 14rem;
}

.calendar-table {
  width: 100%;
  border-collapse: collapse;
}

.calendar-table th,
.calendar-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}
</style>
