import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './theme.css'

import App from './App.vue'
import { router } from './router'
import { onUnauthorized } from './platform/http'
import { logout } from './platform/auth/session'
import { saveContinuation } from './platform/auth/continuation'

onUnauthorized(() => {
  logout()
  saveContinuation({ intent: 'reauthenticate', path: router.currentRoute.value.fullPath, params: null })
  router.push({ name: 'login' })
})

createApp(App).use(router).use(ElementPlus).mount('#app')
