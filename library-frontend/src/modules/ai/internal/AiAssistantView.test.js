import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { login, logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import AiAssistantView from './AiAssistantView.vue'

beforeEach(() => login({ accessToken: 't', actor: { accountId: '1001', role: 'READER' } }))
afterEach(() => {
  logout()
  setTransport(null)
})

async function mountAssistant(props = {}) {
  const wrapper = await mountView(AiAssistantView, { props })
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

    expect(wrapper.find('[data-testid="ai-session-id"]').text()).toBe('7001')
    expect(wrapper.find('[data-testid="ai-quota-remaining"]').text()).toBe('10')
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

  it('renders answers, citations and the L2 card, updating the quota', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '帮我预填续借')

    const messages = wrapper.find('[data-testid="ai-messages"]')
    expect(messages.text()).toContain('请核对后提交')
    expect(wrapper.find('[data-testid="ai-action-L2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ai-quota-remaining"]').text()).toBe('9')
  })

  it('keeps the L2 entry inert without a hosting form and explains why', async () => {
    installMockTransport({ fixtures: sharedFixtures })
    const wrapper = await mountAssistant()
    await sendMessage(wrapper, '帮我预填续借')

    expect(wrapper.find('[data-testid="ai-action-L2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ai-action-open-7201"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-action-l2-here"]').text()).toContain('请在对应业务页面')
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
  })
})
