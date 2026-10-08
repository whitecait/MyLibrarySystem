<template>
  <section class="home" aria-label="图书馆首页">
    <section class="home-hero" aria-labelledby="home-title">
      <div class="home-hero__copy">
        <p class="home-eyebrow">馆藏检索与读者服务</p>
        <h1 id="home-title">找到下一本想读的书</h1>
        <p class="home-hero__description">搜索馆藏书目，查看借阅情况，或预约一本好书。</p>
      </div>

      <form class="home-search" data-testid="home-search-form" @submit.prevent="submitSearch">
        <Search aria-hidden="true" />
        <input
          v-model="searchText"
          data-testid="home-search-input"
          type="search"
          placeholder="搜索书名、作者或分类"
          aria-label="搜索书名、作者或分类"
        />
        <button data-testid="home-search-submit" type="submit">搜索</button>
      </form>

      <div class="home-shortcuts" aria-label="随机图书" data-testid="home-suggestions">
        <span>随便看看：</span>
        <span v-if="discoveryState.status === 'loading'">正在选书…</span>
        <RouterLink
          v-for="book in suggestions"
          :key="book.titleId"
          :to="`/titles/${book.titleId}`"
          :aria-label="`查看《${book.title}》详情`"
          data-testid="home-suggestion"
        >{{ book.title }}</RouterLink>
        <span v-if="discoveryState.status === 'success' && !suggestions.length">暂无馆藏图书</span>
      </div>
    </section>

    <section
      v-if="showReaderShortcuts"
      class="home-summary"
      data-testid="reader-service-shortcuts"
      aria-label="读者服务入口"
    >
      <RouterLink to="/me/business" class="home-summary-card">
        <Reading aria-hidden="true" />
        <span><strong>当前借阅</strong><small>查看借阅事项</small></span>
        <b>查看 →</b>
      </RouterLink>
      <RouterLink to="/me/business" class="home-summary-card">
        <Tickets aria-hidden="true" />
        <span><strong>预约与候补</strong><small>打开业务页面查看</small></span>
        <b>查看 →</b>
      </RouterLink>
      <RouterLink to="/notifications" class="home-summary-card">
        <Bell aria-hidden="true" />
        <span><strong>通知</strong><small>查看账户通知</small></span>
        <b>查看 →</b>
      </RouterLink>
    </section>

    <section class="home-catalog" aria-labelledby="home-catalog-title">
      <div class="home-section-heading">
        <div>
          <p class="home-eyebrow home-eyebrow--light">LIBRARY COLLECTION</p>
          <h2 id="home-catalog-title">馆藏图书</h2>
        </div>
        <span v-if="books.length">已展示 {{ books.length }} 本</span>
      </div>

      <div class="home-filters" data-testid="home-filter-panel" :aria-busy="discoveryState.status === 'loading'">
        <fieldset class="home-filter-group" data-testid="home-category-filter">
          <legend>分类 <small v-if="categories.length">{{ categories.length }} 项</small></legend>
          <div class="home-filter-options">
            <button type="button" :aria-pressed="category === ''" @click="selectCategory('')">全部分类</button>
            <button
              v-for="item in categories"
              :key="item"
              type="button"
              :aria-pressed="category === item"
              data-testid="home-category-option"
              @click="selectCategory(item)"
            >{{ item }}</button>
          </div>
        </fieldset>
        <fieldset class="home-filter-group" data-testid="home-availability-filter">
          <legend>可借状态</legend>
          <div class="home-filter-options">
            <button type="button" :aria-pressed="availability === ''" @click="selectAvailability('')">全部状态</button>
            <button
              v-for="item in availabilities"
              :key="item"
              type="button"
              :aria-pressed="availability === item"
              data-testid="home-availability-option"
              @click="selectAvailability(item)"
            >{{ availabilityLabel(item) }}</button>
          </div>
        </fieldset>
        <p v-if="discoveryState.status === 'loading'" class="home-filter-status" role="status">正在加载筛选选项…</p>
        <div v-else-if="discoveryState.status === 'failure'" class="home-filter-error" data-testid="home-discovery-error">
          <ErrorAlert :failure="discoveryState.failure" />
          <button type="button" data-testid="home-discovery-retry" @click="loadDiscovery">重新加载选项</button>
        </div>
      </div>

      <p v-if="searchState.status === 'loading'" class="home-status" data-testid="home-loading">正在加载书目…</p>
      <template v-else-if="searchState.status === 'success' && books.length === 0">
        <el-empty data-testid="home-empty" description="没有找到符合条件的书目" />
      </template>
      <div v-else-if="searchState.status === 'success'" class="home-book-grid" data-testid="home-results">
        <article v-for="book in books" :key="book.titleId" class="home-book-card" :data-testid="`home-card-${book.titleId}`">
          <RouterLink class="home-book-cover" :to="`/titles/${book.titleId}`" :aria-label="`查看《${book.title}》详情`">
            <small>{{ book.category || '馆藏图书' }}</small>
            <strong>{{ book.title }}</strong>
            <span>{{ authorLabel(book.authors) }}</span>
          </RouterLink>
          <div class="home-book-card__body">
            <span :class="['home-availability', availabilityClass(book.availability)]">
              {{ availabilityLabel(book.availability) }}
            </span>
            <h3>{{ book.title }}</h3>
            <p>{{ authorLabel(book.authors) }}</p>
            <footer>
              <RouterLink :to="`/titles/${book.titleId}`" :data-testid="`home-detail-${book.titleId}`">查看详情</RouterLink>
              <RouterLink class="is-primary" :to="`/titles/${book.titleId}`" :data-testid="`home-reserve-${book.titleId}`">
                预约
              </RouterLink>
            </footer>
          </div>
        </article>
      </div>

      <div v-if="searchState.status === 'failure'" class="home-error-state" data-testid="home-error-state">
        <ErrorAlert :failure="searchState.failure" />
        <button data-testid="home-retry" type="button" @click="submitSearch">重试</button>
      </div>

      <div ref="feedSentinel" class="home-feed-status" aria-live="polite">
        <p v-if="moreState.status === 'loading'">正在加载更多书目…</p>
        <template v-else-if="moreState.status === 'failure'">
          <ErrorAlert :failure="moreState.failure" />
          <button type="button" data-testid="home-load-more" @click="loadMore">重试加载</button>
        </template>
        <button v-else-if="nextCursor" type="button" data-testid="home-load-more" @click="loadMore">继续加载</button>
        <p v-else-if="books.length && searchState.status === 'success'" data-testid="home-feed-end">已展示全部结果</p>
      </div>
    </section>

  </section>
</template>

<script setup>
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { backgroundModalKey } from '@/router/presentation'
import { RouterLink } from 'vue-router'
import { Bell, Reading, Search, Tickets } from '@element-plus/icons-vue'
import ErrorAlert from '@/platform/components/ErrorAlert.vue'
import { getRole, isAuthenticated } from '@/platform/auth/session'
import { getCatalogDiscovery, searchTitles } from '@/modules/catalog'
import { useApiRequest } from '@/platform/http'

const PAGE_SIZE = 4
const AVAILABILITY_LABELS = { AVAILABLE: '可借', UNAVAILABLE: '不可借', UNKNOWN: '未知' }

const searchText = ref('')
const category = ref('')
const availability = ref('')
const books = ref([])
const nextCursor = ref(null)
const feedSentinel = ref(null)
const routeModalOpen = inject(backgroundModalKey, ref(false))
const moreRequest = ref(useApiRequest())
const moreState = computed(() => moreRequest.value.state)
let observer = null
const appliedFilters = ref({ q: undefined, category: undefined, availability: undefined })
const activeRequest = ref(useApiRequest())
const searchState = computed(() => activeRequest.value.state)
const categories = ref([])
const availabilities = ref([])
const suggestions = ref([])
const { state: discoveryState, run: runDiscovery } = useApiRequest()
const discoveryController = new AbortController()
const showReaderShortcuts = computed(() => isAuthenticated() && getRole() === 'READER')

let requestSequence = 0
let isUnmounted = false

function filterSnapshot() {
  return {
    q: searchText.value.trim() || undefined,
    category: category.value.trim() || undefined,
    availability: availability.value || undefined,
  }
}

async function submitSearch() {
  const filters = filterSnapshot()
  appliedFilters.value = filters
  const sequence = ++requestSequence
  const request = useApiRequest()
  activeRequest.value = request
  moreRequest.value = useApiRequest()
  books.value = []
  nextCursor.value = null
  const envelope = await request.run(() => searchTitles({ ...filters, limit: PAGE_SIZE }))
  if (isUnmounted || sequence !== requestSequence || !envelope) return
  books.value = envelope.data.items ?? []
  nextCursor.value = envelope.data.nextCursor ?? null
  await nextTick()
  observeFeed()
}

async function loadMore() {
  if (!nextCursor.value || searchState.value.status === 'loading' || moreState.value.status === 'loading'
    || routeModalOpen.value) return
  const sequence = requestSequence
  const cursor = nextCursor.value
  const request = useApiRequest()
  moreRequest.value = request
  const envelope = await request.run(() => searchTitles({ ...appliedFilters.value, cursor, limit: PAGE_SIZE }))
  if (isUnmounted || sequence !== requestSequence || !envelope) return
  const existing = new Set(books.value.map(book => book.titleId))
  books.value = [...books.value, ...(envelope.data.items ?? []).filter(book => !existing.has(book.titleId))]
  nextCursor.value = envelope.data.nextCursor === cursor ? null : (envelope.data.nextCursor ?? null)
  await nextTick()
  observeFeed()
}

function observeFeed() {
  observer?.disconnect()
  if (nextCursor.value && moreState.value.status !== 'failure'
    && !routeModalOpen.value && feedSentinel.value) observer?.observe(feedSentinel.value)
}
watch(routeModalOpen, () => { void nextTick(observeFeed) })

async function loadDiscovery() {
  if (discoveryState.status === 'loading') return
  const data = await runDiscovery(() => getCatalogDiscovery({ signal: discoveryController.signal }))
  if (isUnmounted || !data) return
  categories.value = data.categories
  availabilities.value = data.availabilities
  suggestions.value = data.suggestions
}

function selectCategory(value) {
  if (category.value === value) return
  category.value = value
  void submitSearch()
}

function selectAvailability(value) {
  if (availability.value === value) return
  availability.value = value
  void submitSearch()
}

function authorLabel(authors) {
  return Array.isArray(authors) && authors.length ? authors.join('、') : '作者信息暂缺'
}

function availabilityLabel(value) {
  return AVAILABILITY_LABELS[value] ?? '未知'
}

function availabilityClass(value) {
  if (value === 'AVAILABLE') return 'is-available'
  if (value === 'UNAVAILABLE') return 'is-unavailable'
  return 'is-unknown'
}

onMounted(() => {
  if (typeof IntersectionObserver === 'function') {
    observer = new IntersectionObserver(entries => {
      if (moreState.value.status !== 'failure' && entries.some(entry => entry.isIntersecting)) void loadMore()
    }, { rootMargin: '240px' })
  }
  void submitSearch()
  void loadDiscovery()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  isUnmounted = true
  discoveryController.abort()
  requestSequence += 1
})
</script>

<style scoped>
.home-book-cover { text-decoration: none; }
.home-feed-status { display: grid; justify-items: center; gap: 12px; padding: 28px; color: #64748b; }
.home-feed-status button { border: 1px solid #dce4f2; border-radius: 999px; background: white; color: #2445b4; padding: 10px 24px; cursor: pointer; }

.home,
.home * {
  box-sizing: border-box;
}

.home {
  width: 100%;
  min-width: 0;
  color: #192238;
}

.home-hero {
  position: relative;
  min-height: 330px;
  margin-top: 28px;
  padding: 55px 64px;
  overflow: hidden;
  color: #fff;
  border-radius: 22px;
  background: linear-gradient(118deg, #172c69 0%, #2448bd 60%, #5470d9 100%);
  box-shadow: 0 24px 60px rgb(32 61 149 / 17%);
}

.home-hero::before,
.home-hero::after {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.home-hero::before {
  top: -170px;
  right: -80px;
  width: 440px;
  height: 440px;
}

.home-hero::after {
  right: 80px;
  bottom: -250px;
  width: 510px;
  height: 510px;
}

.home-hero__copy,
.home-search,
.home-shortcuts {
  position: relative;
  z-index: 1;
}

.home-eyebrow {
  margin: 0 0 11px;
  color: #aebfff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .16em;
}

.home-hero h1 {
  margin: 0;
  font-family: Georgia, 'Noto Serif SC', serif;
  font-size: 42px;
  font-weight: 600;
}

.home-hero__description {
  margin: 13px 0 0;
  color: #d6def7;
  font-size: 14px;
}

.home-search {
  display: flex;
  align-items: center;
  width: min(720px, 100%);
  height: 58px;
  margin-top: 34px;
  overflow: hidden;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 13px 30px rgb(10 23 66 / 24%);
}

.home-search > svg {
  flex: 0 0 auto;
  width: 20px;
  margin: 0 14px 0 19px;
  color: #8a94a8;
}

.home-search input {
  min-width: 0;
  height: 100%;
  flex: 1;
  padding: 0;
  color: #25304a;
  border: 0;
  outline: 0;
  font-size: 15px;
}

.home-search button {
  align-self: stretch;
  min-width: 92px;
  color: #fff;
  border: 0;
  background: #17399e;
  font-weight: 700;
  cursor: pointer;
}

.home-shortcuts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  color: #d6def7;
  font-size: 11px;
}

.home-shortcuts a {
  max-width: 100%;
  padding: 5px 10px;
  overflow-wrap: anywhere;
  text-decoration: none;
  line-height: 1.5;
  color: #dce4ff;
  border: 1px solid rgb(255 255 255 / 17%);
  border-radius: 12px;
  background: rgb(255 255 255 / 7%);
  font-size: inherit;
  cursor: pointer;
}

.home-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 15px;
  margin-top: 22px;
}

.home-summary-card {
  display: flex;
  min-width: 0;
  min-height: 84px;
  align-items: center;
  gap: 13px;
  padding: 16px 20px;
  color: #34405a;
  border: 1px solid #e5e9f0;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px rgb(31 43 78 / 4%);
  text-decoration: none;
}

.home-summary-card > svg {
  width: 24px;
  flex: 0 0 auto;
  color: #4162d3;
}

.home-summary-card > span {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.home-summary-card strong {
  font-family: Georgia, 'Noto Serif SC', serif;
  font-size: 16px;
}

.home-summary-card small {
  margin-top: 3px;
  color: #7c8597;
  font-size: 11px;
}

.home-summary-card b {
  color: #3154d9;
  font-size: 11px;
  white-space: nowrap;
}

.home-catalog {
  margin: 48px 0;
}

.home-section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 17px;
}

.home-eyebrow--light {
  margin-bottom: 6px;
  color: #4b63b7;
  font-size: 10px;
  letter-spacing: .14em;
}

.home-section-heading h2 {
  margin: 0;
  font-family: Georgia, 'Noto Serif SC', serif;
  font-size: 28px;
}

.home-section-heading > span {
  color: #7f889b;
  font-size: 12px;
}

.home-filters {
  display: grid;
  gap: 18px;
  padding: 20px;
  border: 1px solid #e5e9f0;
  border-radius: 12px;
  background: #fff;
}

.home-filter-group {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.home-filter-group legend {
  margin-bottom: 10px;
  padding: 0;
  color: #59647a;
  font-size: 12px;
  font-weight: 700;
}

.home-filter-group legend small {
  margin-left: 6px;
  color: #7f889b;
  font-weight: 400;
}

.home-filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.home-filter-options button,
.home-filter-error > button {
  max-width: 100%;
  min-height: 32px;
  padding: 6px 11px;
  color: #566077;
  border: 1px solid #dde2ea;
  border-radius: 8px;
  background: #fff;
  font-size: 12px;
  line-height: 1.5;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.home-filter-options button[aria-pressed='true'] {
  color: #2445b4;
  border-color: #8ca1ed;
  background: #edf2ff;
  font-weight: 700;
}

.home-filter-options button:hover,
.home-shortcuts a:hover {
  border-color: #8ca1ed;
}

.home-filter-options button:focus-visible,
.home-shortcuts a:focus-visible {
  outline: 2px solid #8ca1ed;
  outline-offset: 3px;
}

.home-filter-status {
  margin: 0;
  color: #788196;
  font-size: 12px;
}

.home-filter-error {
  display: grid;
  justify-items: start;
  gap: 10px;
}

.home-status {
  margin: 22px 0;
  color: #687189;
  text-align: center;
}

.home-book-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 17px;
  margin-top: 18px;
}

.home-book-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e4e8ef;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 7px 24px rgb(31 43 78 / 5%);
  transition: transform .18s ease, box-shadow .18s ease;
}

.home-book-card:hover {
  box-shadow: 0 15px 38px rgb(31 43 78 / 10%);
  transform: translateY(-3px);
}

.home-book-cover {
  display: flex;
  height: 205px;
  flex-direction: column;
  justify-content: flex-end;
  padding: 22px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(145deg, #425eaa 0%, #243b79 52%, #17264e 100%);
}

.home-book-cover small {
  margin-bottom: auto;
  color: rgb(255 255 255 / 72%);
  font-size: 10px;
}

.home-book-cover strong {
  display: -webkit-box;
  overflow: hidden;
  font-family: Georgia, 'Noto Serif SC', serif;
  font-size: 19px;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.home-book-cover span {
  margin-top: 7px;
  overflow: hidden;
  color: rgb(255 255 255 / 78%);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-book-card__body {
  padding: 15px;
}

.home-availability {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 10px;
  font-size: 9px;
  font-weight: 700;
}

.home-availability.is-available {
  color: #168563;
  background: #e6f6f0;
}

.home-availability.is-unavailable {
  color: #a45d20;
  background: #fff0e0;
}

.home-availability.is-unknown {
  color: #687189;
  background: #edf0f4;
}

.home-book-card h3 {
  display: -webkit-box;
  height: 42px;
  margin: 13px 0 4px;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.home-book-card p {
  height: 17px;
  margin: 0;
  overflow: hidden;
  color: #7d8597;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-book-card footer {
  display: flex;
  gap: 8px;
  margin-top: 15px;
}

.home-book-card footer a {
  display: grid;
  min-width: 0;
  height: 35px;
  flex: 1;
  place-items: center;
  color: #46516a;
  border: 1px solid #dce1e9;
  border-radius: 8px;
  background: #fff;
  font-size: 11px;
  font-weight: 700;
  text-decoration: none;
}

.home-book-card footer a.is-primary {
  color: #fff;
  border-color: #3154d9;
  background: #3154d9;
}

.home-error-state {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.home-error-state > button {
  min-height: 34px;
  padding: 0 12px;
  color: #3154d9;
  border: 1px solid #cdd6f5;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.home-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 24px;
}

.home-pagination button {
  min-height: 34px;
  padding: 0 13px;
  color: #46516a;
  border: 1px solid #dce1e9;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.home-pagination button:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.home-pagination span {
  color: #687189;
  font-size: 12px;
}

@media (max-width: 900px) {
  .home-book-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

}

@media (max-width: 640px) {
  .home-hero {
    min-height: 300px;
    margin-top: 18px;
    padding: 38px 24px;
  }

  .home-hero h1 {
    font-size: 31px;
  }

  .home-hero__description {
    font-size: 12px;
    line-height: 1.6;
  }

  .home-search {
    height: 52px;
    margin-top: 28px;
  }

  .home-search button {
    min-width: 72px;
  }

  .home-shortcuts {
    flex-wrap: wrap;
  }

  .home-summary {
    grid-template-columns: 1fr;
  }

  .home-catalog {
    margin-top: 36px;
  }

  .home-filters {
    padding: 16px;
  }

  .home-book-grid {
    grid-template-columns: 1fr;
  }

  .home-book-cover {
    height: 180px;
  }
}
</style>
