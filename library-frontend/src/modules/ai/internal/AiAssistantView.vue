<template>
  <section class="ai-assistant-view">
    <h1>AI 助手</h1>
    <p class="quota" data-testid="ai-quota">
      会话 <span data-testid="ai-session-id">{{ sessionId ?? '—' }}</span> · 身份
      <span data-testid="ai-role">{{ role ?? '—' }}</span> ·
      <span data-testid="ai-quota-scope">{{ role === 'VISITOR' ? '访客共享额度' : '账户额度' }}</span>
      <span data-testid="ai-quota-remaining">{{ quota ?? '—' }}</span>
    </p>

    <div class="messages" data-testid="ai-messages">
      <p v-if="turns.length === 0" class="hint">向 AI 助手描述你的需求，例如“帮我预填续借”。</p>
      <div v-for="(turn, index) in turns" :key="index" class="turn" :class="turn.role">
        <div class="bubble">
          <SafeMarkdown :source="turn.role === 'user' ? turn.text : turn.data.answer" />
        </div>
        <ul v-if="turn.role === 'assistant' && turn.data.citations.length" class="citations" data-testid="ai-citations">
          <li v-for="citation in turn.data.citations" :key="citation">{{ citation }}</li>
        </ul>
        <div v-if="turn.role === 'assistant' && turn.data.actions.length" class="actions">
          <div v-for="action in turn.data.actions" :key="action.draftId" class="action-card" :data-testid="`ai-action-${action.level}`">
            <span class="action-label">{{ action.label }}（{{ action.level }}）</span>
            <button
              v-if="action.level === 'L2' && canOpenPrefill(action)"
              type="button"
              :data-testid="`ai-action-open-${action.draftId}`"
              @click="openPrefill(action)"
            >
              打开表单
            </button>
            <span v-else-if="action.level === 'L2'" class="action-note" data-testid="ai-action-l2-here">
              请在对应业务页面中使用该入口
            </span>
            <span v-else-if="action.level === 'L1'" class="action-note" data-testid="ai-action-l1-note">
              只读信息，不做任何操作
            </span>
            <span v-else class="action-note" data-testid="ai-action-l3-note">仅作引导，请在对应页面自行操作</span>
          </div>
        </div>
      </div>
    </div>

    <el-alert
      v-if="quotaExhausted"
      type="warning"
      :closable="false"
      data-testid="ai-quota-exhausted"
      title="额度已耗尽（MLS-AI-002）"
      description="本会话不再发起模型调用。"
    />
    <form class="composer" data-testid="ai-composer" @submit.prevent="send">
      <el-input
        v-model="draft"
        data-testid="ai-input"
        :disabled="quotaExhausted || sessionState.status === 'loading'"
        placeholder="输入消息"
      />
      <el-button type="primary" native-type="submit" data-testid="ai-send" :disabled="quotaExhausted" :loading="sendState.status === 'loading'">
        发送
      </el-button>
      <el-button native-type="button" data-testid="ai-events-toggle" @click="toggleEvents">事件流</el-button>
    </form>

    <div v-if="eventsVisible" class="events">
      <p v-if="eventsLoading" aria-live="polite">加载中…</p>
      <div v-if="events.length" class="events-scroll" data-testid="ai-events-scroll" tabindex="0" aria-label="AI 会话事件列表，可横向滚动">
        <table class="events-table" data-testid="ai-events-table">
          <thead>
            <tr><th>事件</th><th>类型</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="event in events" :key="event.eventId">
              <td>{{ event.eventId }}</td>
              <td>{{ event.type }}</td>
              <td>{{ event.occurredAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="eventsState.status !== 'failure' && !eventsLoading">暂无事件</p>
      <ErrorAlert v-if="eventsState.status === 'failure'" :failure="eventsState.failure" />
    </div>
    <ErrorAlert v-if="sendState.status === 'failure' && !quotaExhausted" :failure="sendState.failure" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind } from '@/platform/http'
import { useApiRequest } from '@/platform/http'
import { listAiEvents, sendAiMessage, startAiSession } from '../index'
import SafeMarkdown from './SafeMarkdown'

const props = defineProps({ canApplyPrefill: { type: Boolean, default: false } })
const emit = defineEmits(['prefill'])
const router = useRouter()

const sessionId = ref(null)
const role = ref(null)
const quota = ref(null)
const turns = ref([])
const draft = ref('')
const events = ref([])
const eventsVisible = ref(false)
const eventsCursor = ref(null)
const eventsLoading = ref(false)
const eventsRequestVersion = ref(0)
const eventsSessionId = ref(null)
const eventsState = reactive({ status: 'idle', failure: null })

const sessionState = useApiRequest()
const { state: sendState, run: runSend } = useApiRequest()

const quotaExhausted = computed(
  () => sendState.status === 'failure' && sendState.failure?.kind === FailureKind.QUOTA_EXHAUSTED,
)

onMounted(async () => {
  const envelope = await sessionState.run(() => startAiSession())
  if (!envelope) return
  sessionId.value = envelope.data.sessionId
  role.value = envelope.data.role
  quota.value = envelope.data.quota.remaining
})

async function send() {
  const message = draft.value.trim()
  if (!message || !sessionId.value) return
  turns.value.push({ role: 'user', text: message })
  draft.value = ''
  const envelope = await runSend(() => sendAiMessage({ sessionId: sessionId.value, message }))
  if (!envelope) return
  turns.value.push({ role: 'assistant', data: envelope.data })
  quota.value = envelope.data.quota.remaining
  if (eventsVisible.value) await refreshEvents()
}

function canOpenPrefill(action) {
  if (action.actionType === 'WALK_IN_LOAN') return role.value === 'LIBRARIAN'
  return props.canApplyPrefill
}

function openPrefill(action) {
  if (action.actionType === 'WALK_IN_LOAN') {
    if (role.value !== 'LIBRARIAN') return
    void router.push({
      name: 'walk-in',
      query: {
        readerId: String(action.prefill?.readerId ?? ''),
        titleId: String(action.prefill?.titleId ?? ''),
        copyId: String(action.prefill?.copyId ?? ''),
        aiDraftId: String(action.draftId),
      },
    })
    return
  }
  if (props.canApplyPrefill) emit('prefill', action)
}

async function toggleEvents() {
  eventsVisible.value = !eventsVisible.value
  if (!eventsVisible.value) {
    eventsRequestVersion.value += 1
    eventsLoading.value = false
    return
  }
  await refreshEvents()
}

async function refreshEvents() {
  const currentSessionId = sessionId.value
  if (!eventsVisible.value || !currentSessionId) return

  if (eventsSessionId.value !== currentSessionId) {
    events.value = []
    eventsCursor.value = null
    eventsSessionId.value = currentSessionId
  }

  const requestVersion = ++eventsRequestVersion.value
  const requestedCursors = new Set()
  let cursor = eventsCursor.value
  eventsLoading.value = true
  eventsState.status = 'loading'
  eventsState.failure = null
  try {
    while (eventsVisible.value && requestVersion === eventsRequestVersion.value && currentSessionId === sessionId.value) {
      const cursorKey = cursor ?? '<first-page>'
      if (requestedCursors.has(cursorKey)) break
      requestedCursors.add(cursorKey)

      const eventRequest = useApiRequest()
      const envelope = await eventRequest.run(() => listAiEvents({ sessionId: currentSessionId, cursor: cursor ?? undefined, limit: 20 }))
      if (requestVersion !== eventsRequestVersion.value || currentSessionId !== sessionId.value || !eventsVisible.value) return
      if (!envelope) {
        eventsState.status = 'failure'
        eventsState.failure = eventRequest.state.failure
        return
      }
      eventsState.status = 'success'

      const knownIds = new Set(events.value.map((event) => String(event.eventId)))
      for (const event of envelope.data.items) {
        if (!knownIds.has(String(event.eventId))) {
          events.value.push(event)
          knownIds.add(String(event.eventId))
        }
      }
      const lastEvent = envelope.data.items.at(-1)
      if (lastEvent) eventsCursor.value = String(lastEvent.eventId)

      const nextCursor = envelope.data.nextCursor
      if (!nextCursor || String(nextCursor) === String(cursor)) break
      cursor = String(nextCursor)
    }
  } finally {
    if (requestVersion === eventsRequestVersion.value) eventsLoading.value = false
  }
}
</script>

<style scoped>
.ai-assistant-view {
  max-width: 52rem;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.quota {
  color: #526274;
  font-size: 0.9rem;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 8rem;
}

.turn {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 0;
  max-width: 100%;
}

.bubble {
  max-width: 100%;
  min-width: 0;
  overflow-wrap: anywhere;
}

.turn.user .bubble {
  align-self: flex-end;
  background: #e8f2f8;
  padding: 0.5rem 0.85rem;
  border-radius: 0.75rem;
}

.turn.assistant .bubble {
  align-self: flex-start;
  background: #f2f4f6;
  padding: 0.5rem 0.85rem;
  border-radius: 0.75rem;
}

.bubble :deep(.safe-markdown > :first-child) {
  margin-top: 0;
}

.bubble :deep(.safe-markdown > :last-child) {
  margin-bottom: 0;
}

.bubble :deep(.markdown-table-scroll) {
  max-width: 100%;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
}

.bubble :deep(.markdown-table) {
  min-width: 24rem;
  border-collapse: collapse;
}

.bubble :deep(.markdown-table th),
.bubble :deep(.markdown-table td) {
  border: 1px solid #cad8e3;
  padding: 0.35rem 0.6rem;
  text-align: left;
}

.bubble :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  white-space: pre;
}

.hint {
  color: #526274;
}

.citations {
  margin: 0;
  padding-left: 1.25rem;
  color: #526274;
  font-size: 0.85rem;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.action-card {
  border: 1px solid #cad8e3;
  border-radius: 0.5rem;
  padding: 0.6rem 0.85rem;
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.action-note {
  color: #526274;
  font-size: 0.85rem;
}

.composer {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.composer .el-input {
  flex: 1 1 12rem;
  min-width: 0;
}

.events-scroll {
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
}

.events-table {
  width: 100%;
  min-width: 24rem;
  border-collapse: collapse;
}

.events-table th,
.events-table td {
  text-align: left;
  padding: 0.35rem 0.6rem;
  border-bottom: 1px solid #e2eaf0;
}

.events {
  max-width: 100%;
  min-width: 0;
}
</style>
