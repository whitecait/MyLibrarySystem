<template>
  <section class="payment-simulator-view">
    <h1>模拟支付调度（受控测试操作员）</h1>
    <p class="note">仅为联调与测试提供模拟结果调度；正式支付回调由模拟器凭证发起，无页面入口。</p>
    <form data-testid="simulator-form" @submit.prevent="submit">
      <el-input v-model="orderId" data-testid="simulator-order-id" placeholder="支付单 ID" class="inline-input" />
      <select v-model="result" data-testid="simulator-result" aria-label="模拟结果">
        <option value="SUCCESS">成功</option>
        <option value="FAILURE">失败</option>
      </select>
      <el-input v-model.number="delayMs" data-testid="simulator-delay" placeholder="延迟毫秒（可选）" class="amount-input" />
      <el-input
        v-model.number="duplicateCount"
        data-testid="simulator-duplicate"
        placeholder="重复次数（可选，0-20）"
        class="amount-input"
      />
      <el-button type="primary" native-type="submit" data-testid="simulator-submit" :loading="state.status === 'loading'">
        调度
      </el-button>
    </form>
    <el-alert
      v-if="resultData"
      type="success"
      :closable="false"
      data-testid="simulator-scheduled"
      :title="`已调度模拟 ${resultData.simulationId}（${resultData.status}）`"
    />
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { scheduleSimulatedPayment } from '../index'

const orderId = ref('')
const result = ref('SUCCESS')
const delayMs = ref('')
const duplicateCount = ref('')
const resultData = ref(null)
const { state, run } = useApiRequest()

async function submit() {
  const envelope = await run(() =>
    scheduleSimulatedPayment({
      orderId: orderId.value,
      result: result.value,
      delayMs: delayMs.value === '' ? undefined : Number(delayMs.value),
      duplicateCount: duplicateCount.value === '' ? undefined : Number(duplicateCount.value),
    }),
  )
  if (envelope) resultData.value = envelope.data
}
</script>

<style scoped>
.payment-simulator-view {
  max-width: 52rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.inline-input {
  min-width: 14rem;
}

.amount-input {
  width: 12rem;
}

.note {
  color: #526274;
  font-size: 0.85rem;
}
</style>
