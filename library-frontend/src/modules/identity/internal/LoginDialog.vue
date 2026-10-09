<template>
  <ElDialog
    :model-value="open"
    title="登录后提交预约"
    width="min(420px, calc(100vw - 32px))"
    append-to-body
    align-center
    class="reservation-login-dialog"
    @update:model-value="value => { if (!value) emit('cancel') }"
  >
    <p class="login-context">预约信息已保留。</p>
    <LoginForm v-if="open" @authenticated="emit('authenticated')" />
  </ElDialog>
</template>

<script setup>
import { ElDialog } from 'element-plus'
import LoginForm from './LoginForm.vue'
defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['authenticated', 'cancel'])
</script>

<style>
.reservation-login-dialog.el-dialog { border-radius: 16px; padding: 24px; }
.reservation-login-dialog .login-context { margin: 0 0 20px; color: #68715f; font-size: 13px; }
.reservation-login-dialog form { display: flex; flex-direction: column; gap: 4px; }
.reservation-login-dialog .el-form-item { display: flex; flex-direction: column; align-items: stretch; gap: 6px; margin-bottom: 14px; }
.reservation-login-dialog .el-form-item__label { justify-content: flex-start; }
.reservation-login-dialog .el-button { min-height: 40px; margin: 0; background: #52634a; border-color: #52634a; }
</style>
