import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
// 导入主布局组件
import MainLayout from '../layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 登录页路由，独立于主布局
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    // 需要主布局的页面路由组
    {
      path: '/',
      component: MainLayout, // 使用 MainLayout 作为布局
      children: [ // 所有子路由都会在 MainLayout 的 <router-view> 中显示
        {
          path: '', // 默认子路由，访问 '/' 时显示
          name: 'home',
          component: HomeView
        },
        {
          path: '/about',
          name: 'about',
          // route level code-splitting
          // this generates a separate chunk (About.[hash].js) for this route
          // which is lazy-loaded when the route is visited.
          component: () => import('../views/AboutView.vue')
        },
        {
          path: '/book-management',
          name: 'book-management',
          component: () => import('../views/BookManagement.vue')
        },
        {
          path: '/reader-management',
          name: 'reader-management',
          component: () => import('../views/ReaderManagement.vue')
        },
        {
          path: '/category-management',
          name: 'category-management',
          component: () => import('../views/CategoryManagement.vue')
        },
        {
          path: '/book-browsing',
          name: 'book-browsing',
          component: () => import('../views/BookBrowsing.vue')
        },
        {
          path: '/my-borrowing',
          name: 'my-borrowing',
          component: () => import('../views/MyBorrowing.vue')
        },
        {
          path: '/my-fines',
          name: 'my-fines',
          component: () => import('../views/MyFines.vue')
        },
        {
          path: '/borrow-approval',
          name: 'borrow-approval',
          component: () => import('../views/BorrowApproval.vue')
        },
        {
          path: '/renewal-approval',
          name: 'renewal-approval',
          component: () => import('../views/RenewalApproval.vue')
        },
        {
          path: '/return-approval',
          name: 'return-approval',
          component: () => import('../views/ReturnApproval.vue')
        },
        {
          path: '/penalty-management',
          name: 'penalty-management',
          component: () => import('../views/PenaltyManagement.vue')
        },
        {
          path: '/news-management',
          name: 'news-management',
          component: () => import('../views/NewsManagement.vue')
        },
        {
          path: '/reader-profile',
          name: 'reader-profile',
          component: () => import('../views/ReaderProfile.vue')
        }
      ]
    }
  ]
})

// 设置导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token');

  // 场景1：用户已登录，但试图访问登录页
  if (to.name === 'login' && token) {
    // 将其重定向到主页
    next({ name: 'home' });
  }
  // 场景2：用户未登录，且试图访问的不是登录页
  else if (to.name !== 'login' && !token) {
    // 将其重定向到登录页
    next({ name: 'login' });
  }
  // 场景3：其他所有情况（未登录访问登录页、已登录访问非登录页）
  else {
    // 正常放行
    next();
  }
});

export default router