import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { login, logout } from '@/platform/auth/session'
import HomeAiDialog from './HomeAiDialog.vue'

const assistantState = vi.hoisted(() => ({ mounts: 0, unmounts: 0 }))
vi.mock('@/modules/ai', async () => {
  const { defineComponent, h, onBeforeUnmount, onMounted, ref } = await import('vue')
  return { AiAssistantView: defineComponent({
    props: { compact: Boolean },
    setup() {
      const turns = ref(0)
      onMounted(() => { assistantState.mounts += 1 })
      onBeforeUnmount(() => { assistantState.unmounts += 1 })
      return () => h('button', { 'data-testid': 'mock-ai-turn', onClick: () => { turns.value += 1 } }, `对话 ${turns.value}`)
    },
  }) }
})
const wrappers = []
beforeEach(() => { logout(); assistantState.mounts = 0; assistantState.unmounts = 0 })
afterEach(() => { wrappers.forEach(wrapper => wrapper.unmount()); wrappers.length = 0; logout() })
function mountPanel(open = false) {
  const wrapper = mount(HomeAiDialog, { attachTo: document.body, props: { open } })
  wrappers.push(wrapper)
  return wrapper
}
it('creates the assistant on first expansion and retains the same conversation after collapse', async () => {
  const wrapper = mountPanel()
  expect(assistantState.mounts).toBe(0)
  await wrapper.setProps({ open: true })
  await wrapper.get('[data-testid="mock-ai-turn"]').trigger('click')
  await wrapper.setProps({ open: false })
  expect(assistantState.unmounts).toBe(0)
  await wrapper.setProps({ open: true })
  expect(assistantState.mounts).toBe(1)
  expect(wrapper.text()).toContain('对话 1')
  expect(wrapper.find('dialog').exists()).toBe(false)
})
it('emits collapse without owning another overlay', async () => {
  const wrapper = mountPanel(true)
  await wrapper.get('[aria-label="关闭 AI 助手"]').trigger('click')
  expect(wrapper.emitted('close')).toHaveLength(1)
  expect(wrapper.find('[aria-modal]').exists()).toBe(false)
})
it('clears the old conversation on identity change, including while collapsed', async () => {
  const wrapper = mountPanel(true)
  await flushPromises()
  login({ accessToken: 'first-token', actor: { accountId: '1001', role: 'READER' } })
  await flushPromises()
  expect(assistantState.mounts).toBe(2)
  expect(assistantState.unmounts).toBe(1)
  await wrapper.setProps({ open: false })
  logout()
  await flushPromises()
  expect(assistantState.unmounts).toBe(2)
  expect(assistantState.mounts).toBe(2)
  expect(document.body.innerHTML).not.toContain('first-token')
  await wrapper.setProps({ open: true })
  expect(assistantState.mounts).toBe(3)
})
it('ignores an expansion that is cancelled before rendering', async () => {
  const wrapper = mountPanel()
  await Promise.all([wrapper.setProps({ open: true }), wrapper.setProps({ open: false })])
  expect(assistantState.mounts).toBe(0)
})
