import { apiRequest } from '@/platform/http'

const DISCOVERY_PAGE_SIZE = 100
const AVAILABILITY_ORDER = ['AVAILABLE', 'UNAVAILABLE', 'UNKNOWN']

export function pickRandomTitles(titles, count = 3) {
  const sample = []
  titles.forEach((title, index) => {
    if (index < count) sample.push(title)
    else {
      const position = Math.floor(Math.random() * (index + 1))
      if (position < count) sample[position] = title
    }
  })
  return sample
}

// Read every unfiltered cursor page. A partial feed cannot supply complete choices.
export async function getCatalogDiscovery({ signal } = {}) {
  const titles = new Map()
  const cursors = new Set()
  let cursor
  do {
    if (signal?.aborted) throw signal.reason ?? new Error('Catalog discovery cancelled')
    const envelope = await apiRequest('searchTitles', {
      query: { limit: DISCOVERY_PAGE_SIZE, cursor },
      signal,
    })
    for (const title of envelope.data.items ?? []) titles.set(title.titleId, title)
    cursor = envelope.data.nextCursor ?? null
    if (cursor && cursors.has(cursor)) throw new Error('Catalog discovery received a repeated cursor')
    if (cursor) cursors.add(cursor)
  } while (cursor)

  const books = [...titles.values()]
  const categories = [...new Set(books.map(book => book.category).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
  const availableStates = new Set(books.map(book => book.availability))
  return {
    categories,
    availabilities: AVAILABILITY_ORDER.filter(state => availableStates.has(state)),
    suggestions: pickRandomTitles(books),
  }
}
