<template>
  <div class="reserve-panel">
    <header class="reserve-heading">
      <span class="reserve-mark" aria-hidden="true">↗</span>
      <div><h2>线上预约</h2><p>选择预约日期与取书时段</p></div>
    </header>
    <form data-testid="reserve-form" @submit.prevent="onSubmit">
      <div class="reserve-field">
        <label :for="dateInputId">预约日期</label>
        <el-input :id="dateInputId" v-model="reservationDate" type="date" required data-testid="reserve-date" class="inline-input" />
      </div>
      <div class="reserve-field">
        <label :for="slotInputId">取书时段</label>
        <select :id="slotInputId" v-model="pickupSlot" class="slot-input" required data-testid="reserve-slot" aria-label="取书时段">
          <option disabled value="">请选择时段</option>
          <option v-for="slot in PICKUP_SLOTS" :key="slot" :value="slot">{{ slot }}</option>
        </select>
      </div>
      <el-button class="reserve-submit" type="primary" native-type="submit" data-testid="reserve-submit" :loading="state.status === 'loading'">
        提交线上预约
      </el-button>
    </form>

    <template v-if="result">
      <el-alert
        v-if="result.outcome === 'CONFIRMED'"
        type="success"
        :closable="false"
        data-testid="reserve-confirmed"
        :title="`预约成功，交易号 ${result.txId}`"
      />
      <template v-else-if="result.outcome === 'WAITLIST_PENDING_CONFIRMATION'">
        <el-alert
          type="warning"
          :closable="false"
          data-testid="reserve-waitlist-pending"
          title="当前进入候补，需要你确认后方可成立（确认前不产生业务事实）"
        />
        <el-button type="primary" data-testid="reserve-confirm" :loading="state.status === 'loading'" @click="confirmWaitlist">
          确认候补预约
        </el-button>
      </template>
      <el-alert
        v-else-if="result.outcome === 'WAITLIST_ENQUEUED'"
        type="info"
        :closable="false"
        data-testid="reserve-waitlist-enqueued"
        title="已进入候补队列，候补到时将通知你确认"
      />
      <el-alert
        v-else-if="result.outcome === 'MANUAL_HANDLING'"
        type="info"
        :closable="false"
        data-testid="reserve-manual"
        title="该预约转人工处理，请留意通知"
      />
      <template v-else-if="result.outcome === 'REJECTED'">
        <el-alert
          type="error"
          :closable="false"
          data-testid="reserve-rejected"
          title="预约被拒绝"
          :description="(result.reasons ?? []).join('；')"
        />
      </template>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
    <p class="note">预约提交以服务端判定为准；书目可借状态仅为展示。</p>
  </div>
</template>

<script setup>
import { ref, useId } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { PICKUP_SLOTS, reserveTitle } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })

const reservationDate = ref('')
const pickupSlot = ref('')
const fieldId = useId()
const dateInputId = fieldId + '-date'
const slotInputId = fieldId + '-slot'
const result = ref(null)
const { state, run } = useApiRequest()

async function submit(confirm = false) {
  if (!reservationDate.value || !PICKUP_SLOTS.includes(pickupSlot.value)) return

  const envelope = await run(() =>
    reserveTitle({
      titleId: props.titleId,
      reservationDate: reservationDate.value,
      pickupSlot: pickupSlot.value,
      channel: 'ONLINE',
      confirmWaitlist: confirm,
    }),
  )
  if (envelope) result.value = envelope.data
}

function confirmWaitlist() {
  result.value = null
  submit(true)
}

function onSubmit() {
  result.value = null
  submit(false)
}
</script>

<style scoped>
.reserve-panel { border: 1px solid var(--detail-line, #dbe4ee); border-radius: 16px; padding: 20px; background: var(--detail-surface, #f7f9fd); }
.reserve-heading { display: flex; gap: 12px; align-items: center; margin-bottom: 20px; }
.reserve-mark { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: var(--detail-tint, #edf2ff); color: var(--detail-accent, #3458c8); font-size: 22px; }
h2 { margin: 0 0 5px; font-size: 16px; }
.reserve-heading p { margin: 0; color: var(--detail-muted, #526274); font-size: 12px; }
.reserve-panel form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; align-items: end; margin-bottom: 16px; }
.reserve-field { display: flex; flex-direction: column; gap: 8px; min-width: 0; color: var(--detail-muted, #526274); font-size: 12px; }
.inline-input, .slot-input { min-width: 0; width: 100%; }
.slot-input { min-height: 40px; border-color: var(--detail-line, #dbe4ee); color: var(--detail-ink, #192238); }
.reserve-panel :deep(.el-input__wrapper) { min-height: 40px; box-shadow: 0 0 0 1px var(--detail-line, #dbe4ee) inset; border-radius: 9px; }
.reserve-submit { grid-column: 1 / -1; min-height: 42px; margin: 0; background: var(--detail-accent, #3458c8); border-color: var(--detail-accent, #3458c8); border-radius: 9px; }
.reserve-submit:hover, .reserve-submit:focus-visible { background: var(--detail-accent, #3458c8); border-color: var(--detail-accent, #3458c8); filter: brightness(1.08); }
.note { margin: 14px 0 0; color: var(--detail-muted, #526274); font-size: 11px; line-height: 1.8; }
@container title-detail (max-width: 370px) { .reserve-panel form { grid-template-columns: minmax(0, 1fr); } }
</style>
