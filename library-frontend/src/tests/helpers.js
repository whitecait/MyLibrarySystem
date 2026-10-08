// 组件与页面测试的公共助手：安装 mock 传输、构建测试路由并挂载视图。
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { setTransport } from '@/platform/http'
import { createMockTransport } from '@/platform/http/mock/mockAdapter'

export { ElementPlus }

export function installMockTransport({ scenarioId, fixtures } = {}) {
  const transport = createMockTransport({ scenarioId, fixtures })
  setTransport(transport)
  return transport
}

export function createTestRouter(extraRoutes = []) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      ...extraRoutes,
      { path: '/:pathMatch(.*)*', name: 'not-found', component: { template: '<div />' } },
    ],
  })
}

export async function mountView(view, { props, router = createTestRouter(), provide = {} } = {}) {
  const wrapper = mount(view, { props, global: { plugins: [ElementPlus, router], provide } })
  await wrapper.vm.$nextTick()
  return wrapper
}
