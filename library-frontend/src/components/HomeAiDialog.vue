<template>
  <section v-show="open" id="home-ai-dialog" class="home-ai-panel" :aria-labelledby="titleId" :aria-describedby="descriptionId">
    <header class="home-ai-panel__header modal-panel-header">
      <div class="home-ai-panel__heading">
        <h2 :id="titleId">AI 助手</h2>
        <p :id="descriptionId">查找图书，了解借阅与预约</p>
      </div>
      <button class="home-ai-panel__close modal-panel-close" type="button" aria-label="关闭 AI 助手" @click="emit('close')">×</button>
    </header>
    <div v-if="hasOpened" class="home-ai-panel__body">
      <AiAssistantView :key="assistantKey" compact />
    </div>
  </section>
</template>

<script setup>
import { ref, useId, watch } from 'vue'
import { AiAssistantView } from '@/modules/ai'
import { getSessionState } from '@/platform/auth/session'
const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
const hasOpened = ref(false)
const assistantKey = ref(0)
const session = getSessionState()
const componentId = useId()
const titleId = `home-ai-dialog-title-${componentId}`
const descriptionId = `home-ai-dialog-description-${componentId}`
watch(() => props.open, open => { if (open) hasOpened.value = true }, { immediate: true })
watch(() => [session.token, session.actor?.accountId, session.actor?.role], () => {
  assistantKey.value += 1
  if (!props.open) hasOpened.value = false
}, { flush: 'sync' })
</script>

<style scoped>
.home-ai-panel {
  display: flex;
  width: 100%;
  height: 100%;
  max-height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #dbe4ff;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 1.25rem 3rem rgb(23 37 84 / 24%);
  transform-origin: bottom right;
}

.home-ai-panel__header {
  display: flex;
  min-height: 4.75rem;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e5eaff;
  background: linear-gradient(120deg, #f6f8ff, #fff 68%);
}

.home-ai-panel__heading h2 {
  margin: 0;
  color: #203b9d;
  font-size: 1.125rem;
  line-height: 1.35;
}

.home-ai-panel__heading p {
  margin: 0.15rem 0 0;
  color: #526274;
  font-size: 0.82rem;
}

.home-ai-panel__close {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #dbe4ff;
  border-radius: 0.65rem;
  background: #fff;
  color: #3154d9;
  cursor: pointer;
  font-family: inherit;
  font-size: 1.5rem;
  line-height: 1;
}

.home-ai-panel__close:hover {
  background: #eef2ff;
}

.home-ai-panel__close:focus-visible {
  outline: 3px solid rgb(49 84 217 / 35%);
  outline-offset: 2px;
}

.home-ai-panel__body {
  display: flex;
  min-height: 0;
  flex: 1;
  overflow: hidden;
  padding: 0.75rem 1rem 1rem;
}

.home-ai-panel__body :deep(.ai-assistant-view) {
  width: 100%;
  max-width: none;
  min-height: 0;
  flex: 1;
}

</style>
