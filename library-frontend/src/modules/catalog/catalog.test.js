import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import { installMockTransport } from '../../tests/helpers'
import * as catalog from './index'

afterEach(() => {
  setTransport(null)
})

describe('catalog module facade', () => {
  it('replays the approved public search scenario verbatim', async () => {
    installMockTransport({ scenarioId: 'CATALOG-PUBLIC-SEARCH' })
    const envelope = await catalog.searchTitles({ q: 'database', limit: 20 })
    expect(envelope.code).toBe('MLS-CAT-S001')
    expect(envelope.data.items[0]).toEqual({
      titleId: '2001',
      title: 'Database Systems',
      availability: 'AVAILABLE',
    })
  })

  it('maps maintenance calls to their contract operations', async () => {
    const calls = []
    setTransport({
      mode: 'mock',
      async request(operationId, options = {}) {
        calls.push({ operationId, options })
        return { code: 'MLS-T-S001', message: 'ok', data: {} }
      },
    })

    await catalog.getTitle({ titleId: '2001' })
    await catalog.getReadingContent({ titleId: '2001', cursor: 'c2' })
    await catalog.createTitle({ isbn: 'x', title: 't', authors: ['a'], category: 'c', priceCents: 100 })
    await catalog.updateTitle({ titleId: '2001', version: 3, title: 'New' })
    await catalog.removeTitle({ titleId: '2001', version: 3, reason: '重复' })

    expect(calls.map((call) => call.operationId)).toEqual([
      'getTitle',
      'getReadingContent',
      'createTitle',
      'updateTitle',
      'removeTitle',
    ])
    expect(calls[0].options.params).toEqual({ titleId: '2001' })
    expect(calls[1].options.query).toEqual({ cursor: 'c2', limit: undefined })
    expect(calls[3].options.params).toEqual({ titleId: '2001' })
    expect(calls[3].options.body).toEqual({ version: 3, title: 'New' })
    expect(calls[4].options.body).toEqual({ version: 3, reason: '重复' })
  })

  it('exposes the module views through the public entry', () => {
    expect(catalog.PortalView).toBeTruthy()
    expect(catalog.TitleDetailView).toBeTruthy()
    expect(catalog.ReadingView).toBeTruthy()
    expect(catalog.CatalogAdminView).toBeTruthy()
  })
})
