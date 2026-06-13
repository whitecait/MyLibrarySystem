import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 目标地址不变
        changeOrigin: true,
        // 关键在这里：重写路径时，在前面加上后端需要的上下文路径
        rewrite: (path) => path.replace(/^\/api/, '/springboot8393c')
      }
    }
  }
})