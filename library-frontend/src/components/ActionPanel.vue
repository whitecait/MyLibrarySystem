<template>
  <section class="action-entry">
    <div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div>
    <el-button type="primary" :data-testid="`${entryId}-open`" @click="open = true">{{ buttonLabel }}</el-button>
    <BusinessDialog :open="open" :title="title" :assistant="assistant" @close="open = false">
      <slot />
    </BusinessDialog>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import BusinessDialog from './BusinessDialog.vue'
const props = defineProps({
  title: { type: String, required: true },
  entryId: { type: String, required: true },
  description: { type: String, default: '' },
  buttonLabel: { type: String, default: '开始办理' },
  activeKey: { type: [String, Number], default: null },
  assistant: { type: Boolean, default: true },
})
const open = ref(props.activeKey != null)
watch(() => props.activeKey, (key) => { if (key != null) open.value = true })
</script>

<style scoped>
.action-entry { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; padding: 24px; border: 1px solid #dce4f2; border-radius: 16px; background: #fff; }
.action-entry h2 { margin: 0; color: #172554; font-size: 20px; }
.action-entry p { margin: 10px 0 0; color: #64748b; }
</style>
