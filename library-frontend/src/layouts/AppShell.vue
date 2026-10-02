<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink to="/" class="brand">MyLibrarySystem</RouterLink>
      <button
        type="button"
        class="mobile-nav-toggle"
        data-testid="mobile-nav-toggle"
        aria-controls="app-nav"
        :aria-expanded="String(mobileNavOpen)"
        aria-label="切换主导航"
        @click="toggleMobileNav"
      >
        <span aria-hidden="true">{{ mobileNavOpen ? '收起菜单' : '菜单' }}</span>
      </button>
      <nav
        id="app-nav"
        class="app-nav"
        data-testid="app-nav"
        :class="{ 'is-mobile-open': mobileNavOpen }"
        aria-label="主导航"
        @click="closeMobileNav"
      >
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/catalog">书目检索</RouterLink>
        <RouterLink to="/assistant" data-testid="assistant-link">AI 助手</RouterLink>
        <template v-if="authenticated">
          <RouterLink v-if="isReader" to="/me/business">我的业务</RouterLink>
          <RouterLink v-if="isLibrarian" to="/walk-in">现场借阅</RouterLink>
          <RouterLink v-if="isReader || isLibrarian" to="/fines">罚款支付</RouterLink>
          <RouterLink v-if="isLibrarian" to="/admin/catalog">书目维护</RouterLink>
          <RouterLink v-if="isLibrarian" to="/admin/copies">副本维护</RouterLink>
          <RouterLink v-if="isLibrarian" to="/desk">馆员工作台</RouterLink>
          <RouterLink v-if="isLibrarian" to="/review-requests">复核发起</RouterLink>
          <RouterLink v-if="isLibrarian" to="/admin/dashboard">到馆看板</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/reviews">复核决定</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/closing">闭馆恢复</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/simulator">模拟调度</RouterLink>
          <RouterLink v-if="isLibrarian || isAdmin" to="/admin/audit">审计</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/accounts">账户管理</RouterLink>
          <RouterLink to="/notifications">通知</RouterLink>
        </template>
      </nav>
      <div class="session-area" data-testid="session-area">
        <template v-if="authenticated">
          <span class="actor-role">{{ roleLabel }}</span>
          <el-button size="small" data-testid="logout-button" @click="onLogout">退出登录</el-button>
        </template>
        <RouterLink v-else to="/login" data-testid="login-link">登录</RouterLink>
      </div>
    </header>
    <aside
      v-if="isReader && unreadBusinessNotification"
      class="notification-banner"
      data-testid="notification-banner"
      role="status"
    >
      <p class="notification-banner-message">{{ unreadBusinessNotification.message }}</p>
      <RouterLink
        :to="{ name: 'my-business-detail', params: { txId: String(unreadBusinessNotification.relatedTxId) } }"
        data-testid="notification-banner-link"
      >
        关联事务 {{ unreadBusinessNotification.relatedTxId }} · 预约日期 {{ unreadBusinessNotification.reservationDate }}
      </RouterLink>
    </aside>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getRole, isAuthenticated, logout } from '@/platform/auth/session'
import { endCurrentSession } from '@/modules/identity'
import {
  clearUnreadBusinessNotification,
  getReaderNotificationSessionKey,
  refreshUnreadBusinessNotification,
  unreadBusinessNotification,
} from '@/modules/delivery'

const router = useRouter()
const route = useRoute()
const mobileNavOpen = ref(false)
const authenticated = computed(() => isAuthenticated())
const roleLabel = computed(() => getRole() ?? '')
const isAdmin = computed(() => getRole() === 'ADMIN')
const isLibrarian = computed(() => getRole() === 'LIBRARIAN')
const isReader = computed(() => getRole() === 'READER')
const readerSessionKey = computed(() => (authenticated.value && isReader.value ? getReaderNotificationSessionKey() : null))

watch([readerSessionKey, () => route.name], ([sessionKey, routeName], previous) => {
  if (!sessionKey) {
    clearUnreadBusinessNotification()
    return
  }
  const previousSessionKey = Array.isArray(previous) ? previous[0] : null
  if (sessionKey !== previousSessionKey || routeName === 'home' || routeName === 'notification-center') {
    void refreshUnreadBusinessNotification(sessionKey).catch(() => {})
  }
}, { immediate: true })

function toggleMobileNav() {
  mobileNavOpen.value = !mobileNavOpen.value
}

function closeMobileNav() {
  mobileNavOpen.value = false
}

async function onLogout() {
  // getTransport() 在请求时读取会话令牌：注销必须先于本地清理发出，
  // 否则请求不带 Bearer，服务端按 MLS-ID-001 拒绝，会话不会真正终结。
  try {
    await endCurrentSession()
  } catch {
    // 服务端会话已不可用时以本地清理为准
  } finally {
    // DEF-M6-004：无论注销成败，本地令牌必须清理，
    // 避免残留作废令牌让后续请求被拦截器按 MLS-ID-001 拒绝。
    logout()
  }
  router.push({ name: 'home' })
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1.5rem;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid #d9e4ec;
  background: #fff;
}

.notification-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  margin: 1rem 1.5rem 0;
  border: 1px solid #d6a340;
  border-radius: 0.4rem;
  padding: 0.75rem 1rem;
  background: #fff8e7;
  color: #33270f;
}

.notification-banner-message {
  margin: 0;
}

.notification-banner a {
  color: #174d77;
  font-weight: 600;
}

.brand {
  font-weight: 700;
  color: #162033;
  text-decoration: none;
}

.app-nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  flex: 1 1 0;
  min-width: 0;
}

.app-nav a {
  min-width: 0;
  overflow-wrap: anywhere;
}

.mobile-nav-toggle {
  display: none;
  min-height: 2.5rem;
  align-items: center;
  justify-content: center;
  border: 1px solid #9aafc1;
  border-radius: 0.35rem;
  padding: 0.35rem 0.7rem;
  background: #fff;
  color: #162033;
}

.session-area {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.app-main {
  flex: 1;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  padding: 1.5rem;
}

@media (max-width: 48rem) {
  .app-header {
    gap: 0.65rem;
    padding: 0.75rem;
  }

  .notification-banner {
    margin: 0.75rem 0.75rem 0;
  }

  .mobile-nav-toggle {
    display: inline-flex;
    margin-left: auto;
  }

  .app-nav {
    display: none;
    flex: 1 0 100%;
    gap: 0.5rem;
  }

  .app-nav.is-mobile-open {
    display: flex;
  }

  .app-nav a {
    flex: 1 1 9rem;
  }

  .session-area {
    margin-left: 0;
  }

  .app-main {
    padding: 1rem;
  }
}

@media (max-width: 24rem) {
  .app-nav a {
    flex-basis: 100%;
  }

  .actor-role {
    display: none;
  }
}
</style>
