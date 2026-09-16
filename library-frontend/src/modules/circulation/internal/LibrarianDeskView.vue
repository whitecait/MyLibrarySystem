<template>
  <section class="librarian-desk-view">
    <h1>馆员工作台</h1>

    <div class="action-block">
      <h2>归还</h2>
      <form data-testid="return-form" @submit.prevent="submitReturn">
        <el-input v-model="returnCopyId" data-testid="return-copy-id" placeholder="副本 ID" class="inline-input" />
        <select v-model="condition" data-testid="return-condition" aria-label="归还状况">
          <option value="INTACT">完好</option>
          <option value="DAMAGED">损坏</option>
          <option value="LOST">遗失</option>
        </select>
        <el-button type="primary" native-type="submit" data-testid="return-submit" :loading="returnState.status === 'loading'">
          确认归还
        </el-button>
      </form>
      <template v-if="returnResult">
        <el-alert
          v-if="returnResult.outcome === 'RETURNED'"
          type="success"
          :closable="false"
          data-testid="return-returned"
          :title="`归还完成，交易号 ${returnResult.txId}`"
          :description="returnResult.fineId ? `本次归还形成罚款（罚款 ID ${returnResult.fineId}），请在罚款结算页办理。` : undefined"
        />
        <el-alert
          v-else
          type="warning"
          :closable="false"
          data-testid="return-abnormal"
          title="归还异常，已转人工处理"
        />
      </template>
      <ErrorAlert v-if="returnState.status === 'failure'" :failure="returnState.failure" />
    </div>

    <div class="action-block">
      <h2>到台取书确认</h2>
      <form data-testid="claim-form" @submit.prevent="submitClaim">
        <el-input v-model="claimTxId" data-testid="claim-tx-id" placeholder="预约交易号" class="inline-input" />
        <el-input v-model="claimCopyId" data-testid="claim-copy-id" placeholder="交付副本 ID" class="inline-input" />
        <el-button type="primary" native-type="submit" data-testid="claim-submit" :loading="claimState.status === 'loading'">
          确认取书
        </el-button>
      </form>
      <el-alert
        v-if="claimResult"
        type="success"
        :closable="false"
        data-testid="claim-success"
        :title="`取书完成：交易 ${claimResult.txId} 现为 ${claimResult.status ?? claimResult.outcome}`"
      />
      <ErrorAlert v-if="claimState.status === 'failure'" :failure="claimState.failure" />
    </div>

    <div class="action-block">
      <h2>人工处置</h2>
      <form data-testid="manual-form" @submit.prevent="submitManual">
        <el-input v-model="manualTxId" data-testid="manual-tx-id" placeholder="交易号" class="inline-input" />
        <select v-model="manualAction" data-testid="manual-action" aria-label="处置动作">
          <option value="RELEASE">释放</option>
          <option value="CONFIRM_ENTRY">确认入账</option>
          <option value="REJECT">拒绝</option>
        </select>
        <el-input v-model="manualReason" data-testid="manual-reason" placeholder="处置原因（必填）" class="inline-input" />
        <el-button type="primary" native-type="submit" data-testid="manual-submit" :loading="manualState.status === 'loading'">
          提交处置
        </el-button>
      </form>
      <el-alert
        v-if="manualResult"
        type="success"
        :closable="false"
        data-testid="manual-success"
        :title="`处置完成：交易 ${manualResult.txId} 现为 ${manualResult.status ?? manualResult.outcome}`"
      />
      <ErrorAlert v-if="manualState.status === 'failure'" :failure="manualState.failure" />
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { applyManualAction, claimPickup, returnCopy } from '../index'

const returnCopyId = ref('')
const condition = ref('INTACT')
const { state: returnState, run: runReturn } = useApiRequest()
const returnResult = ref(null)

const claimTxId = ref('')
const claimCopyId = ref('')
const { state: claimState, run: runClaim } = useApiRequest()
const claimResult = ref(null)

const manualTxId = ref('')
const manualAction = ref('RELEASE')
const manualReason = ref('')
const { state: manualState, run: runManual } = useApiRequest()
const manualResult = ref(null)

async function submitReturn() {
  const envelope = await runReturn(() => returnCopy({ copyId: returnCopyId.value, condition: condition.value }))
  if (envelope) returnResult.value = envelope.data
}

async function submitClaim() {
  const envelope = await runClaim(() => claimPickup({ txId: claimTxId.value, copyId: claimCopyId.value }))
  if (envelope) claimResult.value = envelope.data
}

async function submitManual() {
  const envelope = await runManual(() =>
    applyManualAction({ txId: manualTxId.value, action: manualAction.value, reason: manualReason.value }),
  )
  if (envelope) manualResult.value = envelope.data
}
</script>

<style scoped>
.librarian-desk-view {
  max-width: 52rem;
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
  flex-wrap: wrap;
}

.inline-input {
  min-width: 12rem;
}
</style>
