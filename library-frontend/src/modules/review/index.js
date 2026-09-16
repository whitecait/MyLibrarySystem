// REVIEW 模块公开请求接口：复核列表、发起与决定。
// requestReview 由后端高风险用例经内部流程发起，前端不提供独立发起页面。
import { apiRequest } from '@/platform/http'

export function listReviews({ status, cursor, limit } = {}, options = {}) {
  return apiRequest('listReviews', { query: { status, cursor, limit }, ...options })
}

export function requestReview({ targetType, targetId, action, payload, reason }, options = {}) {
  return apiRequest('requestReview', { body: { targetType, targetId, action, payload, reason }, ...options })
}

export function decideReview({ reviewId, decision, reason }, options = {}) {
  return apiRequest('decideReview', { params: { reviewId }, body: { decision, reason }, ...options })
}

export { default as ReviewListView } from './internal/ReviewListView.vue'
