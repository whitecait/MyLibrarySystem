<template>
  <section class="reconciliation-view">
    <h1>营业日对账</h1>
    <form data-testid="reconcile-form" @submit.prevent="submit">
      <el-input v-model="businessDate" data-testid="reconcile-date" placeholder="营业日（YYYY-MM-DD）" class="date-input" />
      <el-button type="primary" native-type="submit" data-testid="reconcile-submit" :loading="state.status === 'loading'">
        发起对账
      </el-button>
    </form>
    <template v-if="result">
      <el-alert
        type="success"
        :closable="false"
        data-testid="reconcile-success"
        :title="`对账完成：${result.reconciliationId}`"
      />
      <dl class="counts" data-testid="reconcile-counts">
        <div v-for="(value, key) in result.counts" :key="key">
          <dt>{{ key }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <el-alert
        v-if="result.exceptions.length"
        type="warning"
        :closable="false"
        data-testid="reconcile-exceptions"
        :title="`发现 ${result.exceptions.length} 项异常`"
        :description="result.exceptions.join('；')"
      />
      <el-alert v-else type="info" :closable="false" data-testid="reconcile-clean" title="无异常项" />
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { reconcileBusinessDate } from '../index'

const businessDate = ref('')
const result = ref(null)
const { state, run } = useApiRequest()

async function submit() {
  const envelope = await run(() => reconcileBusinessDate({ businessDate: businessDate.value }))
  if (envelope) result.value = envelope.data
}
</script>

<style scoped>
.reconciliation-view {
  max-width: 40rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

form {
  display: flex;
  gap: 0.75rem;
}

.date-input {
  min-width: 14rem;
}

.counts {
  display: flex;
  gap: 1rem;
  margin: 0;
}

.counts div {
  border: 1px solid #e2eaf0;
  border-radius: 0.5rem;
  padding: 0.5rem 1rem;
  text-align: center;
}

.counts dt {
  color: #526274;
  font-size: 0.85rem;
}

.counts dd {
  margin: 0.25rem 0 0;
  font-weight: 700;
}
</style>
