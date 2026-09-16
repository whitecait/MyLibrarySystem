<template>
  <section class="home">
    <h1>MyLibrarySystem</h1>
    <p>M5 前端运行骨架就绪：统一信封、认证头、幂等键与 HTTP/Mock 适配器已接入。</p>
    <p>
      当前适配器模式：
      <el-tag data-testid="adapter-mode">{{ mode }}</el-tag>
    </p>
  </section>
</template>

<script setup>
import { resolveDefaultMode } from '@/platform/http'

const mode = resolveDefaultMode()
</script>

<style scoped>
.home {
  max-width: 48rem;
}
</style>
