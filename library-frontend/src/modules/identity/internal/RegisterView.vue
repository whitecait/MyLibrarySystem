<template>
  <section class="register-view">
    <h1>注册读者账户</h1>
    <p>自助注册即时生效，无需人工审核；一个账户绑定一个角色。</p>
    <form v-if="!result" data-testid="register-form" @submit.prevent="onSubmit">
      <el-form-item label="用户名">
        <el-input v-model="username" name="username" data-testid="register-username" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input
          v-model="password"
          name="password"
          type="password"
          data-testid="register-password"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item label="显示名">
        <el-input v-model="displayName" name="displayName" data-testid="register-display-name" />
      </el-form-item>
      <el-button type="primary" native-type="submit" data-testid="register-submit" :loading="state.status === 'loading'">
        注册
      </el-button>
    </form>
    <el-alert
      v-else
      type="success"
      :closable="false"
      data-testid="register-success"
      title="注册成功"
      :description="`账户 ID ${result.accountId}，角色 ${result.role}，状态 ${result.status}。可以直接登录。`"
    />
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
    <p><RouterLink to="/login">返回登录</RouterLink></p>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { register } from '../index'

const username = ref('')
const password = ref('')
const displayName = ref('')
const result = ref(null)
const { state, run } = useApiRequest()

async function onSubmit() {
  const envelope = await run(() =>
    register({ username: username.value, password: password.value, displayName: displayName.value }),
  )
  if (envelope) result.value = envelope.data
}
</script>

<style scoped>
.register-view {
  max-width: 24rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
</style>
