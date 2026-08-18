import { describe, expect, it } from 'vitest'
import appSource from '../App.vue?raw'
import profilePageSource from '../pages/profile/index.vue?raw'
import statusPageSource from '../pages/status/index.vue?raw'
import minePageSource from '../pages/mine/index.vue?raw'

describe('ArchiveTabBar H5 integration', () => {
  it('renders the constrained custom footer on all three tab pages', () => {
    expect(profilePageSource).toContain('<ArchiveTabBar current="profile" />')
    expect(statusPageSource).toContain('<ArchiveTabBar current="status" />')
    expect(minePageSource).toContain('<ArchiveTabBar current="mine" />')
  })

  it('removes the native full-viewport H5 tab bar and placeholder', () => {
    expect(appSource).toMatch(/uni-tabbar\s*\{[^}]*display:\s*none\s*!important;/s)
    expect(appSource).toMatch(
      /\.uni-app--showtabbar\s+uni-page-wrapper\s*\{[^}]*height:\s*100%\s*!important;/s,
    )
    expect(appSource).toMatch(
      /\.uni-app--showtabbar\s+uni-page-wrapper::after\s*\{[^}]*display:\s*none\s*!important;/s,
    )
  })
})
