import { computed, ref, shallowRef } from 'vue'

export const modalWorkspaceKey = Symbol('modalWorkspace')

// All transaction panels use one overlay and the same floating assistant instance.
export function createModalWorkspace() {
  const entries = shallowRef([])
  const assistantOpen = ref(false)
  const triggerTarget = shallowRef(null)
  const triggerElement = shallowRef(null)
  const active = computed(() => entries.value.at(-1) ?? null)
  const open = computed(() => Boolean(active.value) || assistantOpen.value)
  let previousAssistantOpen = false

  function show(entry) {
    if (entries.value.length === 0) previousAssistantOpen = assistantOpen.value
    entries.value = [...entries.value.filter(item => item.id !== entry.id), entry]
    assistantOpen.value = entry.assistant
  }

  function dismiss(id) {
    if (!entries.value.some(entry => entry.id === id)) return
    const wasActive = active.value?.id === id
    entries.value = entries.value.filter(entry => entry.id !== id)
    if (entries.value.length === 0) assistantOpen.value = previousAssistantOpen
    else if (wasActive) assistantOpen.value = active.value.assistant
  }

  function setTriggerTarget(target) {
    triggerTarget.value = target
  }

  function closePrimary() {
    const entry = active.value
    if (!entry) return
    entry.close()
    dismiss(entry.id)
  }

  function closeAll() {
    const closingEntries = [...entries.value].reverse()
    previousAssistantOpen = false
    entries.value = []
    assistantOpen.value = false
    for (const entry of closingEntries) entry.close()
  }

  return { entries, active, open, assistantOpen, triggerTarget, triggerElement, setTriggerTarget, show, dismiss, closePrimary, closeAll }
}
