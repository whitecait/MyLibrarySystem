// 应用路由与权限呈现守卫：路由元数据声明 module / requiresAuth / roles；
// 未登录访问受保护页面时只保存登录续行意图，角色不足呈现 403 页面。
import { createRouter, createWebHistory } from 'vue-router'
import { getRole, isAuthenticated } from '../platform/auth/session.js'
import { saveContinuation } from '../platform/auth/continuation.js'
import HomePage from '../views/HomePage.vue'
import LoginPage from '../views/LoginPage.vue'
import ForbiddenPage from '../views/ForbiddenPage.vue'
import NotFoundPage from '../views/NotFoundPage.vue'

export const routes = [
  { path: '/', name: 'home', component: HomePage, meta: { module: null, requiresAuth: false, roles: [], title: '首页' } },
  { path: '/login', name: 'login', component: LoginPage, meta: { module: 'identity', requiresAuth: false, roles: [], title: '登录' } },
  { path: '/forbidden', name: 'forbidden', component: ForbiddenPage, meta: { module: null, requiresAuth: false, roles: [], title: '无权限' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage, meta: { module: null, requiresAuth: false, roles: [], title: '页面不存在' } },
]

export function createAppRouter({ history = createWebHistory(), extraRoutes = [] } = {}) {
  const router = createRouter({
    history,
    routes: [...routes, ...extraRoutes],
    scrollBehavior: () => ({ top: 0 }),
  })

  router.beforeEach((to) => {
    if (to.meta.requiresAuth && !isAuthenticated()) {
      saveContinuation({ intent: 'navigate', path: to.fullPath, params: null })
      return { name: 'login' }
    }
    if (Array.isArray(to.meta.roles) && to.meta.roles.length > 0) {
      const role = getRole()
      if (!role || !to.meta.roles.includes(role)) return { name: 'forbidden' }
    }
    return true
  })

  return router
}

export const router = createAppRouter()
