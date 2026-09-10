import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'

describe('engineering status page', () => {
  it('shows that the engineering foundation is being prepared', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('工程基础准备中')
  })
})
