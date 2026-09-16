<template>
  <el-alert type="error" :closable="false" data-testid="error-alert">
    <template #title>{{ title }}</template>
    <span v-if="failure.message && failure.message !== title">{{ failure.message }}</span>
  </el-alert>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ failure: { type: Object, required: true } })

const KIND_LABELS = {
  UNAUTHENTICATED: '请先登录',
  FORBIDDEN: '没有执行该操作的权限',
  NOT_FOUND: '对象不存在或不可见',
  INVALID: '提交的字段有误',
  CONFLICT: '状态冲突',
  REJECTED: '业务规则拒绝',
  QUOTA_EXHAUSTED: '额度已耗尽',
  SYSTEM: '系统失败',
  NETWORK: '网络不可达',
  TIMEOUT: '等待超时，请稍后重试',
  CANCELLED: '已取消',
  MALFORMED: '响应异常',
}

const title = computed(() => {
  const label = KIND_LABELS[props.failure.kind] ?? '系统失败'
  return props.failure.code ? `${label}（${props.failure.code}）` : label
})
</script>
