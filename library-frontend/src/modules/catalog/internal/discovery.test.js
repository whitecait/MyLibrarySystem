import { afterEach, describe, expect, it, vi } from 'vitest'
import { setTransport } from '@/platform/http'
import { getCatalogDiscovery } from '../index'

function page(items, nextCursor = null) {
  return { code: 'MLS-CAT-S001', message: 'ok', data: { items, nextCursor } }
}
function title(titleId, category, availability = 'AVAILABLE') {
  return { titleId, title: `书目 ${titleId}`, category, availability, authors: ['作者'] }
}

afterEach(() => {
  setTransport(null)
  vi.restoreAllMocks()
})

describe('catalog discovery', () => {
  it('reads the whole unfiltered catalog and includes choices from later pages', async () => {
    const calls = []
    setTransport({ request(_operation, options) {
      calls.push(options.query)
      if (!options.query.cursor) return page([title('1', '文学'), title('2', '历史')], 'page2')
      return page([title('3', '科学', 'UNKNOWN'), title('4', '历史', 'UNAVAILABLE')])
    } })
    const result = await getCatalogDiscovery()
    expect(calls).toEqual([{ limit: 100, cursor: undefined }, { limit: 100, cursor: 'page2' }])
    expect(new Set(result.categories)).toEqual(new Set(['文学', '历史', '科学']))
    expect(result.availabilities).toEqual(['AVAILABLE', 'UNAVAILABLE', 'UNKNOWN'])
    expect(result.suggestions).toHaveLength(3)
    expect(new Set(result.suggestions.map(book => book.titleId)).size).toBe(3)
  })

  it('samples late books as well as the first page and removes duplicate IDs', async () => {
    vi.spyOn(Math, 'random').mockReturnValue(0)
    setTransport({ request(_operation, options) {
      if (!options.query.cursor) return page([title('1', '文学'), title('2', '文学'), title('3', '文学')], 'page2')
      return page([title('3', '文学'), title('4', '科学'), title('5', '科学')])
    } })
    const result = await getCatalogDiscovery()
    expect(result.suggestions.map(book => book.titleId)).toEqual(['5', '2', '3'])
  })

  it('returns every available book when fewer than three exist, including an empty catalog', async () => {
    setTransport({ request: () => page([title('1', '文学')]) })
    expect((await getCatalogDiscovery()).suggestions.map(book => book.titleId)).toEqual(['1'])
    setTransport({ request: () => page([]) })
    expect(await getCatalogDiscovery()).toEqual({ categories: [], availabilities: [], suggestions: [] })
  })

  it('rejects a later-page failure instead of reporting incomplete choices', async () => {
    setTransport({ request(_operation, options) {
      if (!options.query.cursor) return page([title('1', '文学')], 'page2')
      throw new Error('connection lost')
    } })
    await expect(getCatalogDiscovery()).rejects.toThrow('connection lost')
  })

  it('refuses a repeated cursor instead of continuing indefinitely', async () => {
    const request = vi.fn(() => page([title('1', '文学')], 'same-cursor'))
    setTransport({ request })
    await expect(getCatalogDiscovery()).rejects.toThrow('repeated cursor')
    expect(request).toHaveBeenCalledTimes(2)
  })

  it('stops reading new pages after cancellation', async () => {
    const controller = new AbortController()
    const request = vi.fn(() => {
      controller.abort(new Error('page left'))
      return page([title('1', '文学')], 'page2')
    })
    setTransport({ request })
    await expect(getCatalogDiscovery({ signal: controller.signal })).rejects.toThrow('page left')
    expect(request).toHaveBeenCalledTimes(1)
  })
})
