<template>
  <section class="reading-view">
    <h1>阅读</h1>
    <p v-if="state.status === 'loading'" data-testid="reading-loading">加载中…</p>
    <template v-else-if="state.status === 'success'">
      <p class="reading-meta">内容类型：{{ content?.contentType }}</p>
      <article class="reading-pages" data-testid="reading-pages">
        <section v-for="page in content?.pages ?? []" :key="page.pageNo">
          <h2>第 {{ page.pageNo }} 页</h2>
          <p>{{ page.text }}</p>
        </section>
      </article>
      <el-button v-if="content?.nextCursor" data-testid="reading-next" @click="loadNext">下一页</el-button>
      <p v-else data-testid="reading-end">已经是最后一页。</p>
    </template>
    <el-empty v-else-if="notFound" data-testid="reading-not-found" description="阅读内容不存在或暂未公开" />
    <ErrorAlert v-if="state.status === 'failure' && !notFound" :failure="state.failure" />
    <p><RouterLink :to="`/titles/${titleId}`">返回书目详情</RouterLink></p>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind } from '@/platform/http'
import { useApiRequest } from '@/platform/http'
import { getReadingContent } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })

const content = ref(null)
const cursor = ref(null)
const { state, run } = useApiRequest()
const notFound = computed(() => state.status === 'failure' && state.failure?.kind === FailureKind.NOT_FOUND)

async function load(cursorValue) {
  const envelope = await run(() => getReadingContent({ titleId: props.titleId, cursor: cursorValue ?? undefined }))
  if (envelope) content.value = envelope.data
}

function loadNext() {
  cursor.value = content.value?.nextCursor ?? null
  load(cursor.value)
}

onMounted(() => load(null))
</script>

<style scoped>
.reading-view {
  max-width: 44rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.reading-pages section {
  margin-bottom: 1.25rem;
}
</style>
