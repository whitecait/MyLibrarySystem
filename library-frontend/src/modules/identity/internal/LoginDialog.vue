<template>
  <ElDialog
    :model-value="open"
    :title="mode === 'login' ? '登录后提交预约' : '注册读者账户'"
    width="min(420px, calc(100vw - 32px))"
    append-to-body
    align-center
    class="reservation-login-dialog"
    @update:model-value="value => { if (!value) emit('cancel') }"
  >
    <p class="login-context">预约信息已保留。</p>
    <template v-if="open">
      <LoginForm v-show="mode === 'login'" :suggested-username="registeredUsername" @pending="pending = $event" @authenticated="emit('authenticated')" />
      <div v-show="mode === 'register'">
        <p class="registration-context">创建读者账户后，请返回登录。</p>
        <RegisterForm @pending="pending = $event" @registered="registeredUsername = $event" />
      </div>
      <p v-if="mode === 'login'" class="auth-navigation">没有账户？<button type="button" data-testid="register-entry" :disabled="pending" @click="mode = 'register'">注册读者账户</button></p>
      <p v-else class="auth-navigation">已有账户？<button type="button" data-testid="login-entry" :disabled="pending" @click="mode = 'login'">返回登录</button></p>
    </template>
  </ElDialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElDialog } from 'element-plus'
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'
const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['authenticated', 'cancel'])
const mode = ref('login')
const pending = ref(false)
const registeredUsername = ref('')
watch(() => props.open, open => {
  if (!open) return
  mode.value = 'login'
  pending.value = false
  registeredUsername.value = ''
})
</script>

<style>
.reservation-login-dialog.el-dialog { border-radius: 16px; padding: 24px; }
.reservation-login-dialog .login-context { margin: 0 0 20px; color: #68715f; font-size: 13px; }
.reservation-login-dialog form { display: flex; flex-direction: column; gap: 4px; }
.reservation-login-dialog .el-form-item { display: flex; flex-direction: column; align-items: stretch; gap: 6px; margin-bottom: 14px; }
.reservation-login-dialog .el-form-item__label { justify-content: flex-start; }
.reservation-login-dialog .el-button { min-height: 40px; margin: 0; background: #52634a; border-color: #52634a; }
.reservation-login-dialog .registration-context { margin: 0 0 16px; color: #68715f; font-size: 13px; line-height: 1.7; }
.reservation-login-dialog .auth-navigation { margin: 18px 0 0; color: #68715f; font-size: 13px; }
.reservation-login-dialog .auth-navigation button { padding: 0 0 0 4px; border: 0; background: none; color: #52634a; font: inherit; text-decoration: underline; cursor: pointer; }
.reservation-login-dialog .auth-navigation button:disabled { cursor: default; opacity: .55; }
.reservation-login-dialog .auth-navigation button:focus-visible { outline: 2px solid #90a9ff; outline-offset: 4px; }
</style>
