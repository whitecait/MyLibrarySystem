<template>
  <section class="closing-admin-view">
    <h1>闭馆与恢复</h1>

    <ActionPanel title="重开营业日" entry-id="closing-reopen" description="闭馆批次追平后，恢复指定营业日。">
      <h2>重开营业日</h2>
      <p class="note">
        闭馆批次由系统在计划钟点触发；隔离期内所有写操作会立即收到明确失败（而非无限等待）。
        批次未追平（存在未补结的不完整批次）时重开会被拒绝。
      </p>
      <form data-testid="reopen-form" @submit.prevent="submitReopen">
        <el-input v-model="businessDate" data-testid="reopen-date" placeholder="营业日（YYYY-MM-DD）" class="date-input" />
        <el-button type="primary" native-type="submit" data-testid="reopen-submit" :loading="state.status === 'loading'">
          重开该营业日
        </el-button>
      </form>
      <el-alert
        v-if="result"
        type="success"
        :closable="false"
        data-testid="reopen-success"
        :title="`营业日已重开（状态 ${result.status}），新纪元 epoch：${result.epoch}`"
        description="重开后该营业日的新写入以此 epoch 为准；旧请求不会自动重放。"
      />
      <el-alert
        v-if="notCaughtUp"
        type="warning"
        :closable="false"
        data-testid="reopen-blocked"
        title="闭馆批次尚未追平，不能开放该营业日"
        description="请先完成失败批次的补结，再尝试重开。"
      />
      <ErrorAlert v-if="state.status === 'failure' && !notCaughtUp" :failure="state.failure" />
    </ActionPanel>
  </section>
</template>

<script setup>
import ActionPanel from '@/components/ActionPanel.vue'
import { computed, ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { reopenBusinessDate } from '../index'

const businessDate = ref('')
const result = ref(null)
const { state, run } = useApiRequest()

const notCaughtUp = computed(
  () =>
    state.status === 'failure' &&
    (state.failure?.code === 'MLS-CIRC-008' || state.failure?.code === 'MLS-CIRC-013'),
)

async function submitReopen() {
  result.value = null
  const envelope = await run(() => reopenBusinessDate({ businessDate: businessDate.value }))
  if (envelope) result.value = envelope.data
}
</script>

<style scoped>
.closing-admin-view {
  max-width: 44rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.action-block {
  border: 1px solid #e2eaf0;
  border-radius: 0.5rem;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.action-block form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.date-input {
  min-width: 14rem;
}

.note {
  color: #526274;
  font-size: 0.85rem;
}
</style>
