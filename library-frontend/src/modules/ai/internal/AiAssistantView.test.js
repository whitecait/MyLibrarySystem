import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { createTestRouter, installMockTransport, mountView } from '../../../tests/helpers'
import { WalkInLoansView } from '@/modules/circulation'
import AiAssistantView from './AiAssistantView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => {
  logout()
  setTransport(null)
})

async function mountAssistant(props = {}, router) {
  const wrapper = await mountView(AiAssistantView, { props, ...(router ? { router } : {}) })
  await flushPromises()
  return wrapper
}

async function sendMessage(wrapper, text) {
  await wrapper.find('[data-testid="ai-input"]').setValue(text)
  await wrapper.find('[data-testid="ai-composer"]').trigger('submit')
  await flushPromises()
}

describe('AiAssistantView', () => {
  it('starts a session and shows the quota', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant()

    expect(wrapper.find('h1').text()).toBe('AI 助手')
    expect(wrapper.find('[data-testid="ai-session-id"]').text()).toBe('7001')
    expect(wrapper.find('[data-testid="ai-quota-remaining"]').text()).toBe('10')
  })

  it('renders the compact chat without page metadata or the event stream', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant({ compact: true })

    expect(wrapper.find('h1').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-session-id"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-role"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-quota"]').text()).toContain('今日剩余 10 次询问')
    expect(wrapper.find('[data-testid="ai-messages"]').text()).toContain('想找什么书？也可以询问借阅和预约。')
    expect(wrapper.find('[data-testid="ai-events-toggle"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-events-table"]').exists()).toBe(false)
    expect(wrapper.find('.ai-assistant-view').classes()).toContain('compact')
    expect(wrapper.find('[data-testid="ai-messages"]').classes()).toContain('messages-compact')
    expect(wrapper.find('[data-testid="ai-composer"]').classes()).toContain('composer-compact')
    expect(wrapper.find('[data-testid="ai-input"]').attributes('aria-label')).toBe('询问 AI')
  })

  it('labels a compact visitor quota as shared without exposing the role', async () => {
    logout()
    installMockTransport({
      fixtures: {
        startAiSession: {
          code: 'MLS-AI-S001',
          message: 'started',
          data: { sessionId: '7001', role: 'VISITOR', quota: { remaining: 10 } },
        },
      },
    })
    const wrapper = await mountAssistant({ compact: true })

    expect(wrapper.find('[data-testid="ai-quota"]').text()).toContain('今日剩余 10 次询问（访客共享）')
    expect(wrapper.find('[data-testid="ai-role"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('VISITOR')
  })

  it('shows session creation failure and retries only after an explicit click', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId) {
        calls.push(operationId)
        if (calls.length === 1) throw new Error('session unavailable')
        return sharedFixtures.startAiSession
      },
    })
    const wrapper = await mountAssistant({ compact: true })

    expect(calls).toEqual(['startAiSession'])
    expect(wrapper.find('[data-testid="ai-session-error"]').text()).toContain('AI 会话创建失败，请重试')
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('session unavailable')
    expect(wrapper.find('[data-testid="ai-session-retry"]').exists()).toBe(true)

    await wrapper.find('[data-testid="ai-session-retry"]').trigger('click')
    await flushPromises()

    expect(calls).toEqual(['startAiSession', 'startAiSession'])
    expect(wrapper.find('[data-testid="ai-session-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-quota"]').text()).toContain('10 次')
  })

  it('keeps the composer unavailable while session creation is pending', async () => {
    let resolveSession
    const calls = []
    setTransport({
      mode: 'mock',
      request(operationId) {
        calls.push(operationId)
        if (operationId === 'startAiSession') return new Promise((resolve) => { resolveSession = resolve })
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })
    const wrapper = await mountAssistant({ compact: true })

    expect(wrapper.find('[data-testid="ai-quota"]').text()).toContain('正在创建会话')
    expect(wrapper.find('[data-testid="ai-input"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="ai-send"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()
    expect(calls).toEqual(['startAiSession'])

    resolveSession(sharedFixtures.startAiSession)
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-input"]').attributes('disabled')).toBeUndefined()
  })

  it('disables blank submissions and prevents concurrent sends', async () => {
    let resolveSend
    const calls = []
    setTransport({
      mode: 'mock',
      request(operationId) {
        calls.push(operationId)
        if (operationId === 'startAiSession') return Promise.resolve(sharedFixtures.startAiSession)
        if (operationId === 'sendAiMessage') return new Promise((resolve) => { resolveSend = resolve })
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })
    const wrapper = await mountAssistant({ compact: true })
    const input = wrapper.find('[data-testid="ai-input"]')
    const composer = wrapper.find('[data-testid="ai-composer"]')

    expect(wrapper.find('[data-testid="ai-send"]').attributes('disabled')).toBeDefined()
    await input.setValue('   ')
    expect(wrapper.find('[data-testid="ai-send"]').attributes('disabled')).toBeDefined()
    await composer.trigger('submit')
    expect(calls).toEqual(['startAiSession'])

    await input.setValue('查找一本书')
    await composer.trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-sending"]').text()).toContain('AI 正在回复')
    expect(input.attributes('disabled')).toBeDefined()
    await composer.trigger('submit')
    await flushPromises()
    expect(calls.filter((operationId) => operationId === 'sendAiMessage')).toHaveLength(1)
    expect(wrapper.find('[data-testid="ai-messages"]').text()).toContain('查找一本书')

    resolveSend(sharedFixtures.sendAiMessage)
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-sending"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-messages"]').text()).toContain('请核对后提交')
  })

  it('shows the server-issued VISITOR role and labels its quota as shared', async () => {
    logout()
    installMockTransport({
      fixtures: {
        startAiSession: {
          code: 'MLS-AI-S001',
          message: 'started',
          data: { sessionId: '7001', role: 'VISITOR', quota: { remaining: 10 } },
        },
      },
    })
    const wrapper = await mountAssistant()

    expect(wrapper.find('[data-testid="ai-role"]').text()).toBe('VISITOR')
    expect(wrapper.find('[data-testid="ai-quota-scope"]').text()).toContain('访客共享额度')
    expect(wrapper.find('[data-testid="ai-quota-remaining"]').text()).toBe('10')
  })

  it('keeps an existing visitor session after account login instead of starting an upgraded session', async () => {
    logout()
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId) {
        calls.push(operationId)
        if (operationId === 'startAiSession') {
          return { code: 'MLS-AI-S001', message: 'started', data: { sessionId: '7001', role: 'VISITOR', quota: { remaining: 10 } } }
        }
        return {
          code: 'MLS-AI-S001',
          message: 'visitor answer',
          data: { messageId: '7102', answer: '访客会话不能查询本人借阅', citations: [], actions: [], quota: { remaining: 9 } },
        }
      },
    })
    const wrapper = await mountAssistant()

    login({ accessToken: 'account-token', actor: { accountId: '1001', role: 'READER' } })
    await sendMessage(wrapper, '查询我的借阅')

    expect(calls).toEqual(['startAiSession', 'sendAiMessage'])
    expect(wrapper.find('[data-testid="ai-role"]').text()).toBe('VISITOR')
    expect(wrapper.text()).toContain('访客会话不能查询本人借阅')
  })

  it('ignores a session start from the prior identity after the new session is ready', async () => {
    let completeOldStart
    let oldSignal
    let starts = 0
    setTransport({ mode: 'test', request(operationId, options) {
      expect(operationId).toBe('startAiSession')
      if (++starts === 1) {
        oldSignal = options.signal
        return new Promise(resolve => { completeOldStart = resolve })
      }
      return Promise.resolve({ code: 'MLS-AI-S001', message: 'ok', data: { sessionId: '7002', role: 'READER', quota: { remaining: 10 } } })
    } })
    const wrapper = await mountAssistant()
    await wrapper.setProps({ sessionRevision: 1 })
    await flushPromises()
    completeOldStart({ code: 'MLS-AI-S001', message: 'old', data: { sessionId: '7001', role: 'LIBRARIAN', quota: { remaining: 0 } } })
    await flushPromises()
    expect(oldSignal.aborted).toBe(true)
    expect(wrapper.get('[data-testid="ai-session-id"]').text()).toBe('7002')
    expect(wrapper.get('[data-testid="ai-role"]').text()).toBe('READER')
    expect(wrapper.get('[data-testid="ai-quota-remaining"]').text()).toBe('10')
    wrapper.unmount()
  })

  it('does not publish the old session when identity changes between async continuations', async () => {
    const starts = []
    setTransport({ mode: 'test', request(operationId) {
      expect(operationId).toBe('startAiSession')
      return new Promise(resolve => starts.push(resolve))
    } })
    const wrapper = await mountAssistant()
    starts[0]({ code: 'MLS-AI-S001', message: 'old', data: { sessionId: '7001', role: 'LIBRARIAN', quota: { remaining: 0 } } })
    void Promise.resolve().then(() => wrapper.setProps({ sessionRevision: 1 }))
    await flushPromises()
    expect(starts).toHaveLength(2)
    expect(wrapper.get('[data-testid="ai-session-id"]').text()).not.toBe('7001')
    expect(wrapper.get('[data-testid="ai-role"]').text()).not.toBe('LIBRARIAN')
    starts[1]({ code: 'MLS-AI-S001', message: 'new', data: { sessionId: '7002', role: 'READER', quota: { remaining: 10 } } })
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-session-id"]').text()).toBe('7002')
    expect(wrapper.get('[data-testid="ai-role"]').text()).toBe('READER')
    wrapper.unmount()
  })

  it('keeps the draft but clears identity-owned turns and discards a late prior answer', async () => {
    let completeOldTurn
    let starts = 0
    setTransport({ mode: 'test', request(operationId) {
      if (operationId === 'startAiSession') return Promise.resolve({ code: 'MLS-AI-S001', message: 'ok', data: { sessionId: String(7000 + ++starts), role: 'READER', quota: { remaining: 10 } } })
      return new Promise(resolve => { completeOldTurn = resolve })
    } })
    const wrapper = await mountAssistant()
    await wrapper.get('[data-testid="ai-input"]').setValue('旧账户问题')
    await wrapper.get('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()
    await wrapper.setProps({ sessionRevision: 1 })
    await flushPromises()
    await wrapper.get('[data-testid="ai-input"]').setValue('未发送的新草稿')
    completeOldTurn({ code: 'MLS-AI-S001', message: 'old', data: { messageId: 'old', answer: '旧账户私有结果', citations: [], actions: [], quota: { remaining: 0 } } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧账户私有结果')
    expect(wrapper.text()).not.toContain('旧账户问题')
    expect(wrapper.get('[data-testid="ai-input"]').element.value).toBe('未发送的新草稿')
    expect(wrapper.get('[data-testid="ai-send"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="ai-quota-remaining"]').text()).toBe('10')
    wrapper.unmount()
  })

  it('renders answers, citations and the L2 card, updating the quota', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '帮我预填续借')

    const messages = wrapper.find('[data-testid="ai-messages"]')
    expect(messages.text()).toContain('请核对后提交')
    expect(wrapper.find('[data-testid="ai-action-L2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ai-quota-remaining"]').text()).toBe('9')
  })

  it('renders Markdown structure while keeping raw HTML inert', async () => {
    installMockTransport({
      fixtures: {
        startAiSession: sharedFixtures.startAiSession,
        sendAiMessage: {
          code: 'MLS-AI-S001',
          message: 'ok',
          data: {
            messageId: '7103',
            answer: `## 预约入口\n\n书目**可以预约**。\n\n| 渠道 | 说明 |\n| --- | --- |\n| ONLINE | 服务端判定 |\n\n<img src=x onerror=alert(1)>\n\n[恶意链接](javascript:alert(1))\n\n\`\`\`text\n${'x'.repeat(600)}<svg onload=alert(1)>\n\`\`\``,
            citations: [],
            actions: [],
            quota: { remaining: 9 },
          },
        },
      },
    })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '这本书怎么预约？')

    expect(wrapper.find('[data-testid="ai-messages"] h2').text()).toBe('预约入口')
    expect(wrapper.find('[data-testid="ai-messages"] strong').text()).toBe('可以预约')
    expect(wrapper.find('[data-testid="ai-messages"] table').text()).toContain('ONLINE')
    expect(wrapper.find('[data-testid="ai-messages"] img').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-messages"] svg').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-messages"] a').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-messages"] pre code').text()).toContain('x'.repeat(600))
    expect(wrapper.find('[data-testid="ai-messages"] .markdown-table-scroll').exists()).toBe(true)
  })

  it('keeps the L2 entry inert without a hosting form and explains why', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '帮我预填续借')

    expect(wrapper.find('[data-testid="ai-action-L2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ai-action-open-7201"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-action-l2-here"]').text()).toContain('请在对应业务页面')
  })

  it('opens a librarian walk-in L2 draft with all fields prefilled and no submission', async () => {
    await login({ accessToken: 'librarian-token', actor: { accountId: '2001', role: 'LIBRARIAN' } })
    const aiFixtures = {
      startAiSession: {
        code: 'MLS-AI-S001',
        message: 'started',
        data: { sessionId: '7017', role: 'LIBRARIAN', quota: { remaining: 10 } },
      },
      sendAiMessage: {
        code: 'MLS-AI-S001',
        message: 'draft ready',
        data: {
          messageId: '7117',
          answer: '请核对现场借阅草稿后提交。',
          citations: [],
          actions: [{
            actionType: 'WALK_IN_LOAN',
            level: 'L2',
            prefill: { readerId: '2', titleId: '700001', copyId: '800002' },
            draftId: '7217',
            label: '打开现场借阅表单',
          }],
          quota: { remaining: 9 },
        },
      },
    }
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        if (operationId === 'createWalkInLoans') {
          return { code: 'MLS-CIRC-S001', message: 'submitted', data: { items: [] } }
        }
        return aiFixtures[operationId]
      },
    })
    const router = createTestRouter([
      { path: '/assistant', name: 'ai-assistant', component: AiAssistantView },
      { path: '/walk-in', name: 'walk-in', component: WalkInLoansView },
    ])
    await router.push('/assistant')
    await router.isReady()
    const assistant = await mountAssistant({}, router)
    await sendMessage(assistant, '为读者2预填书目700001、副本800002的现场借阅')

    const openDraft = assistant.find('[data-testid="ai-action-open-7217"]')
    expect(openDraft.exists()).toBe(true)
    await openDraft.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('walk-in')
    expect(router.currentRoute.value.query).toMatchObject({
      readerId: '2',
      titleId: '700001',
      copyId: '800002',
      aiDraftId: '7217',
    })
    const walkIn = await mountView(WalkInLoansView, { router })
    await flushPromises()
    expect(walkIn.find('[data-testid="walkin-reader-id"]').element.value).toBe('2')
    expect(walkIn.find('[data-testid="walkin-title-0"]').element.value).toBe('700001')
    expect(walkIn.find('[data-testid="walkin-copy-0"]').element.value).toBe('800002')
    expect(walkIn.find('[data-testid="walkin-results"]').exists()).toBe(false)
    expect(calls.filter((call) => call.operationId === 'createWalkInLoans')).toHaveLength(0)
    await walkIn.find('[data-testid="walkin-form"]').trigger('submit')
    await flushPromises()

    const loanCalls = calls.filter((call) => call.operationId === 'createWalkInLoans')
    expect(loanCalls).toHaveLength(1)
    expect(loanCalls[0].options.body).toEqual({
      readerId: '2',
      channel: 'DESK',
      items: [{ titleId: '700001', copyId: '800002' }],
    })
    expect(loanCalls[0].options.headers).toEqual({ 'Ai-Draft-Id': '7217' })
  })

  it('does not expose a librarian-only walk-in draft action to readers', async () => {
    installMockTransport({
      fixtures: {
        startAiSession: {
          code: 'MLS-AI-S001',
          message: 'started',
          data: { sessionId: '7001', role: 'READER', quota: { remaining: 10 } },
        },
        sendAiMessage: {
          code: 'MLS-AI-S001',
          message: 'draft ready',
          data: {
            messageId: '7101',
            answer: '请核对草稿。',
            citations: [],
            actions: [{
              actionType: 'WALK_IN_LOAN',
              level: 'L2',
              prefill: { readerId: '2', titleId: '700001', copyId: '800002' },
              draftId: '7201',
              label: '打开现场借阅表单',
            }],
            quota: { remaining: 9 },
          },
        },
      },
    })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '为读者2预填现场借阅')

    expect(wrapper.find('[data-testid="ai-action-open-7201"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-action-l2-here"]').exists()).toBe(true)
  })

  it('shows the exhausted state and disables input after the 429 sample', async () => {
    installMockTransport({
      fixtures: {
        startAiSession: sharedFixtures.startAiSession,
        sendAiMessage: { status: 429, code: 'MLS-AI-002', message: 'quota exhausted', data: { remaining: 0 } },
      },
    })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '查询我的借阅')

    expect(wrapper.find('[data-testid="ai-quota-exhausted"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ai-input"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="ai-quota-remaining"]').text()).toBe('0')
  })

  it('locks the composer when the server reports zero remaining at session start or after a turn', async () => {
    installMockTransport({
      fixtures: {
        startAiSession: {
          ...sharedFixtures.startAiSession,
          data: { ...sharedFixtures.startAiSession.data, quota: { remaining: 0 } },
        },
      },
    })
    const emptySession = await mountAssistant({ compact: true })
    expect(emptySession.find('[data-testid="ai-quota-exhausted"]').exists()).toBe(true)
    expect(emptySession.find('[data-testid="ai-input"]').attributes('disabled')).toBeDefined()
    expect(emptySession.find('[data-testid="ai-quota-remaining"]').text()).toBe('0')
    emptySession.unmount()

    const calls = []
    setTransport({
      mode: 'mock',
      request(operationId) {
        calls.push(operationId)
        if (operationId === 'startAiSession') {
          return {
            ...sharedFixtures.startAiSession,
            data: { ...sharedFixtures.startAiSession.data, quota: { remaining: 1 } },
          }
        }
        return {
          ...sharedFixtures.sendAiMessage,
          data: { ...sharedFixtures.sendAiMessage.data, quota: { remaining: 0 } },
        }
      },
    })
    const drainedSession = await mountAssistant({ compact: true })
    await sendMessage(drainedSession, '查询图书')

    expect(drainedSession.find('[data-testid="ai-quota-exhausted"]').exists()).toBe(true)
    expect(drainedSession.find('[data-testid="ai-quota-remaining"]').text()).toBe('0')
    expect(drainedSession.find('[data-testid="ai-input"]').attributes('disabled')).toBeDefined()
    await drainedSession.find('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()
    expect(calls.filter((operationId) => operationId === 'sendAiMessage')).toHaveLength(1)
  })

  it('renders L1 and L3 cards as non-submitting guidance', async () => {
    installMockTransport({
      fixtures: {
        startAiSession: sharedFixtures.startAiSession,
        sendAiMessage: {
          code: 'MLS-AI-S001',
          message: 'ok',
          data: {
            messageId: '7102',
            answer: '以下是可选项',
            citations: ['借阅规则 3.1'],
            actions: [
              { actionType: 'VIEW_LOAN', level: 'L1', prefill: { txId: '4001' }, draftId: '7202', label: '查看借阅详情' },
              { actionType: 'GUIDE', level: 'L3', prefill: {}, draftId: '7203', label: '到我的业务页续借' },
            ],
            quota: { remaining: 8 },
          },
        },
      },
    })
    const wrapper = await mountAssistant({ canApplyPrefill: true })
    await sendMessage(wrapper, '我能做什么')

    expect(wrapper.find('[data-testid="ai-action-l1-note"]').text()).toContain('只读信息')
    expect(wrapper.find('[data-testid="ai-action-l3-note"]').text()).toContain('仅作引导')
    expect(wrapper.find('[data-testid="ai-citations"]').text()).toContain('借阅规则 3.1')
    expect(wrapper.find('[data-testid="ai-action-open-7202"]').exists()).toBe(false)
  })

  it('loads the session event stream on demand', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant()

    await wrapper.find('[data-testid="ai-events-toggle"]').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="ai-events-table"] tbody tr')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('QUOTA_CONSUMED')
  })

  it('requests the event stream for the session returned at startup', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return sharedFixtures[operationId]
      },
    })
    const wrapper = await mountAssistant()

    await wrapper.find('[data-testid="ai-events-toggle"]').trigger('click')
    await flushPromises()

    const eventRequest = calls.find((call) => call.operationId === 'listAiEvents')
    expect(eventRequest.options.params.sessionId).toBe('7001')
  })

  it('paginates all events, deduplicates overlap, and refreshes after send and reopen', async () => {
    const calls = []
    const initialEvents = Array.from({ length: 20 }, (_, index) => ({
      eventId: String(index + 1), type: 'MODEL_REQUEST', payload: {}, occurredAt: '2026-09-24T12:00:00Z',
    }))
    const laterEvents = Array.from({ length: 6 }, (_, index) => ({
      eventId: String(index + 20), type: 'MODEL_RESPONSE', payload: {}, occurredAt: '2026-09-24T12:00:01Z',
    }))
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        if (operationId === 'startAiSession') return sharedFixtures.startAiSession
        if (operationId === 'sendAiMessage') return sharedFixtures.sendAiMessage
        if (operationId === 'listAiEvents') {
          const cursor = options.query?.cursor
          if (!cursor) return { code: 'MLS-AI-S002', message: 'ok', data: { items: initialEvents, nextCursor: '20' } }
          if (cursor === '20') return { code: 'MLS-AI-S002', message: 'ok', data: { items: laterEvents, nextCursor: null } }
          if (cursor === '25') return { code: 'MLS-AI-S002', message: 'ok', data: { items: [{ eventId: '26', type: 'USER_MESSAGE', payload: {}, occurredAt: '2026-09-24T12:00:02Z' }], nextCursor: null } }
          return { code: 'MLS-AI-S002', message: 'ok', data: { items: [], nextCursor: null } }
        }
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })
    const wrapper = await mountAssistant()
    const toggle = wrapper.find('[data-testid="ai-events-toggle"]')
    await toggle.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-testid="ai-events-table"] tbody tr')).toHaveLength(25)
    expect(new Set(wrapper.findAll('[data-testid="ai-events-table"] tbody tr').map((row) => row.find('td').text())).size).toBe(25)
    expect(wrapper.find('[data-testid="ai-events-scroll"]').exists()).toBe(true)

    await sendMessage(wrapper, '新问题')
    expect(wrapper.findAll('[data-testid="ai-events-table"] tbody tr')).toHaveLength(26)
    expect(wrapper.find('[data-testid="ai-events-table"]').text()).toContain('26')

    await toggle.trigger('click')
    await toggle.trigger('click')
    await flushPromises()
    expect(calls.filter((call) => call.operationId === 'listAiEvents')).toHaveLength(4)
    expect(wrapper.findAll('[data-testid="ai-events-table"] tbody tr')).toHaveLength(26)
  })

  it('ignores a stale event response after the panel is closed and reopened', async () => {
    const pending = []
    let requestCount = 0
    setTransport({
      mode: 'mock',
      request(operationId) {
        if (operationId === 'startAiSession') return Promise.resolve(sharedFixtures.startAiSession)
        if (operationId === 'listAiEvents') {
          requestCount += 1
          return new Promise((resolve) => pending.push(resolve))
        }
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })
    const wrapper = await mountAssistant()
    const toggle = wrapper.find('[data-testid="ai-events-toggle"]')
    await toggle.trigger('click')
    await toggle.trigger('click')
    await toggle.trigger('click')
    expect(requestCount).toBe(2)

    pending[1]({ code: 'MLS-AI-S002', message: 'ok', data: { items: [{ eventId: '2', type: 'CURRENT', payload: {}, occurredAt: '2026-09-24T12:00:00Z' }], nextCursor: null } })
    await flushPromises()
    pending[0]({ code: 'MLS-AI-S002', message: 'ok', data: { items: [{ eventId: '1', type: 'STALE', payload: {}, occurredAt: '2026-09-24T11:59:00Z' }], nextCursor: null } })
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-events-table"]').text()).toContain('CURRENT')
    expect(wrapper.find('[data-testid="ai-events-table"]').text()).not.toContain('STALE')
  })

  it('presents a wrong visitor cookie as an invisible session when events return 404', async () => {
    installMockTransport({
      fixtures: {
        startAiSession: sharedFixtures.startAiSession,
        listAiEvents: { status: 404, code: 'MLS-AI-005', message: 'session not found', data: {} },
      },
    })
    const wrapper = await mountAssistant()

    await wrapper.find('[data-testid="ai-events-toggle"]').trigger('click')
    await flushPromises()

    const alert = wrapper.find('[data-testid="error-alert"]')
    expect(alert.text()).toContain('对象不存在或不可见')
    expect(alert.text()).toContain('MLS-AI-005')
  })

  it('presents a missing visitor credential as unauthenticated when a turn returns 401', async () => {
    logout()
    installMockTransport({
      fixtures: {
        startAiSession: {
          code: 'MLS-AI-S001',
          message: 'started',
          data: { sessionId: '7001', role: 'VISITOR', quota: { remaining: 10 } },
        },
        sendAiMessage: { status: 401, code: 'MLS-ID-001', message: 'visitor credential required', data: {} },
      },
    })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '查询开放馆藏')

    const alert = wrapper.find('[data-testid="error-alert"]')
    expect(alert.text()).toContain('请先登录')
    expect(alert.text()).toContain('MLS-ID-001')
    expect(wrapper.find('[data-testid="ai-messages"]').text()).toContain('查询开放馆藏')
  })
})
