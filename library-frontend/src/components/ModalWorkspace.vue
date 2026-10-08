<template>
  <ElDialog
    :model-value="open"
    :title="active?.title || 'AI 助手'"
    class="modal-workspace"
    width="min(1240px, calc(100vw - 48px))"
    align-center
    :show-close="false"
    :close-on-click-modal="true"
    :before-close="requestClose"
    @mousedown="trackOutsidePress"
    @mouseup="trackOutsideRelease"
    @click="closeFromEmptySpace"
  >
    <div class="modal-workspace__panels" :class="{ 'is-assistant-only': !active, 'without-assistant': active && !active.assistant }">
      <section v-for="entry in entries" v-show="entry.id === active?.id" :key="entry.id" class="business-panel" data-testid="business-panel">
        <header class="modal-panel-header">
          <h2>{{ entry.title }}</h2>
          <button type="button" class="modal-panel-close" data-testid="business-close" :aria-label="`关闭${entry.title}`" @click="state.closePrimary">×</button>
        </header>
        <div class="business-dialog__content"><PanelContent :entry="entry" /></div>
      </section>
      <aside v-show="!active || active.assistant" class="modal-workspace__assistant">
        <HomeAiDialog :open="assistantOpen" @close="collapseAssistant" />
        <div ref="triggerHost" />
      </aside>
    </div>
  </ElDialog>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElDialog } from 'element-plus'
import HomeAiDialog from './HomeAiDialog.vue'
const props = defineProps({ state: { type: Object, required: true } })
const { entries, active, open, assistantOpen } = props.state
const triggerHost = ref(null)
watch([open, active, () => active.value?.assistant, triggerHost], () => {
  props.state.setTriggerTarget(open.value && active.value?.assistant ? triggerHost.value : null)
}, { flush: 'post' })
onBeforeUnmount(() => { props.state.setTriggerTarget(null) })
async function collapseAssistant() {
  assistantOpen.value = false
  await nextTick()
  props.state.triggerElement.value?.focus({ preventScroll: true })
}
const PanelContent = props => props.entry.render()
let pressedOutside = false
let releasedOutside = false
function isOutsidePanel(event) {
  return !event.target.closest('.business-panel, .home-ai-panel, .home-floating-ai')
}
function trackOutsidePress(event) {
  pressedOutside = isOutsidePanel(event)
  releasedOutside = false
}
function trackOutsideRelease(event) {
  releasedOutside = isOutsidePanel(event)
}
function closeFromEmptySpace(event) {
  // A drag that starts inside a card must not dismiss its unfinished form.
  if (pressedOutside && releasedOutside && isOutsidePanel(event)) props.state.closeAll()
  pressedOutside = releasedOutside = false
}
function requestClose(done) {
  props.state.closeAll()
  done()
}
</script>

<style>
.modal-workspace.el-dialog { padding: 0; background: transparent; box-shadow: none; }
.modal-workspace > .el-dialog__header { display: none; }
.modal-workspace > .el-dialog__body { padding: 0; }
.modal-workspace__panels { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); align-items: stretch; gap: 24px; height: min(720px, calc(100dvh - 64px)); }
.modal-workspace__panels.without-assistant { grid-template-columns: minmax(0, 1fr); }
.modal-workspace__panels.is-assistant-only { display: flex; justify-content: flex-end; }
.modal-workspace__assistant { display: flex; min-width: 0; min-height: 0; flex-direction: column; align-items: stretch; }
.is-assistant-only > .modal-workspace__assistant { width: calc((100% - 24px) / 3); }
.business-panel, .home-ai-panel { border: 1px solid #dbe4ff; border-radius: 20px; background: #fff; box-shadow: 0 20px 48px rgb(23 37 84 / 20%); }
.business-panel { display: flex; min-width: 0; min-height: 0; flex-direction: column; overflow: hidden; }
.modal-panel-header { display: flex; min-height: 76px; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 24px; border-bottom: 1px solid #e5eaff; background: linear-gradient(120deg, #f6f8ff, #fff 68%); }
.modal-panel-header h2 { margin: 0; color: #203b9d; font-size: 18px; }
.modal-panel-close { display: grid; width: 36px; height: 36px; flex: 0 0 auto; place-items: center; border: 1px solid #dbe4ff; border-radius: 10px; background: #fff; color: #3154d9; font-size: 24px; line-height: 1; cursor: pointer; }
.modal-panel-close:focus-visible { outline: 3px solid #90a9ff; outline-offset: 2px; }
.business-dialog__content { min-width: 0; min-height: 0; flex: 1; overflow: auto; padding: 24px; }
.modal-workspace .business-dialog__content > section { max-width: none; }
.business-dialog__content form { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.business-dialog__content .action-block { margin-bottom: 16px; }
.business-dialog__content .el-form-item { width: 100%; min-width: 0 !important; }
.business-dialog__content .el-form-item__content { min-width: 0; }
.business-dialog__content .inline-input, .business-dialog__content .reason-input, .business-dialog__content .search-input, .business-dialog__content .date-input { min-width: 0; }
.business-dialog__content [data-testid="walkin-form"] { flex-direction: column; align-items: stretch; }
.business-dialog__content input, .business-dialog__content .el-input { max-width: 100%; }
@media (max-width: 760px) {
  .modal-workspace.el-dialog { width: calc(100vw - 24px); }
  .modal-workspace__panels { grid-template-columns: minmax(0, 1fr); height: auto; gap: 12px; }
  .business-panel { max-height: 52dvh; }
  .modal-workspace__assistant { height: 36dvh; }
  .modal-workspace__panels.without-assistant > .business-panel { max-height: calc(100dvh - 48px); }
  .is-assistant-only > .modal-workspace__assistant { width: min(440px, 100%); height: min(720px, calc(100dvh - 48px)); }
  .modal-panel-header { min-height: 64px; padding: 12px 16px; }
  .business-dialog__content { padding: 16px; }
}
</style>
