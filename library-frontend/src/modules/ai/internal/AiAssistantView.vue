<template>
  <section class="ai-assistant-view" :class="{ compact }">
    <h1 v-if="!compact">AI 助手</h1>
    <p v-if="!compact" class="quota" data-testid="ai-quota">
      会话 <span data-testid="ai-session-id">{{ sessionId ?? '—' }}</span> · 身份
      <span data-testid="ai-role">{{ role ?? '—' }}</span> ·
      <span data-testid="ai-quota-scope">{{ role === 'VISITOR' ? '访客共享额度' : '账户额度' }}</span>
      <span data-testid="ai-quota-remaining">{{ quota ?? '—' }}</span>
    </p>
    <p v-else class="compact-quota" data-testid="ai-quota" aria-live="polite">
      <template v-if="sessionId">今日剩余 <span data-testid="ai-quota-remaining">{{ quota }}</span> 次询问<span v-if="role === 'VISITOR'" class="quota-scope">（访客共享）</span></template>
      <template v-else-if="sessionRequestState.status === 'loading'">正在创建会话…</template>
      <template v-else>会话暂不可用</template>
    </p>
    <p v-if="!compact && sessionRequestState.status === 'loading'" class="session-loading" data-testid="ai-session-loading" aria-live="polite">
      正在创建会话…
    </p>

    <div v-if="sessionRequestState.status === 'failure'" class="session-error" data-testid="ai-session-error" role="alert">
      <p>AI 会话创建失败，请重试。</p>
      <ErrorAlert :failure="sessionRequestState.failure" />
      <el-button
        type="primary"
        data-testid="ai-session-retry"
        :disabled="sessionRequestState.status === 'loading'"
        @click="createSession"
      >
        重试创建会话
      </el-button>
    </div>

    <div
      ref="messagesElement"
      class="messages"
      :class="{ 'messages-compact': compact }"
      data-testid="ai-messages"
      @scroll="updateFollowMessages"
    >
      <p v-if="turns.length === 0" class="hint">
        {{ compact ? '想找什么书？也可以询问借阅和预约。' : '向 AI 助手描述你的需求，例如“帮我预填续借”。' }}
      </p>
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
      <p v-if="isSending" class="sending-status" data-testid="ai-sending" aria-live="polite">AI 正在回复…</p>
    </div>

    <el-alert
      v-if="quotaExhausted"
      type="warning"
      :closable="false"
      data-testid="ai-quota-exhausted"
      title="额度已耗尽（MLS-AI-002）"
      description="本会话不再发起模型调用。"
    />
    <form class="composer" :class="{ 'composer-compact': compact }" data-testid="ai-composer" @submit.prevent="send">
      <el-input
        v-model="draft"
        data-testid="ai-input"
        aria-label="询问 AI"
        :disabled="!sessionId || sessionRequestState.status !== 'success' || isSending || quotaExhausted"
        placeholder="输入消息"
      />
      <el-button type="primary" native-type="submit" data-testid="ai-send" :disabled="submitDisabled" :loading="isSending">
        发送
      </el-button>
      <el-button v-if="!compact" native-type="button" data-testid="ai-events-toggle" @click="toggleEvents">事件流</el-button>
    </form>

    <div v-if="eventsVisible && !compact" class="events">
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind } from '@/platform/http'
import { useApiRequest } from '@/platform/http'
import { listAiEvents, sendAiMessage, startAiSession } from '../index'
import SafeMarkdown from './SafeMarkdown'

const props = defineProps({
  canApplyPrefill: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  sessionRevision: { type: Number, default: 0 },
  active: { type: Boolean, default: true },
})
const emit = defineEmits(['prefill'])
const router = useRouter()

const sessionId = ref(null)
const role = ref(null)
const quota = ref(null)
const turns = ref([])
const draft = ref('')
const sending = ref(false)
const messagesElement = ref(null)
const followMessages = ref(true)
const events = ref([])
const eventsVisible = ref(false)
const eventsCursor = ref(null)
const eventsLoading = ref(false)
const eventsRequestVersion = ref(0)
const eventsSessionId = ref(null)
const eventsState = reactive({ status: 'idle', failure: null })

const sessionRequestState = reactive({ status: 'idle', envelope: null, failure: null })
const sendState = reactive({ status: 'idle', envelope: null, failure: null })
let identityVersion = 0
let identityController = new AbortController()

// Each request owns its state so an earlier identity cannot overwrite the current one.
async function runCurrentRequest(state, executor) {
  const version = identityVersion
  const request = useApiRequest()
  const pending = request.run(() => executor({ signal: identityController.signal }))
  Object.assign(state, request.state)
  const envelope = await pending
  if (version !== identityVersion) return null
  Object.assign(state, request.state)
  return envelope
}

watch(() => props.sessionRevision, () => {
  identityVersion++
  identityController.abort()
  identityController = new AbortController()
  sessionId.value = role.value = quota.value = null
  turns.value = []
  sending.value = false
  events.value = []
  eventsVisible.value = false
  eventsCursor.value = eventsSessionId.value = null
  eventsRequestVersion.value++
  eventsLoading.value = false
  Object.assign(eventsState, { status: 'idle', failure: null })
  for (const state of [sessionRequestState, sendState]) {
    Object.assign(state, { status: 'idle', envelope: null, failure: null })
  }
  // Keep the unsent draft and component; identity-owned conversation facts are cleared.
  if (props.active) void createSession()
}, { flush: 'sync' })
watch(() => props.active, active => { if (active) void createSession() })
onBeforeUnmount(() => { identityVersion++; identityController.abort(); eventsRequestVersion.value++ })

const quotaExhausted = computed(
  () => quota.value === 0 || (sendState.status === 'failure' && sendState.failure?.kind === FailureKind.QUOTA_EXHAUSTED),
)
const isSending = computed(() => sending.value || sendState.status === 'loading')
const submitDisabled = computed(
  () => !sessionId.value || sessionRequestState.status !== 'success' || !draft.value.trim() || isSending.value || quotaExhausted.value,
)

onMounted(() => {
  if (props.active) void createSession()
})

async function createSession() {
  if (sessionId.value || sessionRequestState.status === 'loading') return
  const version = identityVersion
  const envelope = await runCurrentRequest(sessionRequestState, options => startAiSession(options))
  if (!envelope || version !== identityVersion) return
  sessionId.value = envelope.data.sessionId
  role.value = envelope.data.role
  quota.value = envelope.data.quota.remaining
  await scrollMessagesToBottom()
}

async function send() {
  if (sending.value || sendState.status === 'loading' || quotaExhausted.value) return
  const message = draft.value.trim()
  if (!message || !sessionId.value || sessionRequestState.status !== 'success') return

  const version = identityVersion
  const currentSessionId = sessionId.value
  sending.value = true
  followMessages.value = true
  turns.value.push({ role: 'user', text: message })
  draft.value = ''
  await scrollMessagesToBottom()
  try {
    if (version !== identityVersion) return
    const envelope = await runCurrentRequest(sendState, options => sendAiMessage({ sessionId: currentSessionId, message }, options))
    if (version !== identityVersion) return
    if (!envelope) {
      if (quotaExhausted.value) quota.value = sendState.failure?.data?.remaining ?? 0
      return
    }
    turns.value.push({ role: 'assistant', data: envelope.data })
    quota.value = envelope.data.quota.remaining
    if (props.compact && followMessages.value) await scrollMessagesToBottom()
    if (eventsVisible.value) await refreshEvents()
  } finally {
    if (version === identityVersion) sending.value = false
  }
}

function updateFollowMessages() {
  if (!props.compact || !messagesElement.value) return
  const { scrollTop, clientHeight, scrollHeight } = messagesElement.value
  followMessages.value = scrollHeight - scrollTop - clientHeight <= 80
}

async function scrollMessagesToBottom() {
  if (!props.compact) return
  await nextTick()
  if (messagesElement.value) messagesElement.value.scrollTop = messagesElement.value.scrollHeight
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
      const envelope = await eventRequest.run(() => listAiEvents({ sessionId: currentSessionId, cursor: cursor ?? undefined, limit: 20 }, { signal: identityController.signal }))
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

.ai-assistant-view.compact {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  max-width: none;
  min-height: 0;
  gap: 0.6rem;
  overflow: hidden;
}

.ai-assistant-view.compact :deep(.el-alert) {
  flex: 0 0 auto;
}

.quota {
  color: #526274;
  font-size: 0.9rem;
}

.compact-quota,
.session-loading {
  flex: 0 0 auto;
  margin: 0;
  color: #526274;
  font-size: 0.9rem;
}

.session-error {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.compact .session-error {
  flex: 0 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.session-error p {
  margin: 0;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 8rem;
}

.messages-compact {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  padding: 0.5rem 0.25rem;
  border: 1px solid #e2eaf0;
  border-radius: 0.75rem;
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

.compact .turn.user .bubble {
  color: #173a5e;
  background: #dceeff;
}

.compact .turn.assistant .bubble {
  color: #25384b;
  background: #f4f7fb;
  border: 1px solid #e2eaf0;
}

.hint {
  color: #526274;
}

.sending-status {
  align-self: flex-start;
  flex: 0 0 auto;
  margin: 0;
  padding: 0.5rem 0.85rem;
  color: #526274;
  background: #f4f7fb;
  border-radius: 0.75rem;
}

.citations {
  margin: 0;
  padding-left: 1.25rem;
  color: #526274;
  font-size: 0.85rem;
}

.compact .citations,
.compact .action-card {
  min-width: 0;
  overflow-wrap: anywhere;
}

.compact .action-card {
  flex-wrap: wrap;
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

.composer-compact {
  position: sticky;
  bottom: 0;
  z-index: 1;
  flex: 0 0 auto;
  flex-wrap: nowrap;
  padding-top: 0.35rem;
  background: #fff;
}

.composer-compact .el-input {
  flex: 1 1 0;
  min-width: 0;
}

.composer-compact :deep(.el-input__wrapper) {
  min-width: 0;
}

@media (max-width: 420px) {
  .composer-compact {
    gap: 0.4rem;
  }

  .composer-compact :deep(.el-button) {
    padding-right: 0.75rem;
    padding-left: 0.75rem;
  }
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
