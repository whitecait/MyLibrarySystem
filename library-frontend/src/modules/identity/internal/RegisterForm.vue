<template>
  <div class="register-form-content">
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
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { register } from '../index'

const emit = defineEmits(['registered', 'pending'])
const username = ref('')
const password = ref('')
const displayName = ref('')
const result = ref(null)
const { state, run } = useApiRequest()
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())

async function onSubmit() {
  if (state.status === 'loading') return
  const submittedUsername = username.value
  emit('pending', true)
  try {
    const envelope = await run(() => register(
      { username: submittedUsername, password: password.value, displayName: displayName.value },
      { signal: controller.signal, onUnauthorizedResponse: () => {} },
    ))
    if (!envelope || controller.signal.aborted) return
    result.value = envelope.data
    password.value = ''
    emit('registered', submittedUsername)
  } finally { if (!controller.signal.aborted) emit('pending', false) }
}
</script>
