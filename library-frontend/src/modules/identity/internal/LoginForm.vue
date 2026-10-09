<template>
  <form data-testid="login-form" @submit.prevent="onSubmit">
    <el-form-item label="用户名">
      <el-input v-model="username" name="username" data-testid="login-username" autocomplete="username" />
    </el-form-item>
    <el-form-item label="密码">
      <el-input v-model="password" name="password" type="password" data-testid="login-password" autocomplete="current-password" />
    </el-form-item>
    <el-button type="primary" native-type="submit" data-testid="login-submit" :loading="state.status === 'loading'">登录</el-button>
    <ErrorAlert v-if="state.status === 'failure'" :failure="state.failure" />
  </form>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { useApiRequest } from '@/platform/http'
import { login as saveSession } from '@/platform/auth/session'
import { createSession } from '../index'

const props = defineProps({ suggestedUsername: { type: String, default: '' } })
const emit = defineEmits(['authenticated', 'pending'])
const username = ref(props.suggestedUsername)
const password = ref('')
const { state, run, reset } = useApiRequest()
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())
watch(() => props.suggestedUsername, value => {
  username.value = value
  password.value = ''
  reset()
})

async function onSubmit() {
  if (state.status === 'loading') return
  emit('pending', true)
  try {
    const envelope = await run(() => createSession(
      { username: username.value, password: password.value },
      // Wrong credentials belong to this form; they do not replace the current page.
      { signal: controller.signal, onUnauthorizedResponse: () => {} },
    ))
    if (!envelope || controller.signal.aborted) return
    saveSession(envelope.data)
    password.value = ''
    emit('authenticated')
  } finally { if (!controller.signal.aborted) emit('pending', false) }
}
</script>
