<template>
  <div class="reserve-panel">
    <h2>预约此书</h2>
    <form data-testid="reserve-form" @submit.prevent="onSubmit">
      <el-input v-model="reservationDate" data-testid="reserve-date" placeholder="预约日期（YYYY-MM-DD）" class="inline-input" />
      <select v-model="pickupSlot" data-testid="reserve-slot" aria-label="取书时段">
        <option value="AM">上午</option>
        <option value="PM">下午</option>
      </select>
      <select v-model="channel" data-testid="reserve-channel" aria-label="预约渠道">
        <option value="ONLINE">线上</option>
        <option value="DESK">到台</option>
      </select>
      <el-button type="primary" native-type="submit" data-testid="reserve-submit" :loading="state.status === 'loading'">
        提交预约
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
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { reserveTitle } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })

const reservationDate = ref('')
const pickupSlot = ref('AM')
const channel = ref('ONLINE')
const result = ref(null)
const { state, run } = useApiRequest()

async function submit(confirm = false) {
  const envelope = await run(() =>
    reserveTitle({
      titleId: props.titleId,
      reservationDate: reservationDate.value,
      pickupSlot: pickupSlot.value,
      channel: channel.value,
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
.reserve-panel {
  border: 1px solid #e2eaf0;
  border-radius: 0.5rem;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.reserve-panel form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.inline-input {
  min-width: 14rem;
}

.note {
  color: #526274;
  font-size: 0.85rem;
}
</style>
