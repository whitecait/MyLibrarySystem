<template>
  <section class="title-detail-view">
    <p v-if="state.status === 'loading'" role="status">加载中…</p>
    <el-empty v-else-if="notFound" data-testid="title-not-found" description="书目不存在或不可见" />
    <template v-else-if="state.status === 'success' && detail">
      <div class="detail-topline"><span>馆藏书目</span><span>CATALOG / {{ detail.titleId }}</span></div>
      <header class="detail-hero">
        <div class="book-graphic" aria-hidden="true">
          <span class="book-imprint">云图 · 馆藏</span>
          <strong>{{ detail.title }}</strong>
          <span class="book-author">{{ authors }}</span>
          <span class="book-rule"></span>
          <span class="book-category">{{ category }}</span>
        </div>
        <div class="hero-copy">
          <span class="title-category">{{ category }}</span>
          <h1 data-testid="title-detail-name">{{ detail.title }}</h1>
          <p class="title-authors">{{ authors }}</p>
          <span
            class="title-availability"
            :class="{ 'is-unavailable': detail.availability === 'UNAVAILABLE', 'is-unknown': detail.availability === 'UNKNOWN' }"
            data-testid="title-availability"
          >{{ availabilityLabel(detail.availability) }}</span>
          <p class="availability-note">可借状态为书目级展示，不构成借阅承诺。</p>
          <div class="title-links">
            <RouterLink
              v-if="detail.hasReadingContent"
              class="reading-link"
              :to="`/titles/${detail.titleId}/read`"
              data-testid="title-reading-entry"
            >开始阅读 <span aria-hidden="true">↗</span></RouterLink>
            <RouterLink
              :to="`/titles/${detail.titleId}/calendar`"
              data-testid="title-calendar-entry"
            >预约日历</RouterLink>
          </div>
        </div>
      </header>
      <section class="title-information" aria-labelledby="title-information-heading">
        <div class="section-heading"><span aria-hidden="true">01</span><h2 id="title-information-heading">书目信息</h2></div>
        <dl class="title-meta" data-testid="title-metadata">
          <div v-for="item in metadata" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div>
        </dl>
      </section>
      <ReservePanel :title-id="detail.titleId" data-testid="title-reserve-panel" />
    </template>
    <ErrorAlert v-if="state.status === 'failure' && !notFound" :failure="state.failure" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { FailureKind, useApiRequest } from '@/platform/http'
import { ReservePanel } from '../../circulation'
import { getTitle } from '../index'

const props = defineProps({ titleId: { type: String, required: true } })
const detail = ref(null)
const { state, run } = useApiRequest()
const notFound = computed(() => state.status === 'failure' && state.failure?.kind === FailureKind.NOT_FOUND)
const authors = computed(() => detail.value?.authors?.join('、') || '作者未提供')
const category = computed(() => detail.value?.category || '分类未提供')
const metadata = computed(() => detail.value ? [
  { label: '作者', value: authors.value },
  { label: '分类', value: category.value },
  { label: '出版社', value: detail.value.publisher || '未提供' },
  { label: '出版年', value: detail.value.publishYear ?? '未提供' },
  { label: 'ISBN', value: detail.value.isbn || '未提供' },
  { label: '定价', value: (detail.value.priceCents / 100).toFixed(2) + ' 元' },
] : [])

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
  --detail-accent: #52634a;
  --detail-muted: #68715f;
  --detail-line: #e2e6d9;
  --detail-surface: #fafbf6;
  --detail-tint: #eef1e7;
  --detail-ink: #273128;
  width: 100%;
  min-width: 0;
  color: var(--detail-ink);
  container: title-detail / inline-size;
}
.detail-topline { display: flex; justify-content: space-between; gap: 12px; padding-bottom: 18px; border-bottom: 1px solid var(--detail-line); color: var(--detail-muted); font-size: 10px; letter-spacing: 2px; }
.detail-hero { display: grid; grid-template-columns: minmax(0, .76fr) minmax(0, 1.24fr); gap: 30px; align-items: center; padding: 30px 0; }
.book-graphic { min-width: 0; min-height: 250px; padding: 23px 22px; border-left: 8px solid #d6ddc8; border-radius: 2px 10px 10px 2px; background: #e8eddf; box-shadow: 8px 10px 0 #f1f3eb; display: flex; flex-direction: column; gap: 20px; }
.book-imprint { font-size: 10px; letter-spacing: 3px; color: #627056; }
.book-graphic strong { font-family: 'Noto Serif CJK SC', 'Songti SC', SimSun, serif; font-size: 26px; font-weight: 500; line-height: 1.5; overflow-wrap: anywhere; }
.book-author, .book-category { color: #65725d; font-size: 11px; line-height: 1.7; overflow-wrap: anywhere; }
.book-rule { width: 32px; height: 1px; margin-top: auto; background: #65725d; }
.hero-copy { min-width: 0; }
.title-category { display: block; color: var(--detail-muted); font-size: 11px; line-height: 1.7; margin-bottom: 12px; overflow-wrap: anywhere; }
h1 { margin: 0; font-family: 'Noto Serif CJK SC', 'Songti SC', SimSun, serif; font-size: clamp(24px, 5cqi, 34px); font-weight: 500; line-height: 1.5; overflow-wrap: anywhere; }
.title-authors { margin: 14px 0 20px; color: var(--detail-muted); font-size: 13px; line-height: 1.8; overflow-wrap: anywhere; }
.title-availability { display: inline-block; border: 1px solid #dbe2cd; border-radius: 20px; padding: 5px 10px; background: #f0f4e9; color: #536743; font-size: 12px; }
.title-availability.is-unavailable, .title-availability.is-unknown { background: #f5f3ee; color: #786950; border-color: #e7e0d1; }
.availability-note { margin: 14px 0 24px; color: var(--detail-muted); font-size: 11px; line-height: 1.8; }
.title-links { display: flex; flex-wrap: wrap; gap: 9px; }
.title-links a { border: 1px solid #d9dfcf; border-radius: 9px; padding: 11px 15px; color: var(--detail-accent); background: #fff; font-size: 13px; text-decoration: none; }
.title-links .reading-link { background: var(--detail-accent); color: #fff; border-color: var(--detail-accent); }
.reading-link span { margin-left: 16px; }
.title-information { margin-bottom: 26px; }
.section-heading { display: flex; align-items: center; gap: 12px; padding-top: 20px; border-top: 1px solid var(--detail-line); }
.section-heading > span { color: var(--detail-muted); font-size: 11px; }
h2 { margin: 0 0 4px; font-size: 15px; font-weight: 500; }
.title-meta { margin: 0; }
.title-meta > div { display: grid; grid-template-columns: 5em minmax(0, 1fr); gap: 18px; padding: 12px 0; border-bottom: 1px solid var(--detail-line); }
.title-meta dt { color: var(--detail-muted); font-size: 12px; line-height: 1.8; }
.title-meta dd { margin: 0; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.title-links a:focus-visible { outline-color: var(--detail-accent); }
@container title-detail (max-width: 430px) {
  .detail-hero { grid-template-columns: minmax(0, 1fr); gap: 24px; }
  .book-graphic { min-height: 170px; padding: 18px 20px; }
  .book-graphic strong { font-size: 22px; }
  .book-category { display: none; }
}
@container title-detail (max-width: 300px) {
  .detail-topline { flex-wrap: wrap; }
  .title-meta > div { gap: 10px; }
}
</style>
