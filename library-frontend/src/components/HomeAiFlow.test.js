import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { setTransport } from '@/platform/http'
import { AiAssistantView } from '@/modules/ai'
import { mountView } from '@/tests/helpers'
import App from '@/App.vue'
import { createAppRouter } from '@/router'
import { createMemoryHistory } from 'vue-router'
import { logout } from '@/platform/auth/session'

const mountedWrappers = new Set()
function success(code, data, message = 'ok') {
  return { code, message, data }
}

async function waitForUi(assertion) {
  await vi.waitFor(assertion, { timeout: 1500 })
}

afterEach(() => {
  for (const wrapper of mountedWrappers) wrapper.unmount()
  mountedWrappers.clear()
  setTransport(null)
  logout()
})

describe('Home AI flow', () => {
  it('starts on first open, sends through the real assistant, and keeps the conversation after reopening', async () => {
    const calls = []
    setTransport({
      mode: 'test',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        if (operationId === 'searchTitles') {
          return success('MLS-CAT-S001', { items: [], nextCursor: null })
        }
        if (operationId === 'startAiSession') {
          return success('MLS-AI-S001', {
            sessionId: '7001',
            role: 'VISITOR',
            quota: { remaining: 10 },
          }, 'started')
        }
        if (operationId === 'sendAiMessage') {
          return success('MLS-AI-S001', {
            messageId: '7101',
            answer: '这是真实 AI 视图展示的回答。',
            citations: [],
            actions: [],
            quota: { remaining: 9 },
          })
        }
        throw new Error(`Unexpected operation: ${operationId}`)
      },
    })

    logout()
    const router = createAppRouter({ history: createMemoryHistory() })
    await router.push('/')
    await router.isReady()
    const wrapper = await mountView(App, { router })
    mountedWrappers.add(wrapper)
    await flushPromises()

    const starts = () => calls.filter(({ operationId }) => operationId === 'startAiSession')
    const sends = () => calls.filter(({ operationId }) => operationId === 'sendAiMessage')
    const trigger = wrapper.get('[data-testid="home-assistant-trigger"]')

    expect(starts()).toHaveLength(0)
    expect(wrapper.findComponent(AiAssistantView).exists()).toBe(false)
    expect(router.currentRoute.value.fullPath).toBe('/')

    await trigger.trigger('click')
    await flushPromises()
    await waitForUi(() => expect(trigger.attributes('aria-expanded')).toBe('true'))

    expect(starts()).toHaveLength(1)
    expect(wrapper.findComponent(AiAssistantView).exists()).toBe(true)
    expect(router.currentRoute.value.fullPath).toBe('/')

    const assistant = wrapper.findComponent(AiAssistantView)
    await assistant.get('[data-testid="ai-input"]').setValue('推荐一本有关图书馆的书')
    await assistant.get('[data-testid="ai-composer"]').trigger('submit')
    await flushPromises()

    expect(sends()).toHaveLength(1)
    expect(sends()[0].options).toEqual({
      params: { sessionId: '7001' },
      body: { message: '推荐一本有关图书馆的书' },
    })
    expect(wrapper.get('#home-ai-dialog').element?.textContent).toContain('这是真实 AI 视图展示的回答。')

    await wrapper.get('[aria-label="关闭 AI 助手"]').trigger('click')
    await flushPromises()
    await waitForUi(() => expect(trigger.attributes('aria-expanded')).toBe('false'))
    expect(trigger.attributes('aria-expanded')).toBe('false')
    await trigger.trigger('click')
    await flushPromises()
    await waitForUi(() => expect(trigger.attributes('aria-expanded')).toBe('true'))

    expect(starts()).toHaveLength(1)
    expect(sends()).toHaveLength(1)
    expect(wrapper.get('#home-ai-dialog').element?.textContent).toContain('这是真实 AI 视图展示的回答。')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })
})
