import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { setTransport } from '@/platform/http'
import sharedFixtures from '@/platform/http/mock/fixtures'
import { installMockTransport, mountView } from '../../../tests/helpers'
import CatalogAdminView from './CatalogAdminView.vue'

afterEach(() => setTransport(null))

async function mountAdmin(fixtures) {
  installMockTransport({ fixtures })
  const wrapper = await mountView(CatalogAdminView)
  await flushPromises()
  return wrapper
}

async function selectFirstTitle(wrapper) {
  await wrapper.find('[data-testid="admin-search-input"]').setValue('database')
  await wrapper.find('[data-testid="admin-search-form"]').trigger('submit')
  await flushPromises()
  await wrapper.find('[data-testid="admin-select-2001"]').trigger('click')
  await flushPromises()
}

describe('CatalogAdminView', () => {
  it('searches, selects a title and shows its version for optimistic updates', async () => {
    const wrapper = await mountAdmin(sharedFixtures)
    await selectFirstTitle(wrapper)

    expect(wrapper.find('[data-testid="update-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="update-title"]').element.value).toBe('Database Systems')
    expect(wrapper.text()).toContain('当前版本号：3')
  })

  it('saves an update and reports the new version', async () => {
    const wrapper = await mountAdmin(sharedFixtures)
    await selectFirstTitle(wrapper)

    await wrapper.find('[data-testid="update-title"]').setValue('Database Systems (2nd)')
    await wrapper.find('[data-testid="update-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="update-success"]').text()).toContain('4')
  })

  it('hints a refresh on version conflicts without saving', async () => {
    const wrapper = await mountAdmin({
      ...sharedFixtures,
      updateTitle: { status: 409, code: 'MLS-CAT-003', message: 'version conflict', data: {} },
    })
    await selectFirstTitle(wrapper)

    await wrapper.find('[data-testid="update-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="version-conflict-hint"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="update-success"]').exists()).toBe(false)
  })

  it('removes a title with a reason', async () => {
    const wrapper = await mountAdmin(sharedFixtures)
    await selectFirstTitle(wrapper)

    await wrapper.find('[data-testid="remove-toggle"]').trigger('click')
    await wrapper.find('[data-testid="remove-reason"]').setValue('重复书目')
    await wrapper.find('[data-testid="remove-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="remove-success"]').exists()).toBe(true)
  })

  it('creates a title and reports its id', async () => {
    const wrapper = await mountAdmin(sharedFixtures)

    await wrapper.get('[data-testid="catalog-create-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="create-isbn"]').setValue('978-9-999')
    await wrapper.find('[data-testid="create-title"]').setValue('New Book')
    await wrapper.find('[data-testid="create-authors"]').setValue('A. Author')
    await wrapper.find('[data-testid="create-category"]').setValue('计算机')
    await wrapper.find('[data-testid="create-price"]').setValue('59')
    await wrapper.find('[data-testid="create-form"]').trigger('submit')
    await flushPromises()

    const success = wrapper.find('[data-testid="create-success"]')
    expect(success.exists()).toBe(true)
    expect(success.text()).toContain('2002')
  })

  it('surfaces ISBN conflicts on create', async () => {
    const wrapper = await mountAdmin({
      ...sharedFixtures,
      createTitle: { status: 409, code: 'MLS-CAT-003', message: 'isbn conflict', data: {} },
    })

    await wrapper.get('[data-testid="catalog-create-open"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="create-isbn"]').setValue('dup')
    await wrapper.find('[data-testid="create-title"]').setValue('t')
    await wrapper.find('[data-testid="create-authors"]').setValue('a')
    await wrapper.find('[data-testid="create-category"]').setValue('c')
    await wrapper.find('[data-testid="create-price"]').setValue('10')
    await wrapper.find('[data-testid="create-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="create-success"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('MLS-CAT-003')
  })
})
