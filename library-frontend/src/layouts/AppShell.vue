<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink to="/" class="brand">MyLibrarySystem</RouterLink>
      <nav class="app-nav" data-testid="app-nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/catalog">书目检索</RouterLink>
        <RouterLink to="/assistant" data-testid="assistant-link">AI 助手</RouterLink>
        <template v-if="authenticated">
          <RouterLink v-if="isReader" to="/me/business">我的业务</RouterLink>
          <RouterLink v-if="isReader || isLibrarian" to="/walk-in">现场借阅</RouterLink>
          <RouterLink v-if="isReader || isLibrarian" to="/fines">罚款支付</RouterLink>
          <RouterLink v-if="isLibrarian" to="/admin/catalog">书目维护</RouterLink>
          <RouterLink v-if="isLibrarian" to="/admin/copies">副本维护</RouterLink>
          <RouterLink v-if="isLibrarian" to="/desk">馆员工作台</RouterLink>
          <RouterLink v-if="isLibrarian" to="/review-requests">复核发起</RouterLink>
          <RouterLink v-if="isLibrarian" to="/admin/dashboard">到馆看板</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/reviews">复核决定</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/closing">闭馆恢复</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/reconciliation">对账</RouterLink>
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
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { getRole, isAuthenticated, logout } from '@/platform/auth/session'
import { endCurrentSession } from '@/modules/identity'

const router = useRouter()
const authenticated = computed(() => isAuthenticated())
const roleLabel = computed(() => getRole() ?? '')
const isAdmin = computed(() => getRole() === 'ADMIN')
const isLibrarian = computed(() => getRole() === 'LIBRARIAN')
const isReader = computed(() => getRole() === 'READER')

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
  gap: 1.5rem;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid #d9e4ec;
  background: #fff;
}

.brand {
  font-weight: 700;
  color: #162033;
  text-decoration: none;
}

.app-nav {
  display: flex;
  gap: 1rem;
  flex: 1;
}

.session-area {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.app-main {
  flex: 1;
  padding: 1.5rem;
}
</style>
