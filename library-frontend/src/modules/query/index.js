// QUERY 模块公开只读请求接口与读者业务详情路由；不承载提交守卫。
import { apiRequest } from '@/platform/http'

export function getArrivalDashboard({ businessDate } = {}, options = {}) {
  return apiRequest('getArrivalDashboard', { query: { businessDate }, ...options })
}

export function getMyBusiness({ group, kind, status, cursor, limit } = {}, options = {}) {
  const query = { status, cursor, limit }
  if (group !== undefined) query.group = group
  if (kind !== undefined) query.kind = kind
  return apiRequest('getMyBusiness', { query, ...options })
}

export const queryRoutes = [
  {
    path: '/me/business/:txId',
    name: 'my-business-detail',
    component: () => import('./internal/MyBusinessDetailView.vue'),
    props: true,
    meta: { module: 'query', requiresAuth: true, roles: ['READER'], title: '业务详情' },
  },
]

export { default as ArrivalDashboardView } from './internal/ArrivalDashboardView.vue'
export { default as MyBusinessView } from './internal/MyBusinessView.vue'
