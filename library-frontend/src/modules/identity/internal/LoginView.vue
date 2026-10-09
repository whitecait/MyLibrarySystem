<template>
  <section class="login-view">
    <h1>登录</h1>
    <LoginForm @authenticated="finishNavigation" />
    <p>没有账户？<RouterLink to="/register">注册读者账户</RouterLink></p>
  </section>
</template>

<script setup>
import { RouterLink, useRouter } from 'vue-router'
import { consumeContinuation } from '@/platform/auth/continuation'
import LoginForm from './LoginForm.vue'

const router = useRouter()
async function finishNavigation() {
  const continuation = consumeContinuation()
  await router.push(continuation?.path ?? '/')
}
</script>

<style scoped>
.login-view { max-width: 24rem; display: flex; flex-direction: column; gap: 1rem; }
</style>
