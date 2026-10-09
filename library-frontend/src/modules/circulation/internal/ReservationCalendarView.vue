<template>
  <section class="reservation-calendar-view" aria-labelledby="calendar-heading">
    <header class="calendar-heading">
      <h2 id="calendar-heading">预约日历</h2>
      <button type="button" class="calendar-refresh" aria-label="刷新预约日历" @click="load">刷新</button>
    </header>
    <div class="calendar-month">
      <button type="button" aria-label="上个月" @click="move(-1)">‹</button>
      <strong data-testid="calendar-month">{{ month.replace('-', '年') }}月</strong>
      <button type="button" aria-label="下个月" @click="move(1)">›</button>
    </div>
    <ul class="calendar-legend" aria-label="日历颜色说明">
      <li v-for="(label, status) in LABELS" :key="status"><i :class="status.toLowerCase()" aria-hidden="true"></i>{{ label }}</li>
    </ul>
    <p v-if="state.status === 'loading'" role="status" data-testid="calendar-loading">加载中…</p>
    <table class="calendar-grid" data-testid="calendar-grid" :aria-busy="state.status === 'loading'">
      <caption class="calendar-sr-only">{{ month }} 馆藏状态</caption>
      <thead><tr><th v-for="weekday in WEEKDAYS" :key="weekday" scope="col">{{ weekday }}</th></tr></thead>
      <tbody>
        <tr v-for="(week, index) in range.weeks" :key="index">
          <td v-for="(date, column) in week" :key="column">
            <div v-if="date" :data-date="date" :class="['calendar-day', dayClass(date)]" :aria-label="`${date} ${dayLabel(date)}`">
              <span>{{ Number(date.slice(-2)) }}</span>
              <small v-if="date >= today">{{ LABELS[dayState(date)] ?? '—' }}</small>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="calendar-note">按当前馆藏显示，借出状态在实际归还后更新；无色日期暂无可用状态。预约结果以提交时判定为准。</p>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { getReservationCalendar } from '../index'
import { calendarMonth, moveCalendarMonth } from './calendarMonth'
import { getLibraryDate } from './reservationDefaults'

const props = defineProps({ titleId: { type: String, required: true }, refreshKey: { type: Number, default: 0 } })
const today = getLibraryDate()
const month = ref(today.slice(0, 7))
const range = computed(() => calendarMonth(month.value))
const days = ref([])
const dayByDate = computed(() => new Map(days.value.map(day => [day.businessDate, day])))
const state = shallowRef({ status: 'idle', failure: null })
const LABELS = { AVAILABLE: '可借阅', RESERVED: '已预定', LENT: '已借出' }
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
let requestId = 0

function dayState(date) { return date >= today ? dayByDate.value.get(date)?.displayState : null }
function dayClass(date) { return date < today ? 'past' : (dayState(date)?.toLowerCase() ?? '') }
function dayLabel(date) { return date < today ? '过去日期' : (LABELS[dayState(date)] ?? '暂无可用状态') }
function move(offset) { month.value = moveCalendarMonth(month.value, offset); void load() }

async function load() {
  const id = ++requestId
  state.value = { status: 'loading', failure: null }
  days.value = []
  // Each request owns its failure state; a late response must not replace the new month.
  const { state: requestState, run } = useApiRequest()
  const envelope = await run(() => getReservationCalendar({ titleId: props.titleId, from: range.value.from, to: range.value.to }))
  if (id !== requestId) return
  state.value = requestState
  if (envelope) days.value = envelope.data.items
}
onMounted(load)
watch(() => [props.titleId, props.refreshKey], load)
onBeforeUnmount(() => { requestId++ })
</script>

<style scoped>
.reservation-calendar-view { min-width: 0; border: 1px solid #e2e6d9; border-radius: 16px; padding: 16px; background: #fafbf6; }
.calendar-heading { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.calendar-heading h2 { margin: 0; font-size: 16px; }
.calendar-refresh { border: 0; background: none; color: #52634a; font-size: 11px; cursor: pointer; }
.calendar-month { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 14px; }
.calendar-month strong { font-size: 14px; font-weight: 500; }
.calendar-month button { width: 28px; height: 28px; border: 1px solid #e2e6d9; border-radius: 7px; color: #52634a; background: #fff; font-size: 20px; cursor: pointer; }
.calendar-legend { display: flex; flex-wrap: wrap; gap: 8px; padding: 0; margin: 0 0 12px; list-style: none; font-size: 10px; }
.calendar-legend li { display: flex; align-items: center; gap: 4px; }
.calendar-legend i { width: 8px; height: 8px; border-radius: 50%; }
.calendar-legend i.available { background: #60844e; }
.calendar-legend i.reserved { background: #5379b4; }
.calendar-legend i.lent { background: #b75e4f; }
.calendar-grid { width: 100%; table-layout: fixed; border-spacing: 3px; font-size: 12px; }
.calendar-grid th { padding-bottom: 8px; color: #68715f; font-size: 11px; font-weight: 400; }
.calendar-grid td { padding: 0; }
.calendar-day { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; min-height: 48px; border-radius: 6px; background: #f0f1ec; color: #68715f; }
.calendar-day small { font-size: 9px; white-space: nowrap; }
.calendar-day.available { background: #e3eedb; color: #36552b; }
.calendar-day.reserved { background: #e1eafa; color: #2e5083; }
.calendar-day.lent { background: #f7e2dd; color: #8a4034; }
.calendar-day.past { background: transparent; color: #9ca296; }
.calendar-note { margin: 12px 0 0; font-size: 10px; color: #68715f; line-height: 1.8; }
.calendar-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
button:focus-visible { outline: 3px solid #90a9ff; outline-offset: 2px; }
</style>
