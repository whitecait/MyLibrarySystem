import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, expect, it, vi } from 'vitest'
import { logout } from '@/platform/auth/session'
import { setTransport } from '@/platform/http'
import { createTestRouter } from '@/tests/helpers'
import BusinessDialog from './BusinessDialog.vue'
import ActionPanel from './ActionPanel.vue'

const wrappers = []
afterEach(() => { wrappers.forEach(wrapper => wrapper.unmount()); wrappers.length = 0; logout(); setTransport(null) })
function mountDialog(component, props, slots) {
  const wrapper = mount(component, { attachTo: document.body, props, slots, global: { plugins: [ElementPlus, createTestRouter()] } })
  wrappers.push(wrapper)
  return wrapper
}
it('renders an isolated form without creating a second assistant instance', async () => {
  const calls = []
  setTransport({ mode: 'test', request: async operationId => { calls.push(operationId) } })
  const wrapper = mountDialog(BusinessDialog, { open: true, title: '详情' }, { default: '<p>已有表单</p>' })
  await flushPromises()
  expect(wrapper.text()).toContain('已有表单')
  expect(calls).toEqual([])
  expect(wrapper.find('[data-testid="ai-composer"]').exists()).toBe(false)
})
it('reopens the same selected record without recreating its background', async () => {
  const wrapper = mountDialog(ActionPanel, { activeKey: 1, title: '办理', entryId: 'record', assistant: false }, { default: '<input data-testid="record-value" value="未提交内容" />' })
  await flushPromises()
  expect(wrapper.get('[data-testid="record-value"]').element.value).toBe('未提交内容')
  await wrapper.get('[data-testid="business-close"]').trigger('click')
  await flushPromises()
  await wrapper.setProps({ activeKey: 2 })
  await flushPromises()
  expect(wrapper.get('[data-testid="record-value"]').isVisible()).toBe(true)
})
it('passes a close request to its parent', async () => {
  const onClose = vi.fn()
  const wrapper = mountDialog(BusinessDialog, { open: true, title: '详情', onClose })
  await flushPromises()
  await wrapper.get('[data-testid="business-close"]').trigger('click')
  await flushPromises()
  expect(onClose).toHaveBeenCalledOnce()
})

it.each(['.el-overlay-dialog', '.modal-workspace__panels'])(
  'closes a transaction when the user clicks outside its visible card: %s', async selector => {
    const wrapper = mountDialog(ActionPanel, { activeKey: 1, title: '办理', entryId: 'record', assistant: false }, { default: '<input data-testid="record-value" value="未提交内容" />' })
    await flushPromises()
    const outside = wrapper.get(selector)
    await outside.trigger('mousedown')
    await outside.trigger('mouseup')
    await outside.trigger('click')
    await flushPromises()
    expect(wrapper.get('.el-overlay').element.style.display).toBe('none')
    await wrapper.get('[data-testid="record-open"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="record-value"]').isVisible()).toBe(true)
  },
)
