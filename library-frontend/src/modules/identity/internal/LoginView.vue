<template>
  <section class="login-view">
    <h1>登录</h1>
    <form data-testid="login-form" @submit.prevent="onSubmit">
      <el-form-item label="用户名">
        <el-input v-model="username" name="username" data-testid="login-username" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input
          v-model="password"
          name="password"
          type="password"
          data-testid="login-password"
          autocomplete="current-password"
        />
      </el-form-item>
      <el-button type="primary" native-type="submit" data-testid="login-submit" :loading="state.status === 'loading'">
        登录
      </el-button>
    </form>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
    <p>没有账户？<RouterLink to="/register">注册读者账户</RouterLink></p>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { consumeContinuation } from '@/platform/auth/continuation'
import { login as saveSession } from '@/platform/auth/session'
import { createSession } from '../index'

const router = useRouter()
const username = ref('')
const password = ref('')
const { state, run } = useApiRequest()

async function onSubmit() {
  const envelope = await run(() => createSession({ username: username.value, password: password.value }))
  if (!envelope) return
  saveSession(envelope.data)
  const continuation = consumeContinuation()
  await router.push(continuation?.path ?? '/')
}
</script>

<style scoped>
.login-view {
  max-width: 24rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
</style>
