<template>
  <section class="walk-in-loans-view">
    <h1>现场借阅</h1>
    <p>逐册提交借书意向；每一册独立判定，混合成败按册保留，副本可借状态不构成提交保证。</p>
    <form data-testid="walkin-form" @submit.prevent="submit">
      <select v-model="channel" data-testid="walkin-channel" aria-label="借阅渠道">
        <option value="SELF_SERVICE">自助</option>
        <option value="DESK">到台（馆员代借）</option>
      </select>
      <el-input
        v-if="channel === 'DESK'"
        v-model="readerId"
        data-testid="walkin-reader-id"
        placeholder="读者 ID"
        class="inline-input"
      />
      <div v-for="(item, index) in items" :key="index" class="item-row">
        <el-input
          v-model="item.titleId"
          :data-testid="`walkin-title-${index}`"
          placeholder="书目 ID"
          class="id-input"
        />
        <el-input
          v-model="item.copyId"
          :data-testid="`walkin-copy-${index}`"
          placeholder="副本 ID"
          class="id-input"
        />
        <el-button
          v-if="items.length > 1"
          size="small"
          :data-testid="`walkin-remove-${index}`"
          @click="items.splice(index, 1)"
        >
          移除
        </el-button>
      </div>
      <el-button size="small" data-testid="walkin-add" @click="items.push({ titleId: '', copyId: '' })">再加一册</el-button>
      <el-button type="primary" native-type="submit" data-testid="walkin-submit" :loading="state.status === 'loading'">
        提交借阅
      </el-button>
    </form>

    <template v-if="state.status === 'success' && batch">
      <table class="batch-table" data-testid="walkin-results">
        <thead>
          <tr><th>副本</th><th>结果</th><th>交易号 / 原因</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in batch.items" :key="item.itemRef">
            <td>{{ item.itemRef }}</td>
            <td>
              <el-tag :type="item.outcome === 'LENT' ? 'success' : 'danger'">{{ item.outcome }}</el-tag>
            </td>
            <td>{{ item.txId ?? (item.reasons ?? []).join('；') }}</td>
          </tr>
        </tbody>
      </table>
    </template>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { createWalkInLoans } from '../index'

const channel = ref('SELF_SERVICE')
const readerId = ref('')
const items = ref([{ titleId: '', copyId: '' }])
const batch = ref(null)
const { state, run } = useApiRequest()

async function submit() {
  const envelope = await run(() =>
    createWalkInLoans({
      readerId: readerId.value || undefined,
      channel: channel.value,
      items: items.value.filter((item) => item.titleId && item.copyId),
    }),
  )
  if (envelope) batch.value = envelope.data
}
</script>

<style scoped>
.walk-in-loans-view {
  max-width: 44rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.walk-in-loans-view > form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: flex-start;
}

.item-row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.id-input {
  width: 12rem;
}

.inline-input {
  min-width: 12rem;
}

.batch-table {
  width: 100%;
  border-collapse: collapse;
}

.batch-table th,
.batch-table td {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}
</style>
