<template>
  <div :class="['app-shell', { 'app-shell--home': isHome }]" :inert="loginPrompt.open.value || undefined">
    <header :class="['app-header', 'app-header--home']">
      <RouterLink to="/" :class="['brand', 'brand--home']">
        <span class="brand-mark">云图</span>
        <strong>公共图书馆</strong>
      </RouterLink>
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
        :class="['app-nav', { 'app-nav--home': true, 'is-mobile-open': mobileNavOpen }]"
        data-testid="app-nav"
        aria-label="主导航"
        @click="closeMobileNav"
      >
        <RouterLink to="/">首页</RouterLink>
        <details v-if="authenticated" class="home-work-menu" data-testid="personal-info-menu" @click.stop>
          <summary>个人信息</summary>
          <div class="home-work-menu__panel" @click="closeMobileNav">
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
          </div>
        </details>
        <RouterLink v-else to="/login" data-testid="personal-info-link">个人信息</RouterLink>
      </nav>
      <div :class="['session-area', 'session-area--home']" data-testid="session-area">
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
    <main :class="['app-main', { 'app-main--home': isHome }]">
      <RouterView :route="backgroundRoute" v-slot="{ Component }">
        <component :is="Component" :key="backgroundRoute.path" />
      </RouterView>
      <BusinessDialog v-if="modalOpen" :open="modalOpen" :title="route.meta.title || '详情'" :breadcrumbs="modalBreadcrumbs" :assistant="route.name !== 'ai-assistant'" @close="closeRouteModal">
        <RouterView v-slot="{ Component }"><component :is="Component" :key="route.fullPath" /></RouterView>
      </BusinessDialog>
    </main>
    <Teleport :to="workspace.triggerTarget.value || 'body'" :disabled="!workspace.triggerTarget.value">
      <button
        :ref="element => workspace.triggerElement.value = element"
        v-show="showAssistantTrigger"
        class="home-floating-ai"
        type="button"
        data-testid="home-assistant-trigger"
        aria-haspopup="dialog"
        :aria-expanded="workspace.assistantOpen.value"
        aria-controls="home-ai-dialog"
        @click="openAssistant"
      >✦ 询问 AI</button>
    </Teleport>
    <ModalWorkspace :state="workspace" :locked="loginPrompt.open.value" />
    <LoginDialog :open="loginPrompt.open.value" @authenticated="loginPrompt.finish(true)" @cancel="loginPrompt.finish(false)" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, provide, ref, shallowRef, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getRole, getSessionState, isAuthenticated, logout } from '@/platform/auth/session'
import BusinessDialog from '@/components/BusinessDialog.vue'
import ModalWorkspace from '@/components/ModalWorkspace.vue'
import { createModalWorkspace, modalWorkspaceKey } from '@/components/modalWorkspace'
import { backgroundModalKey, getModalBreadcrumbs, isModalRoute, modalBackgrounds } from '@/router/presentation'
import { endCurrentSession, LoginDialog } from '@/modules/identity'
import { createLoginPrompt, loginPromptKey } from '@/platform/auth/loginPrompt'
import {
  clearUnreadBusinessNotification,
  getReaderNotificationSessionKey,
  refreshUnreadBusinessNotification,
  unreadBusinessNotification,
} from '@/modules/delivery'

const router = useRouter()
const route = useRoute()
const backgroundRoute = shallowRef(router.resolve(isModalRoute(route) ? modalBackgrounds[route.name] : route.fullPath))
const modalOpen = computed(() => isModalRoute(route))
const modalBreadcrumbs = computed(() => getModalBreadcrumbs(route, router))
const workspace = createModalWorkspace()
const loginPrompt = createLoginPrompt()
provide(loginPromptKey, loginPrompt.request)
onBeforeUnmount(() => loginPrompt.finish(false))
watch(() => route.fullPath, () => loginPrompt.finish(false), { flush: 'sync' })
const showAssistantTrigger = computed(() => !workspace.assistantOpen.value
  && (!workspace.active.value || workspace.active.value.assistant))
async function openAssistant() {
  workspace.assistantOpen.value = true
  await nextTick()
  document.getElementById('home-ai-dialog')?.querySelector('button')?.focus({ preventScroll: true })
}
provide(modalWorkspaceKey, workspace)
provide(backgroundModalKey, computed(() => modalOpen.value || workspace.open.value))
watch(() => route.fullPath, () => {
  if (!isModalRoute(route)) backgroundRoute.value = router.resolve(route.fullPath)
}, { flush: 'sync' })
watch(() => [getSessionState().token, getSessionState().actor?.accountId, getRole()], () => {
  const meta = backgroundRoute.value.meta
  if (meta.requiresAuth && (!isAuthenticated() || (meta.roles.length && !meta.roles.includes(getRole())))) {
    backgroundRoute.value = router.resolve('/')
  }
}, { flush: 'sync' })
const isHome = computed(() => backgroundRoute.value.name === 'home')
function closeRouteModal() {
  void router.push(backgroundRoute.value.fullPath)
}
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
.home-floating-ai { position: fixed; right: 28px; bottom: 28px; z-index: 30; display: flex; align-items: center; gap: 8px; min-height: 48px; padding: 12px 22px; border: 0; border-radius: 999px; color: #fff; background: #3154c9; box-shadow: 0 10px 24px rgb(49 84 201 / 24%); cursor: pointer; font-family: inherit; }
.home-floating-ai:focus-visible { outline: 3px solid #90a9ff; outline-offset: 3px; }
@media (max-width: 640px) { .home-floating-ai { right: 16px; bottom: 16px; } }

:global(body:has(.app-shell--home)) {
  margin: 0;
}

:global(.app-shell--home),
:global(.app-shell--home > .app-header--home),
:global(.app-shell--home > .app-header--home *),
:global(.app-shell--home > .app-header--home *::before),
:global(.app-shell--home > .app-header--home *::after),
:global(.app-shell--home > .notification-banner),
:global(.app-shell--home > .notification-banner *),
:global(.app-shell--home > .notification-banner *::before),
:global(.app-shell--home > .notification-banner *::after),
:global(.app-shell--home > .app-main--home) {
  box-sizing: border-box;
}

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

.app-shell--home {
  background: #f5f7fb;
  color: #192238;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.app-header--home {
  position: sticky;
  top: 0;
  z-index: 20;
  flex-wrap: nowrap;
  gap: 0;
  height: 72px;
  box-sizing: border-box;
  padding: 0 max(24px, calc((100vw - 1240px) / 2));
  border-bottom-color: #e6eaf0;
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(12px);
}

.app-header--home .mobile-nav-toggle {
  display: none;
}

.brand--home {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  color: #17213a;
  text-decoration: none;
}

.brand--home .brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 13px;
  background: #2448bd;
  color: #fff;
  font-family: Georgia, serif;
  font-size: 13px;
  font-weight: 700;
}

.brand--home strong {
  font-family: Georgia, "Noto Serif SC", serif;
  font-size: 20px;
}

.app-nav--home {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 5px;
  height: 100%;
  margin-left: 44px;
}

.app-nav--home a,
.app-nav--home .home-work-menu > summary {
  display: flex;
  align-items: center;
  min-height: 100%;
  padding: 0 14px;
  color: #667086;
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
}

.app-nav--home a:hover,
.app-nav--home .home-work-menu > summary:hover,
.app-nav--home .router-link-active {
  color: #2448bd;
}

.home-work-menu {
  position: relative;
  display: flex;
  align-items: stretch;
}

.home-work-menu > summary {
  list-style: none;
}

.home-work-menu > summary::-webkit-details-marker {
  display: none;
}

.home-work-menu > summary::after {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin: -3px 0 0 9px;
  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
  content: "";
  transform: rotate(45deg);
}

.home-work-menu[open] > summary::after {
  margin-top: 3px;
  transform: rotate(225deg);
}

.home-work-menu__panel {
  position: absolute;
  top: calc(100% + 2px);
  right: 0;
  z-index: 21;
  display: grid;
  width: max-content;
  min-width: 190px;
  max-height: min(70vh, 520px);
  gap: 2px;
  overflow-y: auto;
  padding: 7px;
  border: 1px solid #e1e6ef;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 30px rgb(31 43 78 / 14%);
}

.home-work-menu:not([open]) > .home-work-menu__panel {
  display: none;
}

.home-work-menu__panel a {
  min-height: 38px;
  padding: 0 10px;
  border-radius: 6px;
  color: #46516a;
  white-space: nowrap;
}

.home-work-menu__panel a:hover {
  background: #f2f5fc;
}

.session-area--home {
  gap: 10px;
  margin-left: auto;
}

.session-area--home [data-testid="login-link"] {
  display: flex;
  min-height: 38px;
  align-items: center;
  padding: 0 13px;
  border: 1px solid #2448bd;
  border-radius: 9px;
  background: #2448bd;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.app-main--home {
  width: min(1240px, calc(100% - 48px));
  margin: 0 auto;
  padding: 0;
}

.app-shell--home .notification-banner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 13px;
  width: min(1240px, calc(100% - 48px));
  min-height: 70px;
  box-sizing: border-box;
  margin: 22px auto 0;
  padding: 11px 17px;
  border-color: #f1cc88;
  border-radius: 12px;
  background: #fff9ec;
  box-shadow: 0 8px 22px rgb(91 68 27 / 7%);
  color: #273550;
}

.app-shell--home .notification-banner-message {
  font-size: 13px;
}

.app-shell--home .notification-banner a {
  color: #a86613;
  font-size: 11px;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 900px) {
  .app-header--home {
    padding: 0 18px;
  }

  .brand--home strong {
    display: none;
  }

  .app-nav--home {
    margin-left: 20px;
  }

  .app-nav--home a,
  .app-nav--home .home-work-menu > summary {
    padding: 0 8px;
  }
}

@media (max-width: 640px) {
  .app-header--home {
    height: auto;
    min-height: 62px;
    flex-wrap: wrap;
    column-gap: 0.5rem;
    padding: 9px 14px;
  }

  .app-shell--home .mobile-nav-toggle {
    display: inline-flex;
    order: 3;
    margin-left: 0;
    border-color: #e1e5ec;
    border-radius: 9px;
    color: #38445d;
  }

  .brand--home {
    order: 1;
  }

  .session-area--home {
    order: 2;
  }

  .app-nav--home {
    display: none;
    order: 4;
    flex: 1 0 100%;
    flex-wrap: wrap;
    gap: 4px;
    height: auto;
    margin: 9px 0 0;
    padding-top: 8px;
    border-top: 1px solid #e6eaf0;
  }

  .app-nav--home.is-mobile-open {
    display: flex;
  }

  .app-nav--home > a {
    min-height: 38px;
    flex: 1 1 9rem;
    padding: 0 10px;
  }

  .app-nav--home .home-work-menu {
    flex: 1 0 100%;
    display: block;
  }

  .app-nav--home .home-work-menu > summary {
    min-height: 38px;
    padding: 0 10px;
  }

  .home-work-menu__panel {
    position: static;
    width: auto;
    max-height: 50vh;
    grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
    margin: 2px 0 7px;
    box-shadow: none;
  }

  .home-work-menu__panel a {
    min-width: 0;
    overflow-wrap: anywhere;
    white-space: normal;
  }

  .app-main--home {
    width: min(100% - 28px, 1240px);
  }

  .app-shell--home .notification-banner {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
    width: min(100% - 28px, 1240px);
    min-height: 62px;
    margin-top: 15px;
    padding: 10px 13px;
  }
}
</style>
