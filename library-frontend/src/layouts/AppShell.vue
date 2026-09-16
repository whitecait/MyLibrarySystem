<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink to="/" class="brand">MyLibrarySystem</RouterLink>
      <nav class="app-nav" data-testid="app-nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/catalog">书目检索</RouterLink>
        <template v-if="authenticated">
          <RouterLink v-if="isLibrarian" to="/admin/catalog">书目维护</RouterLink>
          <RouterLink v-if="isAdmin" to="/admin/accounts">账户管理</RouterLink>
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

async function onLogout() {
  try {
    await endCurrentSession()
  } catch {
    // 服务端会话已不可用时以本地清理为准
  }
  logout()
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
