<template>
  <ModalWorkspace v-if="!shared" :state="workspace" />
</template>

<script setup>
import { inject, onBeforeUnmount, useSlots, watch } from 'vue'
import ModalWorkspace from './ModalWorkspace.vue'
import { createModalWorkspace, modalWorkspaceKey } from './modalWorkspace'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  assistant: { type: Boolean, default: true },
})
const emit = defineEmits(['close'])
const slots = useSlots()
const shared = inject(modalWorkspaceKey, null)
const workspace = shared ?? createModalWorkspace()
const id = Symbol('transactionPanel')

watch(() => props.open, open => {
  if (open) workspace.show({
    id,
    get title() { return props.title },
    // The application shell owns the assistant. An isolated form has no second assistant.
    get assistant() { return Boolean(shared) && props.assistant },
    render: () => [slots.default?.(), slots.footer?.()],
    close: () => emit('close'),
  })
  else workspace.dismiss(id)
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => workspace.dismiss(id))
</script>
