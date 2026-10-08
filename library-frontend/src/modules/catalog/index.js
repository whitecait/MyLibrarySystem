// CATALOG 模块公开请求接口：公开检索、详情、阅读内容与馆员书目维护。
import { apiRequest } from '@/platform/http'

export function searchTitles({ q, category, availability, cursor, limit } = {}, options = {}) {
  return apiRequest('searchTitles', { query: { q, category, availability, cursor, limit }, ...options })
}

export function getTitle({ titleId }, options = {}) {
  return apiRequest('getTitle', { params: { titleId }, ...options })
}

export function getReadingContent({ titleId, cursor, limit } = {}, options = {}) {
  return apiRequest('getReadingContent', { params: { titleId }, query: { cursor, limit }, ...options })
}

export function createTitle({ isbn, title, authors, category, publisher, publishYear, priceCents }, options = {}) {
  return apiRequest('createTitle', {
    body: { isbn, title, authors, category, publisher, publishYear, priceCents },
    ...options,
  })
}

export function updateTitle({ titleId, ...fields }, options = {}) {
  return apiRequest('updateTitle', { params: { titleId }, body: fields, ...options })
}

export function removeTitle({ titleId, version, reason }, options = {}) {
  return apiRequest('removeTitle', { params: { titleId }, body: { version, reason }, ...options })
}

export { getCatalogDiscovery } from './internal/discovery'

export { default as PortalView } from './internal/PortalView.vue'
export { default as TitleDetailView } from './internal/TitleDetailView.vue'
export { default as ReadingView } from './internal/ReadingView.vue'
export { default as CatalogAdminView } from './internal/CatalogAdminView.vue'
