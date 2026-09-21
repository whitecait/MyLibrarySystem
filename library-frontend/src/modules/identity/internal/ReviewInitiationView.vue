<template>
  <section class="review-initiation-view">
    <h1>复核发起</h1>
    <p>高风险账户变更（停用 / 恢复 / 变更角色）由馆员发起、管理员在复核决定页处置；变更在批准前不生效。</p>
    <form data-testid="initiation-form" @submit.prevent="submit">
      <el-input
        v-model="accountId"
        data-testid="initiation-account-id"
        placeholder="账户 ID"
        class="id-input"
      />
      <select v-model="action" data-testid="initiation-action" aria-label="变更动作">
        <option value="SUSPEND">停用账户</option>
        <option value="RESTORE">恢复账户</option>
        <option value="CHANGE_ROLE">变更角色</option>
      </select>
      <select v-if="action === 'CHANGE_ROLE'" v-model="targetRole" data-testid="initiation-role" aria-label="目标角色">
        <option value="READER">读者</option>
        <option value="LIBRARIAN">馆员</option>
        <option value="ADMIN">管理员</option>
      </select>
      <el-input v-model="reason" data-testid="initiation-reason" placeholder="变更原因（必填）" class="reason-input" />
      <el-button type="primary" native-type="submit" data-testid="initiation-submit" :loading="state.status === 'loading'">
        提交复核
      </el-button>
    </form>

    <el-alert
      v-if="result"
      type="warning"
      :closable="false"
      data-testid="initiation-result"
      :title="`已提交复核：受理号 ${result.reviewId}（${result.status}）`"
      description="变更在管理员批准前不生效。"
    />
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { requestAccountChange } from '../index'

// accountId 手工录入：馆员页面不查账户列表（listAccounts 为 ADMIN 能力），
// 沿用 /walk-in DESK 渠道 walkin-reader-id 的手工录入先例（DEF-M6-005 方向 b）。
const accountId = ref('')
const action = ref('SUSPEND')
const targetRole = ref('READER')
const reason = ref('')
const result = ref(null)
const { state, run } = useApiRequest()

async function submit() {
  const payload = action.value === 'CHANGE_ROLE' ? { role: targetRole.value } : {}
  const envelope = await run(() =>
    requestAccountChange({
      accountId: accountId.value,
      action: action.value,
      payload,
      reason: reason.value,
    }),
  )
  if (envelope) result.value = envelope.data
}
</script>

<style scoped>
.review-initiation-view {
  max-width: 44rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.review-initiation-view > form {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.id-input {
  width: 12rem;
}

.reason-input {
  min-width: 16rem;
}
</style>
