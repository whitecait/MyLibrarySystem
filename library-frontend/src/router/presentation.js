// 模态路由保留可分享的地址；背景页使用独立路由快照，不随模态步骤切换。
export const backgroundModalKey = Symbol('backgroundModal')
export const modalBackgrounds = {
  login: '/',
  register: '/',
  'title-detail': '/',
  'title-reading': '/',
  'title-calendar': '/',
  'my-business-detail': '/me/business',
  'ai-assistant': '/',
}
export function isModalRoute(route) {
  return Object.hasOwn(modalBackgrounds, route.name ?? '')
}
