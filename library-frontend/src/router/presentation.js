// 模态路由保留可分享的地址；背景页使用独立路由快照，不随模态步骤切换。
export const backgroundModalKey = Symbol('backgroundModal')
export const modalBackgrounds = {
  login: '/',
  register: '/',
  'title-detail': '/',
  'title-reading': '/',
  'title-borrow': '/',
  'my-business-detail': '/me/business',
  'ai-assistant': '/',
}
export function isModalRoute(route) {
  return Object.hasOwn(modalBackgrounds, route.name ?? '')
}

// Book steps derive parent links from the route, including when opened directly.
export function getModalBreadcrumbs(route, router) {
  const ancestors = {
    'title-borrow': ['title-detail'],
    'title-reading': ['title-detail'],
  }[route.name]
  if (!ancestors) return []
  return [
    ...ancestors.map(name => {
      const parent = router.resolve({ name, params: { titleId: route.params.titleId } })
      return { title: parent.meta.title, to: parent.fullPath }
    }),
    { title: route.meta.title },
  ]
}
