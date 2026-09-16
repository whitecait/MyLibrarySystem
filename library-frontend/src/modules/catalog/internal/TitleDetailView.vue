<template>
  <section class="title-detail-view">
    <p v-if="state.status === 'loading'">加载中…</p>
    <el-empty v-else-if="notFound" data-testid="title-not-found" description="书目不存在或不可见" />
    <template v-else-if="state.status === 'success' && detail">
      <h1 data-testid="title-detail-name">{{ detail.title }}</h1>
      <dl class="title-meta">
        <div><dt>作者</dt><dd>{{ detail.authors.join('、') }}</dd></div>
        <div><dt>分类</dt><dd>{{ detail.category }}</dd></div>
        <div><dt>ISBN</dt><dd>{{ detail.isbn }}</dd></div>
        <div v-if="detail.publisher"><dt>出版社</dt><dd>{{ detail.publisher }}</dd></div>
        <div v-if="detail.publishYear != null"><dt>出版年</dt><dd>{{ detail.publishYear }}</dd></div>
        <div><dt>定价</dt><dd>{{ (detail.priceCents / 100).toFixed(2) }} 元</dd></div>
        <div>
          <dt>可借状态</dt>
          <dd><el-tag data-testid="title-availability">{{ availabilityLabel(detail.availability) }}</el-tag></dd>
        </div>
      </dl>
      <p class="availability-note">可借状态为书目级展示，不构成借阅承诺。</p>
      <RouterLink
        v-if="detail.hasReadingContent"
        :to="`/titles/${detail.titleId}/read`"
        data-testid="title-reading-entry"
      >
        开始阅读
      </RouterLink>
    </template>
    <ErrorAlert v-if="state.status === 'failure' && !notFound" :failure="state.failure" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind } from '@/platform/http'
import { useApiRequest } from '@/platform/http'
import { getTitle } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })

const detail = ref(null)
const { state, run } = useApiRequest()
const notFound = computed(() => state.status === 'failure' && state.failure?.kind === FailureKind.NOT_FOUND)

const AVAILABILITY_LABELS = { AVAILABLE: '可借', UNAVAILABLE: '不可借', UNKNOWN: '未知' }
function availabilityLabel(availability) {
  return AVAILABILITY_LABELS[availability] ?? availability
}

onMounted(async () => {
  const envelope = await run(() => getTitle({ titleId: props.titleId }))
  if (envelope) detail.value = envelope.data
})
</script>

<style scoped>
.title-detail-view {
  max-width: 40rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.title-meta {
  display: grid;
  gap: 0.5rem;
  margin: 0;
}

.title-meta div {
  display: flex;
  gap: 1rem;
}

.title-meta dt {
  font-weight: 700;
  min-width: 4rem;
}

.title-meta dd {
  margin: 0;
}

.availability-note {
  color: #526274;
  font-size: 0.85rem;
}
</style>
