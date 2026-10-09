<template>
  <section class="reservation-calendar-view">
    <h2>预约日历</h2>
    <p class="calendar-note">查询日期范围内的预约保障</p>
    <form class="range-form" data-testid="calendar-form" @submit.prevent="load">
      <div class="calendar-field">
        <label :for="fieldId + '-from'">开始日</label>
        <el-input :id="fieldId + '-from'" v-model="from" type="date" required data-testid="calendar-from" class="date-input" />
      </div>
      <div class="calendar-field">
        <label :for="fieldId + '-to'">结束日</label>
        <el-input :id="fieldId + '-to'" v-model="to" type="date" required data-testid="calendar-to" class="date-input" />
      </div>
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
  </section>
</template>

<script setup>
import { ref, useId } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getReservationCalendar } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })

const fieldId = useId()
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
.reservation-calendar-view { min-width: 0; border: 1px solid #e2e6d9; border-radius: 16px; padding: 20px; background: #fafbf6; }
.reservation-calendar-view h2 { margin: 0 0 5px; font-size: 16px; }
.calendar-note { margin: 0 0 20px; font-size: 12px; color: #68715f; line-height: 1.8; }
.reservation-calendar-view .range-form { display: flex; flex-direction: column; align-items: stretch; gap: 14px; }
.calendar-field { display: flex; flex-direction: column; gap: 8px; min-width: 0; color: #68715f; font-size: 12px; }
.date-input { min-width: 0; width: 100%; }
.reservation-calendar-view :deep(.el-input__wrapper) { min-height: 40px; box-shadow: 0 0 0 1px #e2e6d9 inset; border-radius: 9px; }
.range-form .el-button { min-height: 42px; margin: 0; color: #52634a; border-color: #d9dfcf; border-radius: 9px; background: #fff; }
.calendar-table { width: 100%; margin-top: 20px; border-collapse: collapse; font-size: 12px; }
.calendar-table th, .calendar-table td { text-align: left; padding: 8px 0; border-bottom: 1px solid #e2e6d9; }
.calendar-table th { color: #68715f; font-weight: 500; }
</style>
