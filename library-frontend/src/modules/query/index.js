// QUERY 模块公开请求接口：到馆看板与我的业务。QUERY 只读，不承载提交守卫。
import { apiRequest } from '@/platform/http'

export function getArrivalDashboard({ businessDate } = {}, options = {}) {
  return apiRequest('getArrivalDashboard', { query: { businessDate }, ...options })
}

export function getMyBusiness({ kind, status, cursor, limit } = {}, options = {}) {
  return apiRequest('getMyBusiness', { query: { kind, status, cursor, limit }, ...options })
}

export { default as ArrivalDashboardView } from './internal/ArrivalDashboardView.vue'
export { default as MyBusinessView } from './internal/MyBusinessView.vue'
