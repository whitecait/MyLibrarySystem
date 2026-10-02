// 应用路由与权限呈现守卫：路由元数据声明 module / requiresAuth / roles；
// 未登录访问受保护页面时只保存登录续行意图，角色不足呈现 403 页面。
// 页面组件经各模块公开入口（index.js）引用，不触碰模块 internal。
import { createRouter, createWebHistory } from 'vue-router'
import { getRole, isAuthenticated } from '../platform/auth/session.js'
import { saveContinuation } from '../platform/auth/continuation.js'
import { AccountAdminView, LoginView, RegisterView, ReviewInitiationView } from '../modules/identity/index.js'
import { CatalogAdminView, PortalView, ReadingView, TitleDetailView } from '../modules/catalog/index.js'
import {
  ClosingAdminView,
  CopyAdminView,
  LibrarianDeskView,
  ReservationCalendarView,
  WalkInLoansView,
} from '../modules/circulation/index.js'
import { ArrivalDashboardView, MyBusinessView, queryRoutes } from '../modules/query/index.js'
import { AiAssistantView } from '../modules/ai/index.js'
import { FineSettlementView, PaymentSimulatorView } from '../modules/settlement/index.js'
import { ReviewListView } from '../modules/review/index.js'
import { AuditQueryView, NotificationCenterView } from '../modules/delivery/index.js'
import ForbiddenPage from '../views/ForbiddenPage.vue'
import HomePage from '../views/HomePage.vue'
import NotFoundPage from '../views/NotFoundPage.vue'

export const routes = [
  { path: '/', name: 'home', component: HomePage, meta: { module: null, requiresAuth: false, roles: [], title: '首页' } },
  { path: '/login', name: 'login', component: LoginView, meta: { module: 'identity', requiresAuth: false, roles: [], title: '登录' } },
  { path: '/register', name: 'register', component: RegisterView, meta: { module: 'identity', requiresAuth: false, roles: [], title: '注册' } },
  { path: '/catalog', name: 'catalog-portal', component: PortalView, meta: { module: 'catalog', requiresAuth: false, roles: [], title: '书目检索' } },
  { path: '/titles/:titleId', name: 'title-detail', component: TitleDetailView, props: true, meta: { module: 'catalog', requiresAuth: false, roles: [], title: '书目详情' } },
  { path: '/titles/:titleId/read', name: 'title-reading', component: ReadingView, props: true, meta: { module: 'catalog', requiresAuth: false, roles: [], title: '阅读' } },
  { path: '/titles/:titleId/calendar', name: 'title-calendar', component: ReservationCalendarView, props: true, meta: { module: 'circulation', requiresAuth: false, roles: [], title: '预约日历' } },
  { path: '/walk-in', name: 'walk-in', component: WalkInLoansView, meta: { module: 'circulation', requiresAuth: true, roles: ['LIBRARIAN'], title: '现场借阅' } },
  { path: '/me/business', name: 'my-business', component: MyBusinessView, meta: { module: 'query', requiresAuth: true, roles: ['READER'], title: '我的业务' } },
  ...queryRoutes,
  { path: '/assistant', name: 'ai-assistant', component: AiAssistantView, meta: { module: 'ai', requiresAuth: false, roles: [], title: 'AI 助手' } },
  { path: '/admin/accounts', name: 'account-admin', component: AccountAdminView, meta: { module: 'identity', requiresAuth: true, roles: ['ADMIN'], title: '账户管理' } },
  { path: '/admin/catalog', name: 'catalog-admin', component: CatalogAdminView, meta: { module: 'catalog', requiresAuth: true, roles: ['LIBRARIAN'], title: '书目维护' } },
  { path: '/admin/copies', name: 'copy-admin', component: CopyAdminView, meta: { module: 'circulation', requiresAuth: true, roles: ['LIBRARIAN'], title: '副本维护' } },
  { path: '/desk', name: 'librarian-desk', component: LibrarianDeskView, meta: { module: 'circulation', requiresAuth: true, roles: ['LIBRARIAN'], title: '馆员工作台' } },
  { path: '/review-requests', name: 'review-initiation', component: ReviewInitiationView, meta: { module: 'identity', requiresAuth: true, roles: ['LIBRARIAN'], title: '复核发起' } },
  { path: '/admin/dashboard', name: 'arrival-dashboard', component: ArrivalDashboardView, meta: { module: 'query', requiresAuth: true, roles: ['LIBRARIAN'], title: '到馆看板' } },
  { path: '/fines', name: 'fine-settlement', component: FineSettlementView, meta: { module: 'settlement', requiresAuth: true, roles: ['READER', 'LIBRARIAN'], title: '罚款与支付' } },
  { path: '/admin/simulator', name: 'payment-simulator', component: PaymentSimulatorView, meta: { module: 'settlement', requiresAuth: true, roles: ['ADMIN'], title: '模拟支付调度' } },
  { path: '/admin/reviews', name: 'review-list', component: ReviewListView, meta: { module: 'review', requiresAuth: true, roles: ['ADMIN'], title: '高风险复核' } },
  { path: '/admin/closing', name: 'closing-admin', component: ClosingAdminView, meta: { module: 'circulation', requiresAuth: true, roles: ['ADMIN'], title: '闭馆与恢复' } },
  { path: '/notifications', name: 'notification-center', component: NotificationCenterView, meta: { module: 'delivery', requiresAuth: true, roles: [], title: '通知中心' } },
  { path: '/admin/audit', name: 'audit-query', component: AuditQueryView, meta: { module: 'delivery', requiresAuth: true, roles: ['LIBRARIAN', 'ADMIN'], title: '审计查询' } },
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
