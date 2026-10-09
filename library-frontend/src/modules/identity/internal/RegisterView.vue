<template>
  <section class="register-view">
    <h1>注册读者账户</h1>
    <p>自助注册即时生效，无需人工审核；一个账户绑定一个角色。</p>
    <RegisterForm />
    <p><RouterLink to="/login">返回登录</RouterLink></p>
  </section>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import RegisterForm from './RegisterForm.vue'
</script>

<style scoped>
.register-view {
  max-width: 24rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
</style>
